package com.margin.app.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import com.margin.app.R
import com.margin.app.data.VaultStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Fills and saves passwords in other apps and browsers. The vault is encrypted with the notes passcode and
 * Margin holds no key in the background, so a fill offers one "Autofill with Margin" item that opens
 * [AutofillActivity] to unlock; only then are the matching logins read and offered. Saving (a new sign-in,
 * or a changed password) also goes through that screen.
 */
@AndroidEntryPoint
class MarginAutofillService : AutofillService() {
    @Inject lateinit var store: VaultStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onFillRequest(request: FillRequest, signal: CancellationSignal, callback: FillCallback) {
        val form = runCatching { AutofillForm.parse(request.fillContexts.last().structure) }.getOrNull()
        if (form == null || form.isEmpty || form.pkg == packageName) return callback.onSuccess(null)
        val job = scope.launch {
            val builder = FillResponse.Builder()
            val save = saveInfo(form)
            save?.let(builder::setSaveInfo)
            val fillable = (form.username + form.password + form.otp).toTypedArray()
            if (fillable.isNotEmpty() && !store.isEmpty()) {
                @Suppress("DEPRECATION")
                builder.setAuthentication(fillable, AutofillActivity.fillSender(this@MarginAutofillService), unlockItem(this@MarginAutofillService))
            } else if (save == null) return@launch callback.onSuccess(null)
            callback.onSuccess(builder.build())
        }
        signal.setOnCancelListener { job.cancel() }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // A sign-in over two screens (username, then password) arrives as several contexts.
        val forms = request.fillContexts.mapNotNull { runCatching { AutofillForm.parse(it.structure) }.getOrNull() }
        val password = forms.mapNotNull { it.passwordValue }.lastOrNull()
        val last = forms.lastOrNull()
        if (password == null || last == null || last.pkg == packageName) return callback.onSuccess()
        val pending = AutofillHandoff.Pending(
            pkg = last.pkg, webDomain = forms.mapNotNull { it.webDomain }.lastOrNull(),
            username = forms.mapNotNull { it.usernameValue }.lastOrNull().orEmpty(), password = password,
        )
        val intent = AutofillActivity.saveIntent(this, AutofillHandoff.put(pending))
        if (Build.VERSION.SDK_INT >= 28) {
            val pi = PendingIntent.getActivity(this, requests.incrementAndGet(), intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            callback.onSuccess(pi.intentSender)
        } else {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            callback.onSuccess()
        }
    }

    companion object {
        private val requests = AtomicInteger()
        fun nextRequest() = requests.incrementAndGet()

        /** Ask to save when a password was typed (and the username with it, when there is one). */
        fun saveInfo(form: AutofillForm): SaveInfo? {
            val passwords = (form.password + form.newPassword).toTypedArray()
            if (passwords.isEmpty()) return null
            val type = SaveInfo.SAVE_DATA_TYPE_PASSWORD or (if (form.username.isNotEmpty()) SaveInfo.SAVE_DATA_TYPE_USERNAME else 0)
            return SaveInfo.Builder(type, passwords)
                .apply { if (form.username.isNotEmpty()) setOptionalIds(form.username.toTypedArray()) }
                // Many sign-in pages never "finish"; the form going away (the next page) counts as submitting.
                .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                .build()
        }

        /** The dropdown item shown before unlocking. */
        fun unlockItem(context: Context): RemoteViews = item(context, context.getString(R.string.autofill_unlock), context.getString(R.string.autofill_unlock_sub), null)

        /** One dropdown item: [icon] or Margin's own. */
        fun item(context: Context, title: String, sub: String?, icon: Bitmap?): RemoteViews =
            RemoteViews(context.packageName, R.layout.autofill_item).apply {
                setTextViewText(R.id.autofill_title, title)
                setTextViewText(R.id.autofill_sub, sub.orEmpty())
                setViewVisibility(R.id.autofill_sub, if (sub.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
                if (icon != null) setImageViewBitmap(R.id.autofill_icon, icon) else setImageViewResource(R.id.autofill_icon, R.mipmap.ic_launcher)
            }
    }
}

/**
 * A sign-in waiting for [AutofillActivity] to save it. It stays in memory, not in the intent, so the password
 * never sits in Android's record of pending intents.
 */
object AutofillHandoff {
    data class Pending(val pkg: String, val webDomain: String?, val username: String, val password: String)

    private val pending = mutableMapOf<String, Pending>()

    @Synchronized fun put(p: Pending): String = java.util.UUID.randomUUID().toString().also { pending[it] = p }
    @Synchronized fun take(token: String?): Pending? = token?.let { pending.remove(it) }
}
