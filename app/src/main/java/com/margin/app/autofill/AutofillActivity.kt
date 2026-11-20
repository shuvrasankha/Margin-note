package com.margin.app.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.WindowManager
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.margin.app.data.AppLock
import com.margin.app.data.AppPrefs
import com.margin.app.data.AutofillSave
import com.margin.app.data.LockSession
import com.margin.app.data.Login
import com.margin.app.data.VaultIcons
import com.margin.app.data.VaultKind
import com.margin.app.data.VaultStore
import com.margin.app.data.fills
import com.margin.app.data.planAutofillSave
import com.margin.app.data.siteOf
import com.margin.app.data.vaultKind
import com.margin.app.ui.Biometric
import com.margin.app.ui.Card
import com.margin.app.ui.CardDivider
import com.margin.app.ui.JType
import com.margin.app.ui.MarginTheme
import com.margin.app.ui.PasscodeDialog
import com.margin.app.ui.PasswordMode
import com.margin.app.ui.SearchField
import com.margin.app.ui.TextAction
import com.margin.app.ui.VaultEntry
import com.margin.app.ui.VaultEntryRow
import com.margin.app.ui.jc
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Autofill's own screen, over the app being signed in to. It unlocks the vault (fingerprint or passcode) with
 * a session of its own that ends with it, then either hands back the logins for that app or site (a picker
 * when none is linked yet, which links the one chosen), or saves the sign-in that was just typed: a new
 * login, or the matching one's changed password, the old one going to its history.
 */
@AndroidEntryPoint
class AutofillActivity : FragmentActivity() {
    @Inject lateinit var store: VaultStore
    @Inject lateinit var appLock: AppLock
    @Inject lateinit var appPrefs: AppPrefs

    private val session = LockSession()

    /** What's on screen after the unlock. */
    private sealed interface Step {
        data object Unlocking : Step
        data object Working : Step
        /** Choose a login; [fresh] offers saving a new one instead. */
        data class Pick(val title: String, val entries: List<VaultEntry>, val fresh: Boolean, val onPick: (VaultEntry?) -> Unit) : Step
    }

