package com.margin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.margin.app.data.PASSCODE_LENGTH
import kotlinx.coroutines.launch
import com.margin.app.R
import androidx.compose.ui.res.stringResource

/**
 * Six dots and a number pad. When the sixth digit goes in, [onComplete] gets the code and the dots clear.
 * [enabled] false while a code is being checked.
 */
@Composable
fun PasscodePad(error: String?, enabled: Boolean, onComplete: (String) -> Unit, modifier: Modifier = Modifier, spread: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var code by remember { mutableStateOf("") }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.semantics { contentDescription = context.getString(R.string.passcode_digits_entered, code.length.toString(), PASSCODE_LENGTH.toString()) },
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            repeat(PASSCODE_LENGTH) { i ->
                val on = i < code.length
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (on) jc.ink else jc.bg).border(1.5.dp, jc.ink, CircleShape))
            }
        }
        Text(
            error ?: "", Modifier.padding(top = 12.dp).height(20.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = JType.small, color = jc.danger, textAlign = TextAlign.Center,
        )
        // spread: the keys sit at the bottom of a full-height [modifier].
        if (spread) Spacer(Modifier.weight(1f))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.chunked(3).forEachIndexed { r, row ->
            Row(Modifier.padding(top = if (r == 0) 0.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                row.forEach { k ->
                    val label = if (k == "⌫") stringResource(R.string.common_delete) else k
                    Box(
                        Modifier.size(76.dp).clip(CircleShape)
                            .then(if (k.isEmpty()) Modifier else Modifier.background(if (k == "⌫") jc.bg else jc.surface))
                            .then(
                                if (k.isEmpty() || !enabled) Modifier
                                else Modifier.clickable(role = Role.Button) {
                                    if (k == "⌫") { code = code.dropLast(1); return@clickable }
                                    code += k
                                    if (code.length == PASSCODE_LENGTH) { val c = code; code = ""; onComplete(c) }
                                }.semantics { contentDescription = label },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k.isNotEmpty()) Text(k, style = JType.heading.copy(fontSize = if (k == "⌫") 24.sp else 30.sp, fontWeight = FontWeight.Normal), color = if (enabled) jc.ink else jc.muted)
                    }
                }
            }
        }
    }
}

/**
 * Full-screen passcode entry. CREATE asks twice (with [intro], or a warning that there's no recovery);
 * ENTER asks once and, with [onOldPassword], offers the old password field for notes locked before passcodes.
 * [onSubmit] returns an error to show, or null when done.
 */
