package com.shuva.jotter.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shuva.jotter.data.AI_PROVIDERS
import com.shuva.jotter.data.AiException
import com.shuva.jotter.data.AiKeys
import com.shuva.jotter.data.aiProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Settings › AI: the provider, the user's own API key for it, the model, and a connection test. */
@Composable
fun AiSettingsCard(ai: AiKeys) {
    val cfg by ai.config.collectAsState()
    val p = aiProvider(cfg.provider)
    val scope = rememberCoroutineScope()
    // Keyed by provider so a half-typed key never carries over. Not saveable: the key stays out of saved state.
    var typed by remember(p) { mutableStateOf("") }
    var note by remember(p, cfg.key, cfg.model) { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    Card {
        CardRow("Provider", icon = Ico.SPARKLE, sub = p.name) {
            var open by remember { mutableStateOf(false) }
            Box {
                TextAction("Change", { open = true })
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    AI_PROVIDERS.forEach { DropdownMenuItem(text = { Text(it.name, style = JType.row, color = if (it == p) jc.accent else jc.ink) }, onClick = { open = false; ai.setProvider(it.id) }) }
                }
            }
        }
        CardDivider()
        if (cfg.key != null) {
            CardRow("Key saved ••••${cfg.mask.orEmpty()}", icon = Ico.KEY, sub = "Encrypted on this phone only. Never synced.") {
                TextAction("Remove", { ai.remove() }, color = jc.danger)
            }
        } else {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    typed, { typed = it }, Modifier.fillMaxWidth(), singleLine = true, shape = JShape.sm,
                    label = { Text("${p.name} API key") }, placeholder = { Text(p.keyHint) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                Text("${p.keyHelp} It's stored encrypted on this phone only.", style = JType.small, color = jc.muted)
                TextAction("Save key", {
                    val saved = ai.save(typed.trim())
                    typed = ""
                    if (!saved) note = "That doesn't look like an API key."
                }, bold = true, enabled = typed.isNotBlank())
            }
        }
        CardDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                cfg.model, ai::setModel, Modifier.fillMaxWidth(), singleLine = true, shape = JShape.sm,
                label = { Text("Model") }, placeholder = { Text(p.defaultModel) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            Text("Leave empty for ${p.defaultModel}. ${p.name} sets the limits and the price.", style = JType.small, color = jc.muted)
        }
        if (cfg.key != null) {
            CardDivider()
            CardRow(if (testing) "Testing…" else "Test connection", labelColor = jc.accent, onClick = {
                if (testing) return@CardRow
                testing = true
                scope.launch {
                    val result = try { ai.test(); "Connection works." } catch (e: AiException) { "Test failed: ${e.message}" }
                    testing = false
                    note = result
                }
            })
        }
        note?.let { Text(it, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = JType.small, color = jc.ink) }
    }
}

private const val AI_SYSTEM = "You help with a note in a notes app. Answer in plain text without Markdown, in the note's language. Be brief."

/** The most note text one request carries. */
private const val AI_MAX_CHARS = 24_000

/** A ready-made request; [rewrites] means the answer is a new version of the whole note, so it can replace it. */
private class AiAsk(val label: String, val request: String, val rewrites: Boolean = false)

private val AI_ASKS = listOf(
    AiAsk("Summarize", "Summarize this note in a few short lines."),
    AiAsk("Action items", "List the action items in this note, one per line, each starting with \"- \"."),
    AiAsk("Improve writing", "Rewrite this note with clearer wording and correct grammar. Keep its meaning, its language and its line structure. Answer with only the rewritten note, without its title.", rewrites = true),
    AiAsk("Fix spelling", "Correct only the spelling and grammar mistakes in this note. Change nothing else. Answer with only the corrected note, without its title.", rewrites = true),
)

/**
 * Ask AI about the open note: a ready-made request or the user's own question goes with [noteText] to their
 * provider. The answer can be copied, or added to the note with [onInsert] (null for a read-only note).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSheet(ai: AiKeys, noteText: String, onInsert: ((String) -> Unit)?, onReplace: ((String) -> Unit)?, onClose: () -> Unit) {
    val cfg by ai.config.collectAsState()
    val p = aiProvider(cfg.provider)
    val scope = rememberCoroutineScope() // leaves with the sheet, so closing it drops the answer
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var answer by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    /** The answer is a rewrite of the whole note. */
    var rewrite by remember { mutableStateOf(false) }
    val busy = job?.isActive == true
    fun ask(request: String, rewrites: Boolean = false) {
        if (busy) return
        answer = null; error = null; rewrite = rewrites
        job = scope.launch {
            try {
                answer = ai.ask(AI_SYSTEM, "$request\n\nNote:\n${noteText.take(AI_MAX_CHARS)}")
            } catch (e: AiException) {
                error = e.message
            }
            job = null
        }
    }
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = jc.bg, shape = JShape.sheet,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) { TextAction("Close", onClose) }
                Text("Ask AI", Modifier.align(Alignment.Center), style = JType.rowTitle, color = jc.ink)
            }
            if (cfg.key == null) {
                Text("Add your own API key in Settings › AI to use this. Margin has no AI of its own.", style = JType.para, color = jc.ink)
            } else if (noteText.isBlank()) {
                Text("This note is empty.", style = JType.para, color = jc.muted)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { AI_ASKS.forEach { a -> Chip(a.label, { ask(a.request, a.rewrites) }) } }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(question, { question = it }, Modifier.weight(1f), shape = JShape.sm, label = { Text("Ask about this note") }, maxLines = 3)
                    TextAction("Ask", { ask(question.trim()) }, bold = true, enabled = question.isNotBlank() && !busy)
                }
                Text(
                    "Sends this note's text to ${p.name} with your key, under ${p.name}'s terms." +
                        if (noteText.length > AI_MAX_CHARS) " Only the first part of this long note is sent." else "",
                    style = JType.small, color = jc.muted,
                )
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = jc.accent, strokeWidth = 2.dp)
                        Text("Asking ${p.name}…", style = JType.para, color = jc.muted)
                    }
                    error?.let { Text(it, style = JType.para, color = jc.danger) }
                    answer?.let { a ->
                        SelectionContainer(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) { Text(a, style = JType.para, color = jc.ink) }
                        Row {
                            if (rewrite) onReplace?.let { TextAction("Replace note", { it(a) }, bold = true) }
                            onInsert?.let { TextAction(if (rewrite && onReplace != null) "Add below" else "Add to note", { it(a) }, bold = !(rewrite && onReplace != null)) }
                            TextAction("Copy", {
                                context.getSystemService(android.content.ClipboardManager::class.java)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("AI answer", a))
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.width(1.dp).navigationBarsPadding())
        }
    }
}