    private var step by mutableStateOf<Step>(Step.Unlocking)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val saving = intent.action == ACTION_SAVE
        val form = if (saving) null else structure()?.let { runCatching { AutofillForm.parse(it) }.getOrNull() }
        val pending = if (saving) AutofillHandoff.take(intent.getStringExtra(EXTRA_TOKEN)) else null
        if (form == null && pending == null) return cancel()
        setContent {
            val appearance by appPrefs.appearance.collectAsState()
            MarginTheme(appearance) {
                Box(Modifier.fillMaxSize()) {
                    when (val s = step) {
                        Step.Unlocking -> Unlock(creating = saving) { lifecycleScope.launch { if (form != null) fill(form) else save(pending!!) } }
                        Step.Working -> {}
                        is Step.Pick -> Picker(s)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        session.lock()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun structure(): AssistStructure? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)
        else intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE)

    private fun cancel() { setResult(RESULT_CANCELED); finish() }

    /** The site, when the app reporting it is a browser; any other app could claim any site. */
    private fun trusted(pkg: String, domain: String?) = domain?.takeIf { VaultIcons.isBrowser(this, pkg) }

    /**
     * The fingerprint first when it's set up, else the passcode pad. Saving into a vault with no passcode yet
     * makes one; it's the passcode for locked notes too.
     */
    @Composable
    private fun Unlock(creating: Boolean, onUnlocked: () -> Unit) {
        var mode by remember { mutableStateOf<PasswordMode?>(null) }
        var sample by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            sample = appLock.passcodeSample()
            val s = sample
            if (s == null) {
                if (creating) mode = PasswordMode.CREATE else cancel()
                return@LaunchedEffect
            }
            appPrefs.fingerprint.value?.let { fp ->
                val (iv, data) = fp.split('.').map { java.util.Base64.getUrlDecoder().decode(it) }
                val r = Biometric.decrypt(this@AutofillActivity, data, iv)
                if (r is Biometric.Result.Ok) {
                    val code = String(r.bytes, Charsets.UTF_8).toCharArray().also { r.bytes.fill(0) }
                    val ok = session.unlock(code, s) != null
                    code.fill('\u0000')
                    if (ok) { step = Step.Working; return@LaunchedEffect onUnlocked() }
                }
            }
            mode = PasswordMode.ENTER
        }
        mode?.let { m ->
            PasscodeDialog(
                m, onDismiss = ::cancel,
                intro = "Choose a passcode to keep your passwords encrypted. Locked notes use the same one.",
                enterTitle = "Unlock Margin", enterSub = "Enter your notes passcode to use your passwords.",
            ) { code ->
                val s = sample
                val ok = if (s == null) { session.startWith(code); true } else session.unlock(code, s) != null
                code.fill('\u0000')
                if (ok) { step = Step.Working; onUnlocked(); null } else "Wrong passcode"
            }
        }
    }

    @Composable
    private fun Picker(s: Step.Pick) {
        var query by remember { mutableStateOf("") }
        BackHandler(onBack = ::cancel)
        Column(Modifier.fillMaxSize().background(jc.page).systemBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) { TextAction("Cancel", ::cancel) }
            Text(s.title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = JType.heading, color = jc.ink)
            SearchField(query, { query = it }, "Search passwords", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fill = jc.card)
            val q = query.trim().lowercase()
            val shown = s.entries.filter { e -> q.isEmpty() || listOf(e.title, e.login.username, e.login.url).any { q in it.lowercase() } }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (s.fresh) item {
                    Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { com.margin.app.ui.CardRow("Save as a New Login", onClick = { s.onPick(null) }) }
                }
                item {
                    if (shown.isEmpty()) Text("No passwords match.", Modifier.padding(20.dp), style = JType.secondary, color = jc.muted)
                    else Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        shown.forEachIndexed { i, e ->
                            if (i > 0) CardDivider()
                            VaultEntryRow(e) { s.onPick(e) }
                        }
                    }
                }
            }
        }
    }

    /** Hands back the logins for this app or site; with none linked yet, the one the user picks (and links it). */
    private suspend fun fill(form: AutofillForm) {
        val domain = trusted(form.pkg, form.webDomain)
        val all = store.openAll(session).filter { it.second.vaultKind == VaultKind.LOGIN }
        val matches = all.filter { it.second.fills(form.pkg, domain) }
        if (matches.isNotEmpty()) return reply(form, matches.map { it.second })
        val label = domain?.let(::siteOf) ?: VaultIcons.appLabel(this, form.pkg) ?: form.pkg
        step = Step.Pick("Fill in $label", all.map { VaultEntry(it.first, it.second) }.sortedBy { it.title.lowercase() }, fresh = false) { e ->
            if (e == null) return@Pick cancel()
            lifecycleScope.launch {
                step = Step.Working
                // Remember the choice, so this app or site fills straight away next time.
                val linked = if (domain != null) { if (e.login.url.isBlank()) e.login.copy(url = siteOf(domain)) else e.login }
                else e.login.copy(apps = (e.login.apps + form.pkg).distinct(), icon = e.login.icon.ifEmpty { VaultIcons.ofApp(this@AutofillActivity, form.pkg).orEmpty() })
                if (linked != e.login) store.save(session, e.id, linked)
                reply(form, listOf(linked))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun reply(form: AutofillForm, logins: List<Login>) {
        val response = FillResponse.Builder()
        val save = MarginAutofillService.saveInfo(form)
        save?.let(response::setSaveInfo)
        var any = false
        for (l in logins) {
            val title = VaultEntry("", l).title
            val ds = Dataset.Builder(MarginAutofillService.item(this, title, l.username.ifBlank { null }, VaultIcons.decode(l.icon)))
            var values = 0
            if (l.username.isNotBlank()) form.username.forEach { ds.setValue(it, AutofillValue.forText(l.username)); values++ }
            if (l.password.isNotEmpty()) form.password.firstOrNull()?.let { ds.setValue(it, AutofillValue.forText(l.password)); values++ }
            if (values > 0) { response.addDataset(ds.build()); any = true }
        }
        if (!any && save == null) return cancel()
        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response.build()))
        finish()
    }

    /** Saves the typed sign-in: a new login, the changed password of the one it matches, or the one the user picks. */
    private suspend fun save(p: AutofillHandoff.Pending) {
        val domain = trusted(p.pkg, p.webDomain)
        val all = store.openAll(session)
        val label = domain?.let(::siteOf) ?: VaultIcons.appLabel(this, p.pkg) ?: p.pkg
        suspend fun icon(login: Login) = login.icon.ifEmpty {
            if (domain == null) VaultIcons.ofApp(this, p.pkg).orEmpty()
            else withTimeoutOrNull(3_000) { VaultIcons.ofSite(domain) }.orEmpty() // else fetched when Passwords next opens
        }
        suspend fun create(login: Login) { store.save(session, null, login.copy(icon = icon(login))); done("Saved to Margin") }
        when (val plan = planAutofillSave(all, p.pkg, domain, p.username, p.password, label)) {
            is AutofillSave.Create -> create(plan.login)
            is AutofillSave.Update -> {
                val old = all.first { it.first == plan.id }.second
                store.save(session, plan.id, plan.login.copy(icon = icon(plan.login)))
                done(if (old.password != plan.login.password) "Password updated in Margin" else "Saved to Margin")
            }
            is AutofillSave.Unchanged -> done("Already saved in Margin")
            is AutofillSave.Choose -> step = Step.Pick(
                "Which login changed?", all.filter { it.first in plan.ids }.map { VaultEntry(it.first, it.second) }, fresh = true,
            ) { e ->
                lifecycleScope.launch {
                    step = Step.Working
                    if (e == null) create(Login(name = label, username = p.username, password = p.password, url = domain?.let(::siteOf).orEmpty(), apps = listOfNotNull(p.pkg.takeIf { domain == null })))
                    else { store.save(session, e.id, e.login.copy(password = p.password)); done("Password updated in Margin") }
                }
            }
        }
    }

    private fun done(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        private const val ACTION_FILL = "com.margin.app.autofill.FILL"
        private const val ACTION_SAVE = "com.margin.app.autofill.SAVE"
        private const val EXTRA_TOKEN = "token"

        /** Opened by Android when "Autofill with Margin" is tapped; it adds the form's structure, so it's mutable. */
        fun fillSender(context: Context): IntentSender {
            val intent = Intent(context, AutofillActivity::class.java).setAction(ACTION_FILL)
            val flags = PendingIntent.FLAG_CANCEL_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            return PendingIntent.getActivity(context, MarginAutofillService.nextRequest(), intent, flags).intentSender
        }

        fun saveIntent(context: Context, token: String): Intent =
            Intent(context, AutofillActivity::class.java).setAction(ACTION_SAVE).putExtra(EXTRA_TOKEN, token)
    }
}