@Composable
fun PasscodeDialog(
    mode: PasswordMode,
    onDismiss: () -> Unit,
    intro: String? = null,
    onOldPassword: (() -> Unit)? = null,
    /** ENTER: what the screen says instead of "Enter your passcode" and its line, and the label of its Cancel. */
    enterTitle: String = stringResource(R.string.passcode_enter_your_passcode),
    enterSub: String = stringResource(R.string.passcode_the_6_digits_for_your),
    cancel: String = stringResource(R.string.common_cancel),
    onSubmit: suspend (CharArray) -> String?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var first by remember { mutableStateOf<String?>(null) } // CREATE: the code to confirm
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = when {
        mode == PasswordMode.ENTER -> enterTitle
        first == null -> stringResource(R.string.passcode_create_a_notes_passcode)
        else -> stringResource(R.string.passcode_confirm_your_passcode)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(jc.bg).systemBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.align(Alignment.CenterStart)) { TextAction(cancel, onDismiss) }
                if (mode == PasswordMode.CREATE) Text(if (first == null) stringResource(R.string.passcode_step_1_of_2) else stringResource(R.string.passcode_step_2_of_2), style = JType.small, color = jc.muted)
            }
            Box(Modifier.padding(top = 28.dp).size(56.dp).clip(CircleShape).background(jc.surface), contentAlignment = Alignment.Center) {
                JIcon(Ico.LOCK, jc.ink, size = 26.dp, stroke = 1.8f)
            }
            Text(title, Modifier.padding(top = 20.dp, bottom = 8.dp), style = JType.heading.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = jc.ink, textAlign = TextAlign.Center)
            val sub = when {
                mode == PasswordMode.ENTER -> enterSub
                first != null -> stringResource(R.string.passcode_enter_the_same_6_digits)
                else -> intro ?: stringResource(R.string.passcode_use_it_to_open_locked)
            }
            // A fixed height keeps the dots in place between the create and confirm steps.
            Text(sub, Modifier.widthIn(max = 290.dp).heightIn(min = 63.dp).padding(bottom = 32.dp - 12.dp), style = JType.para, color = jc.muted, textAlign = TextAlign.Center)
            PasscodePad(error, enabled = !busy, onComplete = { code ->
                error = null
                when {
                    mode == PasswordMode.CREATE && first == null -> first = code
                    mode == PasswordMode.CREATE && code != first -> { first = null; error = context.getString(R.string.passcode_passcodes_didnt_match_try_again) }
                    else -> scope.launch {
                        busy = true
                        error = onSubmit(code.toCharArray())
                        busy = false
                    }
                }
            }, modifier = Modifier.padding(top = 12.dp).weight(1f), spread = true)
            when {
                onOldPassword != null -> Box(Modifier.padding(top = 16.dp)) { TextAction(stringResource(R.string.passcode_use_old_password), onOldPassword) }
                mode == PasswordMode.CREATE -> Text(
                    stringResource(R.string.passcode_if_you_forget_this_passcode),
                    Modifier.padding(top = 28.dp).widthIn(max = 300.dp), style = JType.small.copy(lineHeight = 18.sp), color = jc.muted, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The app lock's screen, over everything (it's a window of its own, so it also covers an open PDF or photo).
 * Tries the fingerprint first when it's set up; the passcode pad otherwise or after. [onLeave] sends the app away.
 */
@Composable
fun AppLockScreen(appLock: com.margin.app.data.AppLock, fingerprint: String?, onLeave: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.fragment.app.FragmentActivity
    var pad by remember { mutableStateOf(fingerprint == null || activity == null) }
    // The lock comes up as the app leaves the screen. The fingerprint prompt can only start once Margin is in
    // front again (Android ignores it from the background), so it waits for that, and tries again on each return.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    var asking by remember { mutableStateOf(false) }
    val askFingerprint: suspend () -> Unit = {
        asking = true
        val (iv, data) = fingerprint!!.split('.').map { java.util.Base64.getUrlDecoder().decode(it) }
        val r = Biometric.decrypt(activity!!, data, iv)
        asking = false
        if (r is Biometric.Result.Ok) {
            val code = String(r.bytes, Charsets.UTF_8).toCharArray().also { r.bytes.fill(0) }
            val ok = appLock.unlock(code)
            code.fill('\u0000')
            if (!ok) pad = true
        } else pad = true // cancelled, "Use password", or it failed: the passcode
    }
    LaunchedEffect(resumed) { if (resumed && !pad && !asking) askFingerprint() }
    if (pad) {
        PasscodeDialog(
            PasswordMode.ENTER, onDismiss = onLeave, enterTitle = stringResource(R.string.passcode_margin_is_locked), enterSub = stringResource(R.string.passcode_enter_your_passcode_to_open), cancel = stringResource(R.string.common_close),
        ) { code -> if (appLock.unlock(code)) null.also { code.fill('\u0000') } else context.getString(R.string.passcode_wrong_passcode).also { code.fill('\u0000') } }
    } else {
        // While the fingerprint prompt is (about to be) up: a cover, so nothing shows behind it, with the passcode
        // a tap away in case the prompt doesn't come.
        Dialog(onDismissRequest = onLeave, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(jc.bg).systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(56.dp).clip(CircleShape).background(jc.surface), contentAlignment = Alignment.Center) {
                    JIcon(Ico.LOCK, jc.ink, size = 26.dp, stroke = 1.8f)
                }
                Text(stringResource(R.string.passcode_margin_is_locked), Modifier.padding(top = 20.dp), style = JType.heading.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = jc.ink)
                Spacer(Modifier.weight(1f))
                TextAction(stringResource(R.string.passcode_use_passcode), { pad = true })
            }
        }
    }
}
