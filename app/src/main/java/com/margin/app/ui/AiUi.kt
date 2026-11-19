package com.margin.app.ui

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
import androidx.compose.ui.text.withLink
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.margin.app.data.AI_PROVIDERS
import com.margin.app.data.AiException
import com.margin.app.data.AiKeys
import com.margin.app.data.aiProvider
import com.margin.app.data.providerForKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Settings › AI. Not set up: a short guided setup ([AiSetup]). Set up: which provider is connected, a test,
 * the model under Advanced, and Remove.
 */
@Composable
fun AiSettingsCard(ai: AiKeys) {
    val cfg by ai.config.collectAsState()
    val p = aiProvider(cfg.provider)
    val scope = rememberCoroutineScope()
    // A result line under the card; [ok] shows it in green.
    var note by remember(cfg.key, cfg.model) { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    // Just connected through setup (Connect tests the key): say so, in green.
    var hadKey by remember { mutableStateOf(cfg.key != null) }
    androidx.compose.runtime.LaunchedEffect(cfg.key) {
        if (cfg.key != null && !hadKey) { note = "✓ Connected. AI is ready to use in your notes."; ok = true }
        hadKey = cfg.key != null
    }
    var advanced by remember { mutableStateOf(cfg.model.isNotBlank()) }
    Card {
        if (cfg.key == null) {
            AiSetup(ai, Modifier.padding(16.dp))
            return@Card
        }
        CardRow("Connected to ${p.name}", icon = Ico.SPARKLE, sub = "Key ••••${cfg.mask.orEmpty()}, encrypted on this phone only. Never synced.") {
            TextAction("Remove", { ai.remove() }, color = jc.danger)
        }
        // Where the key lives: to check usage, limits or make a new one.
        ProviderLink(p, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), prefix = "Manage your key at ")
        CardDivider()
        CardRow(if (testing) "Testing…" else "Test connection", labelColor = jc.accent, onClick = {
            if (testing) return@CardRow
            testing = true
            scope.launch {
                val result = try { ai.test(); ok = true; "✓ Connection works." } catch (e: AiException) { ok = false; "Test failed: ${e.message}" }
                testing = false
                note = result
            }
        })
        CardDivider()
        CardRow("Advanced", sub = if (cfg.model.isBlank()) "Model: ${p.defaultModel} (default)" else "Model: ${cfg.model}", onClick = { advanced = !advanced }) {
            JIcon(if (advanced) Ico.ARROW_UP else Ico.ARROW_DOWN, jc.muted, size = 18.dp)
        }
        if (advanced) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                cfg.model, ai::setModel, Modifier.fillMaxWidth(), singleLine = true, shape = JShape.sm,
                label = { Text("Model") }, placeholder = { Text(p.defaultModel) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            Text("Leave empty for ${p.defaultModel}. ${p.name} sets the limits and the price.", style = JType.small, color = jc.muted)
        }
        note?.let {
            Text(
                it, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = JType.small.copy(fontWeight = if (ok) androidx.compose.ui.text.font.FontWeight.SemiBold else null), color = if (ok) jc.success else jc.danger,
            )
        }
    }
}

private fun openLink(context: android.content.Context, url: String) {
    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
}

/** The provider's key page as a tappable link, shown as its address without "https://". */
@Composable
private fun ProviderLink(p: com.margin.app.data.AiProvider, modifier: Modifier = Modifier, prefix: String = "Or open ") {
    val context = LocalContext.current
    val address = p.keyUrl.removePrefix("https://")
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append(prefix)
            withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("key", androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = jc.accent, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))) { openLink(context, p.keyUrl) }) {
                append(address)
            }
        },
        modifier.semantics { contentDescription = "$prefix$address, link" }, style = JType.small, color = jc.muted,
    )
}

/**
 * Guided AI setup: pick a provider (the free ones first), open its key page, paste the key and Connect,
 * which saves and tests it at once. A pasted key moves the choice to the provider it belongs to.
 */
