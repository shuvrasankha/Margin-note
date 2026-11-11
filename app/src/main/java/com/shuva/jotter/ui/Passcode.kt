package com.shuva.jotter.ui

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
import com.shuva.jotter.data.PASSCODE_LENGTH
import kotlinx.coroutines.launch

/**
 * Six dots and a number pad. When the sixth digit goes in, [onComplete] gets the code and the dots clear.
 * [enabled] false while a code is being checked.
 */
@Composable
fun PasscodePad(error: String?, enabled: Boolean, onComplete: (String) -> Unit, modifier: Modifier = Modifier, spread: Boolean = false) {
    var code by remember { mutableStateOf("") }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.semantics { contentDescription = "${code.length} of $PASSCODE_LENGTH digits entered" },
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
                    val label = if (k == "⌫") "Delete" else k
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
    onSubmit: suspend (CharArray) -> String?,
) {
    var first by remember { mutableStateOf<String?>(null) } // CREATE: the code to confirm
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = when {
        mode == PasswordMode.ENTER -> "Enter your passcode"
        first == null -> "Create a notes passcode"
        else -> "Confirm your passcode"
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(jc.bg).systemBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.align(Alignment.CenterStart)) { TextAction("Cancel", onDismiss) }
                if (mode == PasswordMode.CREATE) Text(if (first == null) "Step 1 of 2" else "Step 2 of 2", style = JType.small, color = jc.muted)
            }
            Box(Modifier.padding(top = 28.dp).size(56.dp).clip(CircleShape).background(jc.surface), contentAlignment = Alignment.Center) {
                JIcon(Ico.LOCK, jc.ink, size = 26.dp, stroke = 1.8f)
            }
            Text(title, Modifier.padding(top = 20.dp, bottom = 8.dp), style = JType.heading.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = jc.ink, textAlign = TextAlign.Center)
            val sub = when {
                mode == PasswordMode.ENTER -> "The 6 digits for your locked notes."
                first != null -> "Enter the same 6 digits again."
                else -> intro ?: "Use it to open locked notes on all your devices. It's separate from your phone passcode."
            }
            // A fixed height keeps the dots in place between the create and confirm steps.
            Text(sub, Modifier.widthIn(max = 290.dp).heightIn(min = 63.dp).padding(bottom = 32.dp - 12.dp), style = JType.secondary.copy(fontSize = 15.sp, lineHeight = 21.sp), color = jc.muted, textAlign = TextAlign.Center)
            PasscodePad(error, enabled = !busy, onComplete = { code ->
                error = null
                when {
                    mode == PasswordMode.CREATE && first == null -> first = code
                    mode == PasswordMode.CREATE && code != first -> { first = null; error = "Passcodes didn't match. Try again." }
                    else -> scope.launch {
                        busy = true
                        error = onSubmit(code.toCharArray())
                        busy = false
                    }
                }
            }, modifier = Modifier.padding(top = 12.dp).weight(1f), spread = true)
            when {
                onOldPassword != null -> Box(Modifier.padding(top = 16.dp)) { TextAction("Use old password", onOldPassword) }
                mode == PasswordMode.CREATE -> Text(
                    "If you forget this passcode, locked notes can't be recovered — not even by us.",
                    Modifier.padding(top = 28.dp).widthIn(max = 300.dp), style = JType.small.copy(lineHeight = 18.sp), color = jc.muted, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
