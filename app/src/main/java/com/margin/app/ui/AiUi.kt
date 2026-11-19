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

/** What an answer is, which decides what can be done with it. Follow-ups keep their thread's kind. */
internal enum class AiKind { TEXT, CONTINUE, REWRITE_NOTE, REWRITE_SELECTION, TITLE, TAGS, CHECKLIST }

/** A ready-made request. */
private class AiAsk(val label: String, val request: String, val kind: AiKind = AiKind.TEXT)

/** On the whole note. */
private val NOTE_ASKS = listOf(
    AiAsk("Summarize", "Summarize this note in a few short lines."),
    AiAsk("Continue writing", "Continue this note from where it ends, in the same style and language, for a short paragraph. Answer with only the new text.", AiKind.CONTINUE),
    AiAsk("Improve writing", "Rewrite this note with clearer wording and correct grammar. Keep its meaning, its language and its line structure. Answer with only the rewritten note, without its title.", AiKind.REWRITE_NOTE),
    AiAsk("Fix spelling", "Correct only the spelling and grammar mistakes in this note. Change nothing else. Answer with only the corrected note, without its title.", AiKind.REWRITE_NOTE),
    AiAsk("Action items", "List the action items in this note as short tasks, one per line, each starting with \"- \". Answer with only the list.", AiKind.CHECKLIST),
    AiAsk("Make checklist", "Turn this note into a checklist of short tasks, one per line, each starting with \"- \". Answer with only the list.", AiKind.CHECKLIST),
    AiAsk("Suggest title", "Suggest a short title for this note, at most 8 words, in its language. Answer with only the title.", AiKind.TITLE),
    AiAsk("Suggest tags", "Suggest 3 to 5 short tags for this note as hashtags on one line, like #work #ideas. Lowercase, no spaces inside a tag. Answer with only the tags.", AiKind.TAGS),
)

/** On the selected text. */
private val SELECTION_ASKS = listOf(
    AiAsk("Rewrite", "Rewrite the text more clearly. Keep its meaning and its language. Answer with only the rewritten text.", AiKind.REWRITE_SELECTION),
    AiAsk("Shorten", "Make the text shorter, keeping what matters. Same language. Answer with only the new text.", AiKind.REWRITE_SELECTION),
    AiAsk("Expand", "Make the text a little longer and fuller, in the same style and language. Answer with only the new text.", AiKind.REWRITE_SELECTION),
    AiAsk("Fix spelling", "Correct only the spelling and grammar mistakes in the text. Change nothing else. Answer with only the corrected text.", AiKind.REWRITE_SELECTION),
    AiAsk("More formal", "Rewrite the text in a more formal tone. Same language. Answer with only the new text.", AiKind.REWRITE_SELECTION),
    AiAsk("Friendlier", "Rewrite the text in a warmer, friendlier tone. Same language. Answer with only the new text.", AiKind.REWRITE_SELECTION),
    AiAsk("Explain", "Explain the text simply, in a few lines, in its language."),
)

/** Quick follow-ups offered under an answer that can be redone. */
private val FOLLOW_UPS = listOf("Shorter", "Longer", "Simpler", "More formal", "Try again")

/** Languages offered by Translate; any other can be asked for in the question field. */
private val TRANSLATE_TO = listOf("English", "বাংলা (Bengali)", "हिन्दी (Hindi)", "Español", "Français", "Deutsch", "العربية (Arabic)", "中文 (Chinese)", "日本語 (Japanese)")

internal fun translateRequest(language: String) =
    "Translate this note into $language. Keep its line structure. Answer with only the translation, without its title."

private fun translateSelection(language: String) = "Translate the text into $language. Answer with only the translation."

/** Checklist items in an answer: one per line, list marks and numbers taken off. */
internal fun checklistItems(answer: String): List<String> = answer.lines()
    .map { it.trim().replace(Regex("^([-*•]|\\d+[.)]|\\[[ xX]?])\\s*"), "").replace(Regex("^\\[[ xX]?]\\s*"), "").trim() }
    .filter { it.isNotEmpty() }

/** Hashtags in an answer; words without a # get one. */
internal fun tagsIn(answer: String): List<String> {
    val found = Regex("#[\\p{L}\\p{N}_-]+").findAll(answer).map { it.value.lowercase() }.toList()
    val tags = found.ifEmpty { answer.split(Regex("[\\s,]+")).map { it.trim('#', '.', '"') }.filter { it.isNotEmpty() }.map { "#" + it.lowercase() } }
    return tags.distinct().take(8)
}