@Composable
fun AiSetup(ai: AiKeys, modifier: Modifier = Modifier) {
    val cfg by ai.config.collectAsState()
    val p = aiProvider(cfg.provider)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Not saveable: the key stays out of saved state.
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun take(key: String) {
        typed = key; error = null
        providerForKey(key)?.takeIf { it.id != cfg.provider }?.let { ai.setProvider(it.id) }
    }
    fun connect() {
        if (busy || typed.isBlank()) return
        busy = true; error = null
        scope.launch {
            try { ai.connect(typed); typed = "" } catch (e: AiException) { error = e.message }
            busy = false
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Use AI with your own key. Margin has no AI of its own; Gemini, Groq and NVIDIA have free keys.", style = JType.para, color = jc.ink)
        // 1. Provider, free ones first.
        Text("1. Choose a provider", style = JType.small, color = jc.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AI_PROVIDERS.sortedByDescending { it.free }.forEach { q ->
                Pill(if (q.free) "${q.short} · free" else q.short, q.id == p.id, { if (q.id != p.id) { ai.setProvider(q.id); error = null } })
            }
        }
        // 2. Its key page, in the browser.
        Text("2. Get a key", style = JType.small, color = jc.muted)
        SecondaryButton(if (p.free) "Get a free ${p.short} key" else "Get a ${p.short} key", { openLink(context, p.keyUrl) }, Modifier.fillMaxWidth())
        ProviderLink(p)
        Text("Sign in, create a key, copy it, then come back here.", style = JType.small, color = jc.muted)
        // 3. Paste and connect.
        Text("3. Paste it and connect", style = JType.small, color = jc.muted)
        OutlinedTextField(
            typed, ::take, Modifier.fillMaxWidth(), singleLine = true, shape = JShape.sm,
            label = { Text("${p.name} API key") }, placeholder = { Text(p.keyHint) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                TextAction("Paste", {
                    context.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)
                        ?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::take)
                })
            },
        )
        PrimaryButton(if (busy) "Connecting…" else "Connect", ::connect, Modifier.fillMaxWidth(), enabled = typed.isNotBlank() && !busy)
        error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = JType.small, color = jc.danger) }
        Text("The key is stored encrypted on this phone only and never synced. Notes you ask about go to ${p.name} under its terms.", style = JType.small, color = jc.muted)
    }
}

private const val AI_SYSTEM = "You help with a note in a notes app. Answer in plain text without Markdown, in the note's language. Be brief. Don't add hashtags."

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

/** Languages offered by Translate; any other can be asked for in the question field. */
private val TRANSLATE_TO = listOf("English", "বাংলা (Bengali)", "हिन्दी (Hindi)", "Español", "Français", "Deutsch", "العربية (Arabic)", "中文 (Chinese)", "日本語 (Japanese)")

internal fun translateRequest(language: String) =
    "Translate this note into $language. Keep its line structure. Answer with only the translation, without its title."

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
    var translating by remember { mutableStateOf(false) }
    // Set up from this sheet: a green line confirms it before the first question.
    var hadKey by remember { mutableStateOf(cfg.key != null) }
    var connected by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(cfg.key) { if (cfg.key != null && !hadKey) connected = true; hadKey = cfg.key != null }
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
            // Not set up yet: set up right here, then carry on with the note.
            if (cfg.key == null) {
                AiSetup(ai, Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()))
            } else if (noteText.isBlank()) {
                if (connected) Text("✓ Connected to ${p.name}.", style = JType.para.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = jc.success)
                Text("This note is empty.", style = JType.para, color = jc.muted)
            } else {
                if (connected) Text("✓ Connected to ${p.name}. Pick something to do with this note.", style = JType.para.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = jc.success)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                    AI_ASKS.forEach { a -> Chip(a.label, { ask(a.request, a.rewrites) }) }
                    Box {
                        Chip("Translate", { translating = true }, trailing = "▾")
                        DropdownMenu(translating, { translating = false }, containerColor = jc.card) {
                            TRANSLATE_TO.forEach { lang ->
                                DropdownMenuItem({ Text(lang, color = jc.ink) }, { translating = false; ask(translateRequest(lang), rewrites = true) })
                            }
                        }
                    }
                }
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