/** A suggested title: its first line, without quotes or a "Title:" label. */
internal fun titleIn(answer: String): String = answer.lines().firstOrNull { it.isNotBlank() }.orEmpty().trim()
    .removePrefix("Title:").trim().trim('"', '“', '”', '\'', '*').trim()

/** One turn of an AI thread: what was asked (as shown) and the answer. */
private class AiTurn(val asked: String, val answer: String)

/**
 * Ask AI about the open note. With [selection] (text the user had selected), its actions work on just that
 * and the answer can replace it ([onReplaceSelection]); otherwise on the whole note. After an answer, follow-ups
 * ("shorter", or anything typed) carry on the same conversation, and act on the newest answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSheet(
    ai: AiKeys, noteText: String, onInsert: ((String) -> Unit)?, onReplace: ((String) -> Unit)?, onClose: () -> Unit,
    selection: String? = null, onReplaceSelection: ((String) -> Unit)? = null,
    onTitle: ((String) -> Unit)? = null, onChecklist: ((List<String>) -> Unit)? = null, onTags: ((String) -> Unit)? = null,
) {
    val cfg by ai.config.collectAsState()
    val p = aiProvider(cfg.provider)
    val scope = rememberCoroutineScope() // leaves with the sheet, so closing it drops the answer
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var translating by remember { mutableStateOf(false) }
    // Working on the selection, or the whole note. Starts on the selection when there is one.
    var onSelection by remember { mutableStateOf(!selection.isNullOrBlank()) }
    // The thread: what was sent ("user"/"assistant" text, for follow-ups) and what's shown.
    val turns = remember { androidx.compose.runtime.mutableStateListOf<AiTurn>() }
    val history = remember { mutableListOf<Pair<String, String>>() }
    var kind by remember { mutableStateOf(AiKind.TEXT) }
    // Set up from this sheet: a green line confirms it before the first question.
    var hadKey by remember { mutableStateOf(cfg.key != null) }
    var connected by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(cfg.key) { if (cfg.key != null && !hadKey) connected = true; hadKey = cfg.key != null }
    val busy = job?.isActive == true
    val answer = turns.lastOrNull()?.answer

    fun material(): String {
        val note = noteText.take(AI_MAX_CHARS)
        return if (onSelection && selection != null) "Text:\n${selection.take(AI_MAX_CHARS)}\n\nIt is part of this note, for context only:\n$note" else "Note:\n$note"
    }
    /** A new thread: [request] about the note or selection. */
    fun ask(label: String, request: String, k: AiKind) {
        if (busy) return
        error = null; kind = k; turns.clear(); history.clear()
        val user = "$request\n\n${material()}"
        job = scope.launch {
            try {
                val a = ai.ask(AI_SYSTEM, user)
                history += "user" to user; history += "assistant" to a
                turns += AiTurn(label, a)
            } catch (e: AiException) { error = e.message }
            job = null
        }
    }
    /** Carries on the thread with [request]; the answer replaces what the thread's kind acts on. */
    fun followUp(request: String) {
        if (busy || request.isBlank()) return
        error = null
        val user = when (request) {
            "Try again" -> "Give a different version of your last answer, in the same form."
            else -> "$request. Answer in the same form as before, with only the result."
        }.let { if (request in FOLLOW_UPS) it else request }
        val past = history.toList()
        job = scope.launch {
            try {
                val a = ai.ask(AI_SYSTEM, user, past)
                history += "user" to user; history += "assistant" to a
                turns += AiTurn(request, a)
            } catch (e: AiException) { error = e.message }
            job = null
        }
    }
    fun copy(a: String) = context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("AI answer", a))

    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = jc.bg, shape = JShape.sheet,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) { TextAction("Close", onClose) }
                Text("Ask AI", Modifier.align(Alignment.Center), style = JType.rowTitle, color = jc.ink)
                if (turns.isNotEmpty() && !busy) Box(Modifier.align(Alignment.CenterEnd)) { TextAction("New", { turns.clear(); history.clear(); error = null }) }
            }
            // Not set up yet: set up right here, then carry on with the note.
            if (cfg.key == null) {
                AiSetup(ai, Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()))
            } else if (noteText.isBlank() && selection.isNullOrBlank()) {
                if (connected) Text("✓ Connected to ${p.name}.", style = JType.para.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = jc.success)
                Text("This note is empty.", style = JType.para, color = jc.muted)
            } else {
                if (connected) Text("✓ Connected to ${p.name}. Pick something to do with this note.", style = JType.para.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = jc.success)
                if (turns.isEmpty()) {
                    // What it works on: the selection or the whole note.
                    if (!selection.isNullOrBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Selected text", onSelection, { onSelection = true })
                            Pill("Whole note", !onSelection, { onSelection = false })
                        }
                        if (onSelection) Text(
                            "“" + selection.trim().replace('\n', ' ').let { if (it.length > 140) it.take(140) + "…" else it } + "”",
                            style = JType.small, color = jc.muted, maxLines = 3,
                        )
                    }
                    val asks = if (onSelection) SELECTION_ASKS else NOTE_ASKS
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        asks.forEach { a -> Chip(a.label, { ask(a.label, a.request, a.kind) }) }
                        Box {
                            Chip("Translate", { translating = true }, trailing = "▾")
                            DropdownMenu(translating, { translating = false }, containerColor = jc.card) {
                                TRANSLATE_TO.forEach { lang ->
                                    DropdownMenuItem({ Text(lang, color = jc.ink) }, {
                                        translating = false
                                        if (onSelection) ask("Translate to $lang", translateSelection(lang), AiKind.REWRITE_SELECTION)
                                        else ask("Translate to $lang", translateRequest(lang), AiKind.REWRITE_NOTE)
                                    })
                                }
                            }
                        }
                    }
                }
                // The thread so far: each request, then its answer; the newest is the one acted on.
                if (turns.isNotEmpty()) Column(
                    Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState(), reverseScrolling = true),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    turns.forEachIndexed { i, t ->
                        val last = i == turns.lastIndex
                        Text(t.asked, style = JType.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = jc.accent)
                        SelectionContainer { Text(t.answer, style = JType.para, color = if (last) jc.ink else jc.muted, maxLines = if (last) Int.MAX_VALUE else 3) }
                    }
                }
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = jc.accent, strokeWidth = 2.dp)
                        Text("Asking ${p.name}…", style = JType.para, color = jc.muted)
                        Spacer(Modifier.weight(1f))
                        TextAction("Stop", { job?.cancel(); job = null })
                    }
                    error?.let { Text(it, style = JType.para, color = jc.danger) }
                    if (answer != null && !busy) {
                        // What can be done with the newest answer depends on what was asked.
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            when (kind) {
                                AiKind.REWRITE_SELECTION -> onReplaceSelection?.let { TextAction("Replace selection", { it(answer) }, bold = true) }
                                AiKind.REWRITE_NOTE -> onReplace?.let { TextAction("Replace note", { it(answer) }, bold = true) }
                                AiKind.TITLE -> onTitle?.let { TextAction("Use as title", { it(titleIn(answer)) }, bold = true) }
                                AiKind.TAGS -> onTags?.let { TextAction("Add tags", { it(tagsIn(answer).joinToString(" ")) }, bold = true) }
                                AiKind.CHECKLIST -> onChecklist?.let { TextAction("Add as checklist", { it(checklistItems(answer)) }, bold = true) }
                                AiKind.CONTINUE -> onInsert?.let { TextAction("Add to note", { it(answer) }, bold = true) }
                                AiKind.TEXT -> {}
                            }
                            if (kind !in setOf(AiKind.CONTINUE, AiKind.TAGS, AiKind.TITLE)) onInsert?.let { TextAction(if (kind == AiKind.TEXT) "Add to note" else "Add below", { it(answer) }, bold = kind == AiKind.TEXT) }
                            TextAction("Copy", { copy(answer) })
                        }
                        // Follow-ups: quick ones, or anything typed below.
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FOLLOW_UPS.forEach { f -> Chip(f, { followUp(f) }) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        question, { question = it }, Modifier.weight(1f), shape = JShape.sm, maxLines = 3,
                        label = { Text(if (answer != null) "Ask a follow-up" else if (onSelection && selection != null) "Ask about the selection" else "Ask about this note") },
                    )
                    TextAction("Ask", {
                        val q = question.trim(); question = ""
                        if (answer != null) followUp(q) else ask(q, q, AiKind.TEXT)
                    }, bold = true, enabled = question.isNotBlank() && !busy)
                }
                Text(
                    "Sends " + (if (onSelection && selection != null) "the selected text and this note" else "this note's text") + " to ${p.name} with your key, under ${p.name}'s terms." +
                        if (noteText.length > AI_MAX_CHARS) " Only the first part of this long note is sent." else "",
                    style = JType.small, color = jc.muted,
                )
            }
            Spacer(Modifier.width(1.dp).navigationBarsPadding())
        }
    }
}
