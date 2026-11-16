package com.margin.app.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import com.margin.app.data.Attachment
import com.margin.app.data.BlockType
import com.margin.app.data.Drawing
import com.margin.app.data.GrammarIssue
import com.margin.app.data.LinkPreviews
import com.margin.app.data.LoadedPreview
import com.margin.app.data.MarginJson
import com.margin.app.data.Mark
import com.margin.app.data.MathSuggestion
import com.margin.app.data.NoteEntity
import com.margin.app.data.NoteFont
import com.margin.app.data.Paper
import com.margin.app.data.Plot
import com.margin.app.data.TableGraph
import com.margin.app.data.color
import com.margin.app.data.decodeImage
import com.margin.app.data.findLinks
import com.margin.app.data.findPhones
import com.margin.app.data.findTags
import com.margin.app.data.findUrls
import com.margin.app.data.hostOf
import com.margin.app.data.invoke
import com.margin.app.data.isAudio
import com.margin.app.data.isDrawing
import com.margin.app.data.isLink
import com.margin.app.data.isLocked
import com.margin.app.data.isPdf
import com.margin.app.data.linkKey
import com.margin.app.data.mathSuggestionAt
import com.margin.app.data.plainText
import com.margin.app.data.plotsIn
import com.margin.app.data.resolveLink
import com.margin.app.data.tableGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val GUTTER = 30.sp
internal const val INDENT_STEP = 24f // sp per indent step
internal const val QUOTE_GAP = 16f // sp between a quote's bar and its text

/** Space in sp before a line's marker or text: its indent, then the quote bar. */
internal fun leadOf(meta: LineMeta) = INDENT_STEP * meta.indent + (if (meta.quote) QUOTE_GAP else 0f) + if (meta.ai) AI_LEAD else 0f
internal const val AI_LEAD = 8f // sp between an AI card's edge and its text
internal const val AI_HEADER = 44f // sp: the height of an AI card's badge row
internal const val AI_BADGE = 60f // sp kept free before an AI card's first line for its "AI" badge

/** True for the first line of a run of AI lines: it carries the card's badge. */
internal fun aiRunStart(doc: EditorDoc, i: Int) = doc.lines[i].ai && doc.lines.getOrNull(i - 1)?.ai != true

/** For each line, true when it sits in a folded AI card under the card's first line. */
internal fun aiFoldedLines(doc: EditorDoc): BooleanArray {
    var folded = false
    return BooleanArray(doc.lines.size) { i ->
        val m = doc.lines[i]
        if (aiRunStart(doc, i)) { folded = m.folded; false } else m.ai && folded
    }
}
internal val FOLDED_SIZE = 0.5.sp // a folded line still exists in the field, just too small to see

/** Extra room under each line of text on four-line paper, as a share of its font size. */
internal const val FOUR_LINE_GAP = 0.6f

/** Extra height (sp) of a to-do line, split above and below it, so the items don't sit tight together. */
internal const val CHECK_GAP = 12f

@Composable
internal fun lineStyle(type: BlockType, font: FontFamily = Inter): TextStyle = when (type) {
    BlockType.TITLE -> JType.noteTitle.copy(fontFamily = font)
    BlockType.HEADING -> JType.heading.copy(fontFamily = font)
    BlockType.SUBHEADING -> JType.subheading.copy(fontFamily = font)
    BlockType.MONOSPACED -> TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFamily = FontFamily.Monospace)
    else -> JType.body.copy(fontFamily = font)
}.copy(color = jc.ink)

val NoteFont.family: FontFamily get() = when (this) {
    NoteFont.DEFAULT -> Inter
    NoteFont.SANS -> FontFamily.SansSerif
    NoteFont.SERIF -> systemSerif
    NoteFont.MONO -> FontFamily.Monospace
    NoteFont.HANDWRITING -> FontFamily.Cursive
}

/**
 * Noto Serif straight from the system's font files: Samsung's font styles swap the generic serif family for
 * the phone's own font, so [FontFamily.Serif] can come out sans. Phones without the files get the generic one.
 */
internal val systemSerif: FontFamily by lazy {
    runCatching {
        fun f(file: String, weight: FontWeight, style: androidx.compose.ui.text.font.FontStyle) =
            java.io.File("/system/fonts/$file").takeIf { it.canRead() }?.let { androidx.compose.ui.text.font.Font(it, weight, style) }
        val normal = androidx.compose.ui.text.font.FontStyle.Normal
        val italic = androidx.compose.ui.text.font.FontStyle.Italic
        listOfNotNull(
            f("NotoSerif-Regular.ttf", FontWeight.Normal, normal), f("NotoSerif-Bold.ttf", FontWeight.Bold, normal),
            f("NotoSerif-Italic.ttf", FontWeight.Normal, italic), f("NotoSerif-BoldItalic.ttf", FontWeight.Bold, italic),
        ).takeIf { it.isNotEmpty() }?.let { FontFamily(it) }
    }.getOrNull() ?: FontFamily.Serif
}

/** Picks the typeface of a note's text; each choice is shown in its own font. */
@Composable
internal fun FontDialog(current: NoteFont, onPick: (NoteFont) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(JShape.lg).background(jc.bg).padding(vertical = 16.dp)) {
            Text("Font", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink)
            Text("Used for this note's text.", Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted)
            Spacer(Modifier.height(8.dp))
            NoteFont.entries.forEach { f ->
                val on = f == current
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable(role = Role.RadioButton) { onPick(f) }
                        .semantics { selected = on }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Aa", Modifier.width(36.dp), style = JType.item.copy(fontFamily = f.family, fontSize = 20.sp), color = jc.ink)
                    Text(f.label, Modifier.weight(1f), style = JType.item.copy(fontFamily = f.family), color = if (on) jc.accent else jc.ink)
                    if (on) JIcon(Ico.CHECK, jc.accent, size = 18.dp, stroke = 2.6f)
                }
            }
        }
    }
}

/**
 * The whole note in one text field, so Enter, Backspace and fast typing never move focus between
 * fields. Each line gets its style through a visual transformation (same text, so offsets map 1:1);
 * list markers and checkboxes are drawn in the left gutter, aligned to the laid-out lines.
 */
@Composable
internal fun NoteField(
    vm: EditorViewModel,
    matches: List<IntRange>,
    matchAt: Int,
    modifier: Modifier,
    onReveal: (Float) -> Unit,
    onOpenDrawing: (Int, Drawing) -> Unit,
    onViewPhoto: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onPreviewLink: (String) -> Unit,
) {
    val requester = remember { FocusRequester() }
    // Lives next to the field so the field is attached when it runs: on a new note, and again after
    // rotation, when the field is recreated but the ViewModel remembers it had focus.
    LaunchedEffect(Unit) {
        if (vm.focusOnOpen || vm.focused) runCatching { requester.requestFocus() }
        vm.focusOnOpen = false
    }
    // Switching between the math keypad and the phone's keyboard restarts the field's input, so the phone's
    // keyboard goes away (or comes back) at once.
    val focusManager = LocalFocusManager.current
    var keysWere by remember { mutableStateOf(vm.mathKeys) }
    LaunchedEffect(vm.mathKeys) {
        if (keysWere == vm.mathKeys) return@LaunchedEffect
        keysWere = vm.mathKeys
        if (vm.focused) {
            focusManager.clearFocus()
            runCatching { requester.requestFocus() }
        }
    }
    val doc = vm.doc
    val styles = BlockType.entries.associateWith { lineStyle(it, vm.font.family) }
    val muted = jc.muted
    val monoBackground = jc.surface
    val quoteBar = jc.muted.copy(alpha = 0.5f)
    val aiTint = jc.accent
    val aiMeasurer = rememberTextMeasurer()
    val aiLabel = remember(aiMeasurer, aiTint) { aiMeasurer.measure("✦ AI", JType.caption.copy(color = aiTint, fontWeight = FontWeight.SemiBold)) }
    val aiHiddenLabel = remember(aiMeasurer, muted) { aiMeasurer.measure("Hidden", JType.caption.copy(color = muted)) }
    val tagColor = jc.accent
    val targets by vm.linkTargets.collectAsState()
    val targetKeys = remember(targets) { targets.map { linkKey(it.title) }.toSet() }
    val links = remember(doc) { findLinks(doc.text) }
    // Phone numbers: a tap offers call, message, WhatsApp, copy, or editing the number.
    // Web addresses: underlined like links; a tap opens the page, pressing and holding selects as usual.
    val urls = remember(doc) { findUrls(doc.text) }
    val phones = remember(doc, urls) { findPhones(doc.text).filter { p -> urls.none { it.first.first <= p.first.last && p.first.first <= it.first.last } } }
    var phoneAt by remember { mutableStateOf<Pair<String, Int>?>(null) }
    phoneAt?.let { (number, offset) ->
        PhoneActions(
            number,
            onEdit = if (vm.readOnly) null else ({
                phoneAt = null
                vm.onValueChange(vm.value.copy(selection = TextRange(offset.coerceIn(0, vm.value.text.length))))
                runCatching { requester.requestFocus() }
            }),
            onDismiss = { phoneAt = null },
        )
    }
    // A PDF open full screen, by its line's id (its file changes when a tool edits it).
    var viewingPdf by remember { mutableStateOf<String?>(null) }
    viewingPdf?.let { id -> PdfViewer(vm, id, onClose = { viewingPdf = null }) }
    // The table editor, for a table just added or tapped.
    vm.editingTable?.let { id ->
        val line = vm.doc.lines.indexOfFirst { it.id == id }
        val table = vm.doc.lines.getOrNull(line)?.table
        if (table == null) LaunchedEffect(id) { vm.editingTable = null }
        else androidx.compose.runtime.key(id) {
            TableEditor(
                table,
                onDone = { vm.updateTable(id, it); vm.editingTable = null },
                onDelete = { vm.removeAttachment(line); vm.editingTable = null },
            )
        }
    }
    val context = LocalContext.current
    val px = LocalDensity.current
    // An attachment's line is as tall as its card, which is drawn over it.
    val imageLine = with(px) { IMAGE_CARD.toSp() }
    val fileLine = with(px) { FILE_CARD.toSp() }
    val pdfLine = with(px) { PDF_CARD.toSp() }
    // Drawings sit in the text like handwriting, so each one's line is as tall as its ink at the page width.
    // A plain copy: the remembered text below must re-run when a drawing finishes loading.
    val drawings = rememberDrawings(doc, vm.files).toMap()
    var pageWidth by remember { mutableIntStateOf(0) }
    val drawingGap = with(px) { (DRAWING_GAP * 2).toPx() }
    // Photos fill the page width at their own shape (up to MAX_PHOTO tall), so their lines are sized from that.
    val photos = rememberPhotoSizes(doc, vm.files).toMap()
    val photoGap = with(px) { 12.dp.toPx() }
    fun photoLine(a: Attachment) = photos[a.file]?.takeIf { pageWidth > 0 }?.let { (w, h) ->
        with(px) { (minOf(pageWidth * h / w, MAX_PHOTO.toPx()) + photoGap).toSp() }
    } ?: imageLine
    fun drawingLine(a: Attachment) = drawings[a.file]?.takeIf { pageWidth > 0 }?.let { d ->
        with(px) { (inlineHeight(d, pageWidth.toFloat()) + drawingGap).coerceIn(MIN_DRAWING.toPx(), MAX_DRAWING.toPx()).toSp() }
    } ?: imageLine
    // A line that is just a link to a note ("[[Plan]]") shows a card with the note's first lines under it.
    val linkPreviews = remember(doc, targets) { linkPreviewLines(doc, targets) }
    fun previewExtra(rows: Int) = with(px) { linkCardHeight(rows).toSp() }
    val findColor = jc.accent
    // Four-line paper: each line of text gets room for a gap under its band, like a copybook.
    val fourLine = vm.paper == Paper.FOUR_LINE
    fun textHeight(style: TextStyle) = if (fourLine) (style.lineHeight.value + style.fontSize.value * FOUR_LINE_GAP).sp else style.lineHeight
    val foldedLines = remember(doc) { aiFoldedLines(doc) }
    val annotated = remember(doc, styles, muted, tagColor, targetKeys, urls, imageLine, matches, matchAt, drawings, pageWidth, linkPreviews, photos, fourLine) {
        buildAnnotatedString {
            // Each ParagraphStyle range is already its own paragraph, so a real '\n' would add an empty one
            // after every line. Show a zero-width space in its place (same length, so offsets still map 1:1).
            append(doc.text.replace('\n', '​'))
            var start = 0
            doc.texts.forEachIndexed { i, line ->
                val meta = doc.lines[i]
                val style = styles.getValue(meta.type)
                val end = start + line.length
                val paragraphEnd = if (i < doc.lines.lastIndex) end + 1 else end
                val indent = (leadOf(meta) + if (meta.type in MARKER_TYPES) GUTTER.value else 0f).sp
                val firstIndent = if (aiRunStart(doc, i)) (indent.value + AI_BADGE).sp else indent // old cards have text on the badge's line
                val preview = linkPreviews[i]
                val height = meta.table?.let { with(px) { tableHeight(it).toSp() } }
                    ?: meta.attachment?.let { if (it.isDrawing) drawingLine(it) else if (it.isImage) photoLine(it) else if (it.isPdf) pdfLine else fileLine }
                    ?: preview?.let { (style.lineHeight.value + previewExtra(it.second.size).value).sp }
                    // shortcut: the gap goes under every row of a wrapped title, not just its last; fine while titles are short.
                    ?: if (i == 0) (textHeight(style).value + TITLE_GAP).sp
                    else if (aiRunStart(doc, i) && line.isEmpty()) AI_HEADER.sp
                    // shortcut: the room goes on every row of a wrapped item, not only between items (Compose has no paragraph spacing).
                    else if (meta.type == BlockType.CHECK && !fourLine) (textHeight(style).value + CHECK_GAP).sp
                    else textHeight(style)
                // A one-row paragraph is trimmed to its font height by default; an attachment's line must keep its full height.
                // A link with a preview keeps its text at the top, leaving the space below for the card.
                val heightStyle = when {
                    meta.isObject -> LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
                    preview != null -> LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.None)
                    fourLine -> LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
                    i == 0 -> LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.None) // the gap below the title
                    meta.ai && line.isEmpty() -> LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
                    meta.type == BlockType.CHECK -> LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None) // room above and below
                    else -> null
                }
                if (foldedLines[i]) {
                    addStyle(ParagraphStyle(lineHeight = FOLDED_SIZE, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)), start, paragraphEnd)
                    addStyle(SpanStyle(fontSize = FOLDED_SIZE, color = Color.Transparent), start, paragraphEnd)
                    start = end + 1
                    return@forEachIndexed
                }
                addStyle(ParagraphStyle(lineHeight = height, textIndent = TextIndent(firstIndent, indent), lineHeightStyle = heightStyle), start, paragraphEnd)
                val checked = meta.type == BlockType.CHECK && meta.checked
                addStyle(
                    style.toSpanStyle().copy(
                        color = if (checked) muted else style.color,
                        textDecoration = if (checked) TextDecoration.LineThrough else null,
                    ),
                    start, end,
                )
                // An empty line has no text to carry its font, so it would be laid out at body size: an empty title
                // came out short, the "Title" hint overlapped the next line and the cursor stuck out above it.
                // Its line break (a zero-width space) takes the font instead.
                if (line.isEmpty() && paragraphEnd > start) {
                    addStyle(SpanStyle(fontSize = style.fontSize, fontWeight = style.fontWeight, fontFamily = style.fontFamily), start, paragraphEnd)
                }
                if (meta.isObject) addStyle(SpanStyle(color = Color.Transparent), start, end) // the card covers it
                start = end + 1
            }
            // Bold, italic, ... from the Format panel.
            // shortcut: underline replaces a checked line's strike-through on that text; combine decorations if it matters.
            doc.marks.forEach { m ->
                val style = when (m.mark) {
                    Mark.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    Mark.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    Mark.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                    Mark.HIGHLIGHT -> SpanStyle(background = m.color.swatch())
                }
                addStyle(style, m.start.coerceIn(0, doc.text.length), m.end.coerceIn(0, doc.text.length))
            }
            // Tags stand out from the text around them (after line styles, so they win).
            findTags(doc.text).forEach { (range, _) ->
                addStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold), range.first, range.last + 1)
            }
            // Links: underlined in the accent colour, or greyed and struck through when the note is gone.
            links.forEach { (range, title) ->
                val found = linkKey(title) in targetKeys
                addStyle(
                    SpanStyle(
                        color = if (found) tagColor else muted,
                        textDecoration = if (found) TextDecoration.Underline else TextDecoration.LineThrough,
                    ),
                    range.first, range.last + 1,
                )
            }
            // Phone numbers: underlined in the accent colour, like links.
            phones.forEach { (range, _) ->
                addStyle(SpanStyle(color = tagColor, textDecoration = TextDecoration.Underline), range.first, range.last + 1)
            }
            urls.forEach { (range, _) ->
                addStyle(SpanStyle(color = tagColor, textDecoration = TextDecoration.Underline), range.first, range.last + 1)
            }
            // Find in note: every match tinted, the current one stronger.
            matches.forEachIndexed { i, r ->
                addStyle(SpanStyle(background = findColor.copy(alpha = if (i == matchAt) 0.45f else 0.18f)), r.first, r.last + 1)
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    /** Where the last finger touched the field, to put a tapped cursor on the row that was tapped (see [placeTap]). */
    var lastTap by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(matches, matchAt, layout) {
        val l = layout ?: return@LaunchedEffect
        val r = matches.getOrNull(matchAt) ?: return@LaunchedEffect
        if (r.first < l.layoutInput.text.length) onReveal(l.getBoundingBox(r.first).top)
    }
    val density = LocalDensity.current
    val activity = LocalContext.current as? Activity
    var hadFocus by remember { mutableStateOf(false) }

    // The built-in cursor is as tall as the line height; ours is drawn the height of the text instead.
    // shortcut: it blinks on a fixed beat rather than staying solid while typing.
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        1f, 0f, infiniteRepeatable(keyframes { durationMillis = 1000; 1f at 0; 1f at 499; 0f at 500 }), label = "blink",
    )
    val cursorColor = jc.accent
    val spelling = rememberMisspellings(doc.text, enabled = !vm.readOnly)
    val squiggle = jc.danger
    // Grammar mistakes, underlined in blue. Where both flag the same words, a typo wins, but our grammar
    // check (which explains itself) wins over the keyboard's own grammar flags.
    val grammarFound = rememberGrammarIssues(doc.text, enabled = !vm.readOnly && vm.grammarOn, cloud = vm.cloudGrammarOn)
    val misspelled = spelling.filter { m -> !m.grammar || grammarFound.none { it.start < m.end && m.start < it.end } }
    val grammar = grammarFound.filter { g -> misspelled.none { it.start < g.end && g.start < it.end } }
    val grammarLine = GRAMMAR_BLUE
    // Suggestions for a flagged word the cursor was just put in (by a tap, not by typing).
    var fixing by remember { mutableStateOf<Misspelling?>(null) }
    var grammarFix by remember { mutableStateOf<GrammarIssue?>(null) }
    var graphing by remember { mutableStateOf<List<Plot>?>(null) }
    var tableGraph by remember { mutableStateOf<TableGraph?>(null) } // set with graphing when it came from a table
    var lastValue by remember { mutableStateOf(vm.value) }
    LaunchedEffect(vm.value) {
        val v = vm.value
        val tapped = v.text == lastValue.text && v.selection != lastValue.selection && v.selection.collapsed
        fixing = if (tapped) misspelled.firstOrNull { v.selection.start in it.start..it.end }?.takeIf { it.suggestions.isNotEmpty() } else null
        grammarFix = if (tapped && fixing == null) grammar.firstOrNull { v.selection.start in it.start..it.end } else null
        lastValue = v
    }

    // Paper: rules go under text lines only; an attachment, table or link card keeps its own background.
    val paper = vm.paper
    val bodyLine = with(px) { textHeight(JType.body).toPx() }
    val paperLine = paperColors()
    // The paper runs on past the text to fill the rest of the screen (the note's scroll view clips it).
    val pageBelow = with(px) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val lineStarts = remember(doc) { doc.texts.runningFold(0) { at, t -> at + t.length + 1 }.dropLast(1) }
    fun docLine(offset: Int) = lineStarts.binarySearch(offset).let { if (it >= 0) it else -it - 2 }.coerceIn(0, doc.lines.lastIndex)
    // No paper under objects (they carry their own background) or under the title and headings.
    fun bareLine(offset: Int): Boolean = docLine(offset).let { i ->
        doc.lines[i].isObject || linkPreviews[i] != null ||
            doc.lines[i].type in setOf(BlockType.TITLE, BlockType.HEADING, BlockType.SUBHEADING)
    }
    val fontPx = styles.mapValues { (_, s) -> with(px) { s.fontSize.toPx() } }
    val bodyFont = with(px) { JType.body.fontSize.toPx() }
    Box(modifier.fillMaxWidth().onSizeChanged { pageWidth = it.width }) {
        BasicTextField(
            value = vm.value,
            onValueChange = { v ->
                val tap = lastTap
                lastTap = null
                vm.onValueChange(if (tap != null && v.text == vm.value.text) placeTap(v, tap, layout) else v)
            },
            readOnly = vm.readOnly,
            // A new note's only line is its empty title, with no characters at all to style: size it as a title.
            textStyle = (if (doc.text.isEmpty()) styles.getValue(BlockType.TITLE) else styles.getValue(BlockType.BODY)).copy(color = jc.ink),
            cursorBrush = SolidColor(Color.Transparent),
            visualTransformation = { TransformedText(annotated, OffsetMapping.Identity) },
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth()
                // The note's paper, under the text and lined up with it.
                .drawBehind {
                    val l = layout ?: return@drawBehind
                    if (paper == Paper.PLAIN || l.layoutInput.text.length != doc.text.length) return@drawBehind
                    // Out to the screen edges (past the page's 24dp margin), so the text sits inside the paper.
                    val bleed = -24.dp.toPx()
                    inset(bleed, 0f, bleed, 0f) {
                        drawNotePaper(
                            paper, l, bodyLine, bodyFont,
                            fontOf = { i -> fontPx.getValue(doc.lines[docLine(l.getLineStart(i))].type) },
                            skip = { i -> bareLine(l.getLineStart(i)) }, colors = paperLine, below = pageBelow,
                        )
                    }
                }
                // A tap on a link opens its note instead of placing the cursor; pressing and holding previews it.
                // A tap on a phone number shows what to do with it.
                // Notes each touch's position (without taking it) so [placeTap] can check where a tap put the cursor.
                .pointerInput(Unit) {
                    awaitEachGesture { lastTap = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position }
                }
                .pointerInput(links, phones, urls) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        val l = layout ?: return@awaitEachGesture
                        if (l.layoutInput.text.length != doc.text.length) return@awaitEachGesture
                        val offset = l.getOffsetForPosition(down.position)
                        // getOffsetForPosition snaps to the nearest character; make sure the finger is on the link's row.
                        fun hit(range: IntRange) = offset in range.first..range.last &&
                            l.getBoundingBox(offset.coerceAtMost(l.layoutInput.text.length - 1)).let { down.position.y in it.top..it.bottom }
                        val link = links.firstOrNull { hit(it.first) }
                        val web = if (link == null) urls.firstOrNull { hit(it.first) } else null
                        if (web != null) {
                            // A quick tap opens it; a long press is left to the field, so the address can be selected and edited.
                            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation(PointerEventPass.Initial) }
                                ?: return@awaitEachGesture
                            down.consume()
                            up.consume()
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(web.second))) } catch (_: android.content.ActivityNotFoundException) {
                                Toast.makeText(context, "No app on this phone can open web links.", Toast.LENGTH_SHORT).show()
                            }
                            return@awaitEachGesture
                        }
                        if (link == null) {
                            val phone = phones.firstOrNull { hit(it.first) } ?: return@awaitEachGesture
                            down.consume()
                            val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                            up.consume()
                            phoneAt = phone.second to offset
                            return@awaitEachGesture
                        }
                        down.consume()
                        var cancelled = false
                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation(PointerEventPass.Initial).also { if (it == null) cancelled = true }
                        }
                        if (cancelled) return@awaitEachGesture
                        if (up == null) {
                            onPreviewLink(link.second)
                            // Swallow the rest of the press so it doesn't select text underneath.
                            do {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                e.changes.forEach { it.consume() }
                            } while (e.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        up.consume()
                        onOpenLink(link.second)
                    }
                }
                .focusRequester(requester)
                .onFocusChanged {
                    // Only a real loss clears the flag: not the "unfocused" report a freshly created field sends
                    // first, and not the teardown during rotation. That way focus comes back after rotating.
                    if (it.isFocused) {
                        hadFocus = true
                        vm.focused = true
                    } else if (hadFocus && activity?.isChangingConfigurations != true) {
                        hadFocus = false
                        vm.focused = false
                    }
                }
                .drawWithContent {
                    drawContent()
                    val l = layout ?: return@drawWithContent
                    val flagged = misspelled.map { Triple(it.start, it.end, squiggle) } + grammar.map { Triple(it.start, it.end, grammarLine) }
                    if (l.layoutInput.text.length == doc.text.length) flagged.forEach { (start, end, color) ->
                        // A wavy underline under the flagged words (per row, should they wrap).
                        val wave = 2.dp.toPx()
                        val step = 3.dp.toPx()
                        (start until end).groupBy { l.getLineForOffset(it) }.forEach { (row, offsets) ->
                            val y = l.getLineBaseline(row) + 3.dp.toPx()
                            val x0 = l.getBoundingBox(offsets.first()).left
                            val x1 = l.getBoundingBox(offsets.last()).right
                            val path = Path().apply {
                                moveTo(x0, y)
                                var x = x0
                                var up = true
                                while (x < x1) { x = minOf(x + step, x1); lineTo(x, if (up) y - wave / 2 else y + wave / 2); up = !up }
                            }
                            drawPath(path, color, style = Stroke(1.2.dp.toPx()))
                        }
                    }
                    val sel = vm.value.selection
                    if (!vm.focused || vm.readOnly || !sel.collapsed || l.layoutInput.text.length != doc.text.length) return@drawWithContent
                    val at = sel.start.coerceIn(0, doc.text.length)
                    // The doc line the cursor is on decides the font size (title, heading, body...).
                    var lineStart = 0
                    val i = doc.texts.indexOfFirst { t -> (at <= lineStart + t.length).also { if (!it) lineStart += t.length + 1 } }
                    val fs = styles.getValue(doc.lines[i.coerceAtLeast(0)].type).fontSize.let { if (it.isSp) it else JType.body.fontSize }.toPx()
                    val baseline = l.getLineBaseline(l.getLineForOffset(at))
                    val x = l.getHorizontalPosition(at, true)
                    drawRect(cursorColor.copy(alpha = blink), Offset(x, baseline - fs * 0.92f), Size(2.dp.toPx(), fs * 1.16f))
                }
                .drawBehind {
                    val l = layout ?: return@drawBehind
                    var aiTop = 0f
                    forEachLineBounds(doc, l) { i, top, bottom, firstBottom ->
                        // An AI run is one rounded card, with the badge in the room its first line keeps free.
                        if (aiRunStart(doc, i)) aiTop = top
                        if (doc.lines[i].ai && doc.lines.getOrNull(i + 1)?.ai != true) {
                            val left = (INDENT_STEP * doc.lines[i].indent).sp.toPx() - 10.dp.toPx()
                            // shortcut: the room under the last line is drawn over the next line's leading; give the card real bottom space if text below looks crowded.
                            val end = if (foldedLines.getOrNull(i) == true) bottom else bottom + 10.dp.toPx()
                            val box = Size(size.width - left + 10.dp.toPx(), end - aiTop)
                            drawRoundRect(aiTint.copy(alpha = 0.06f), Offset(left, aiTop), box, CornerRadius(16.dp.toPx()))
                            drawRoundRect(aiTint.copy(alpha = 0.22f), Offset(left, aiTop), box, CornerRadius(16.dp.toPx()), style = Stroke(1.dp.toPx()))
                        }
                        if (aiRunStart(doc, i)) {
                            // The badge: a soft pill in the accent colour, centred in the first row.
                            val x = (INDENT_STEP * doc.lines[i].indent + AI_LEAD).sp.toPx()
                            val padX = 8.dp.toPx()
                            val padY = 3.dp.toPx()
                            val pill = Size(aiLabel.size.width + padX * 2, aiLabel.size.height + padY * 2)
                            val y = (top + firstBottom - pill.height) / 2
                            drawRoundRect(aiTint.copy(alpha = 0.14f), Offset(x, y), pill, CornerRadius(pill.height / 2))
                            drawText(aiLabel, topLeft = Offset(x + padX, y + padY))
                            if (doc.lines[i].folded) drawText(aiHiddenLabel, topLeft = Offset(x + pill.width + 8.dp.toPx(), y + padY))
                        }
                        if (doc.lines[i].type == BlockType.MONOSPACED) {
                            drawRect(monoBackground, topLeft = Offset(-8.dp.toPx(), top), size = Size(size.width + 16.dp.toPx(), bottom - top))
                        }
                        if (doc.lines[i].quote) {
                            drawRect(quoteBar, topLeft = Offset((INDENT_STEP * doc.lines[i].indent).sp.toPx(), top), size = Size(3.dp.toPx(), bottom - top))
                        }
                    }
                },
        )
        if (doc.texts.first().isEmpty()) {
            Text("Title", style = styles.getValue(BlockType.TITLE).copy(color = muted))
        }
        val l = layout
        fixing?.takeIf { l != null && it.end <= l.layoutInput.text.length }?.let { m ->
            val box = l!!.getBoundingBox(m.start)
            Popup(
                offset = IntOffset(box.left.toInt(), (box.bottom + with(density) { 4.dp.toPx() }).toInt()),
                onDismissRequest = { fixing = null },
            ) {
                Row(
                    Modifier.clip(JShape.sm).background(jc.bg).border(1.dp, jc.line, JShape.sm).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    m.suggestions.take(3).forEach { word ->
                        TextAction(word, {
                            fixing = null
                            val t = vm.value.text
                            if (t.regionMatches(m.start, m.word, 0, m.word.length)) {
                                vm.onValueChange(TextFieldValue(t.replaceRange(m.start, m.end, word), TextRange(m.start + word.length)))
                            }
                        })
                    }
                }
            }
        }
        // A tapped grammar mistake: why it's flagged, and the fixes to choose from.
        grammarFix?.takeIf { l != null && it.end <= l.layoutInput.text.length }?.let { g ->
            val box = l!!.getBoundingBox(g.start)
            Popup(
                offset = IntOffset(box.left.toInt(), (box.bottom + with(density) { 4.dp.toPx() }).toInt()),
                onDismissRequest = { grammarFix = null },
            ) {
                Column(
                    Modifier.widthIn(max = 300.dp).clip(JShape.sm).background(jc.bg)
                        .border(1.dp, jc.line, JShape.sm).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(g.message, style = JType.small, color = jc.muted)
                    if (g.replacements.isNotEmpty()) Row(Modifier.offset(x = (-8).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        g.replacements.take(3).forEach { fix ->
                            TextAction(fix.ifBlank { "Remove" }, {
                                grammarFix = null
                                val t = vm.value.text
                                if (t.regionMatches(g.start, g.original, 0, g.original.length)) {
                                    vm.onValueChange(TextFieldValue(t.replaceRange(g.start, g.end, fix), TextRange(g.start + fix.length)))
                                }
                            }, bold = true)
                        }
                    }
                }
            }
        }
        // Math on the line the cursor ends: a sum ending in "=" gets its answer, an equation its solution, and
        // "y = x^2" a graph. The chip sits just after the cursor; a tap types the answer or opens the graph.
        val sel = vm.value.selection
        val mathOn = fixing == null && grammarFix == null && vm.focused && !vm.readOnly && sel.collapsed
        val math = remember(vm.value.text, sel.start, mathOn) { if (mathOn) mathSuggestionAt(vm.value.text, sel.start) else null }
        if (math != null && l != null && sel.start <= l.layoutInput.text.length) {
            val at = l.getCursorRect(sel.start)
            val (label, spoken) = when (math) {
                is MathSuggestion.Answer -> math.text to "Answer ${math.text}. Tap to add it."
                is MathSuggestion.Solution -> math.text to "${math.text}. Tap to add it."
                is MathSuggestion.Solved -> math.text to "${math.text}. Tap to add it."
                is MathSuggestion.Graph -> "Show graph" to "Show graph"
            }
            Popup(offset = IntOffset((at.right + with(density) { 6.dp.toPx() }).toInt(), (at.center.y - with(density) { 22.dp.toPx() }).toInt())) {
                Box(Modifier.clip(JShape.sm).background(jc.accent.copy(alpha = 0.12f)).semantics { contentDescription = spoken }) {
                    TextAction(label, {
                        val t = vm.value.text
                        val c = vm.value.selection.start
                        val insert = when (math) {
                            is MathSuggestion.Answer -> if (t[c - 1] == ' ') math.text else " ${math.text}"
                            is MathSuggestion.Solution -> (if (t[c - 1] == ' ') "" else " ") + "→ ${math.text}"
                            is MathSuggestion.Solved -> (if (t[c - 1] == ' ') "" else " ") + "→ ${math.text}"
                            is MathSuggestion.Graph -> { tableGraph = null; graphing = plotsIn(t); null }
                        }
                        if (insert != null && mathSuggestionAt(t, c) == math) {
                            vm.onValueChange(TextFieldValue(t.replaceRange(c, c, insert), TextRange(c + insert.length)))
                        }
                    }, bold = true)
                }
            }
        }
        graphing?.let { plots ->
            val context = LocalContext.current
            val tg = tableGraph
            GraphDialog(
                plots, onDismiss = { graphing = null },
                onAdd = if (vm.canAddImage) { image ->
                    graphing = null
                    vm.addGraph(image, plots.joinToString(", ") { it.label }) { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                } else null,
                centre = if (tg != null) Offset(tg.centreX.toFloat(), tg.centreY.toFloat()) else Offset.Zero,
                unitsAcross = if (tg != null) tg::unitsAcross else { { 20f } },
            )
        }
        layout?.let { l ->
            var number = 0
            forEachLineBounds(doc, l) { i, top, bottom, firstBottom ->
                val meta = doc.lines[i]
                number = if (meta.type == BlockType.NUMBERED) number + 1 else 0
                if (aiRunStart(doc, i) && !vm.readOnly) {
                    // Hide / Remove on the card's badge line, at the right.
                    Row(
                        Modifier.offset { IntOffset(0, (top + firstBottom).toInt() / 2 - 18.dp.roundToPx()) }.fillMaxWidth().height(36.dp),
                        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconAction(if (meta.folded) Ico.EYE else Ico.EYE_OFF, if (meta.folded) "Show AI text" else "Hide AI text", { vm.toggleAiFold(i) }, color = jc.muted, size = 18.dp)
                        IconAction(Ico.TRASH, "Remove AI text", { vm.removeAiCard(i) }, color = jc.muted, size = 18.dp)
                    }
                }
                meta.table?.let { t ->
                    TableCard(
                        t,
                        onOpen = { if (!vm.readOnly) vm.editingTable = meta.id },
                        onRemove = if (vm.readOnly || !vm.focused) null else ({ vm.removeAttachment(i) }),
                        modifier = Modifier.offset { IntOffset(0, top.toInt()) }.fillMaxWidth()
                            .height(with(density) { (bottom - top).toDp() }).padding(vertical = TABLE_GAP),
                        onGraph = remember(t) { tableGraph(t) }?.let { g -> { tableGraph = g; graphing = g.plots } },
                    )
                    return@forEachLineBounds
                }
                meta.attachment?.let { a ->
                    if (a.isPdf) {
                        PdfCard(
                            a, vm.files.file(a), onOpen = { viewingPdf = meta.id },
                            onRemove = if (vm.readOnly) null else ({ vm.removeAttachment(i) }),
                            modifier = Modifier.offset { IntOffset(0, top.toInt()) }.fillMaxWidth()
                                .height(with(density) { (bottom - top).toDp() }).padding(vertical = 6.dp),
                        )
                        return@forEachLineBounds
                    }
                    val d = if (a.isDrawing) drawings[a.file] else null
                    if (d != null) {
                        InlineDrawingLine(
                            d, a,
                            onOpen = { if (!vm.readOnly) onOpenDrawing(i, d) },
                            // The remove button shows only while editing, so a finished note reads like plain ink.
                            onRemove = if (vm.readOnly || !vm.focused) null else ({ vm.removeAttachment(i) }),
                            modifier = Modifier.offset { IntOffset(0, top.toInt()) }.fillMaxWidth()
                                .height(with(density) { (bottom - top).toDp() }).padding(vertical = DRAWING_GAP),
                        )
                        return@forEachLineBounds
                    }
                    AttachmentCard(
                        a, vm.files.file(a),
                        onOpen = { d ->
                            if (d != null) { if (!vm.readOnly) onOpenDrawing(i, d); return@AttachmentCard }
                            if (a.isImage) { onViewPhoto(meta.id); return@AttachmentCard }
                            try { context.startActivity(vm.files.viewIntent(a)) } catch (_: android.content.ActivityNotFoundException) {
                                Toast.makeText(context, "No app on this phone can open this file.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRemove = if (vm.readOnly) null else ({ vm.removeAttachment(i) }),
                        modifier = Modifier.offset { IntOffset(0, top.toInt()) }.fillMaxWidth()
                            .height(with(density) { (bottom - top).toDp() }).padding(vertical = 6.dp),
                    )
                    return@forEachLineBounds
                }
                linkPreviews[i]?.let { (note, rows) ->
                    val card = linkCardHeight(rows.size)
                    LinkPreviewCard(
                        rows,
                        onOpen = { onOpenLink(note.title) },
                        modifier = Modifier.offset { IntOffset(0, (bottom - card.toPx()).toInt()) }.fillMaxWidth().height(card)
                            .padding(start = with(density) { leadOf(meta).sp.toDp() }, top = LINK_CARD_GAP, bottom = LINK_CARD_GAP),
                    )
                    return@forEachLineBounds
                }
                if (meta.type !in MARKER_TYPES) return@forEachLineBounds
                val style = styles.getValue(meta.type)
                // Markers line up with the first row of a wrapped line.
                val firstRow = firstBottom - top
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .offset { IntOffset(leadOf(meta).sp.roundToPx(), top.toInt()) }
                        .size(with(density) { GUTTER.toDp() }, with(density) { minOf(firstRow, bottom - top).toDp() }),
                ) {
                    when (meta.type) {
                        BlockType.BULLET -> Text("•", style = style)
                        BlockType.DASHED -> Text("–", style = style)
                        BlockType.NUMBERED -> Text("$number.", style = style)
                        // Sized to the line: at a small text size a fixed 22dp circle is taller than the line and they overlap.
                        else -> CheckCircle(
                            meta.checked,
                            size = with(density) { minOf(firstRow * 0.8f, GUTTER.toPx() * 0.75f).toDp() }.coerceAtMost(22.dp),
                            modifier = Modifier.clip(CircleShape).clickable(enabled = !vm.trashed, role = Role.Checkbox, onClick = tapped { vm.toggleChecked(i) })
                                .semantics { contentDescription = if (meta.checked) "Checked" else "Not checked" },
                        )
                    }
                }
            }
        }
    }
}

/** Grammar underlines: blue, as in most writing apps, so they read differently from red spelling ones. */
internal val GRAMMAR_BLUE = Color(0xFF3B82F6)

/** A note link's preview card: up to this many lines of the note, each one row. */
internal const val LINK_CARD_ROWS = 3
internal val LINK_CARD_GAP = 6.dp
internal val LINK_CARD_ROW = 20.dp
internal val LINK_CARD_PADDING = 10.dp

/** How much taller a link's line gets to fit a card of [rows] rows, including the space around it. */
internal fun linkCardHeight(rows: Int) = LINK_CARD_ROW * rows + LINK_CARD_PADDING * 2 + LINK_CARD_GAP * 2

/**
 * Lines that are nothing but a link to an existing note (not the title, not a list item), with the note and the
 * lines its card shows: its first lines of text, or why there are none.
 */
internal fun linkPreviewLines(doc: EditorDoc, notes: List<NoteEntity>): Map<Int, Pair<NoteEntity, List<String>>> {
    val out = HashMap<Int, Pair<NoteEntity, List<String>>>()
    doc.texts.forEachIndexed { i, text ->
        val meta = doc.lines[i]
        if (i == 0 || meta.isObject || meta.type in MARKER_TYPES) return@forEachIndexed
        val link = findLinks(text).singleOrNull()?.takeIf { (range, _) -> text.substring(0, range.first).isBlank() && text.substring(range.last + 1).isBlank() }
            ?: return@forEachIndexed
        val note = resolveLink(link.second, notes) ?: return@forEachIndexed
        val rows = when {
            note.isLocked -> listOf("Locked note")
            else -> note.blocks.drop(1).filter { it.attachment == null && it.plainText.isNotBlank() }.flatMap { it.plainText.lines() }.take(LINK_CARD_ROWS).map { it.trim() }
                .ifEmpty { listOf(if (note.blocks.any { it.attachment != null }) "Attachments only" else "No text yet") }
        }
        out[i] = note to rows
    }
    return out
}

/** The first lines of a linked note, under the link. Tap opens the note. */
@Composable
internal fun LinkPreviewCard(rows: List<String>, onOpen: () -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(JShape.sm).background(jc.surface).clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = LINK_CARD_PADDING),
    ) {
        rows.forEach { row ->
            Text(
                row, Modifier.height(LINK_CARD_ROW), style = JType.secondary.copy(lineHeight = 20.sp), color = jc.muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The tallest a photo shows in a note; a taller one is narrower than the page, keeping its shape. */
internal val MAX_PHOTO = 520.dp

/**
 * Every photo's upright width and height in [doc], by file name, read from the file's header (not decoded).
 * One not downloaded yet (or unreadable) is missing, and its line keeps the default height.
 */
@Composable
internal fun rememberPhotoSizes(doc: EditorDoc, files: com.margin.app.data.AttachmentFiles): Map<String, Pair<Float, Float>> {
    val photos = doc.lines.mapNotNull { it.attachment?.takeIf { a -> a.isImage } }
    val loaded = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    LaunchedEffect(photos.map { it.file }) {
        photos.filter { it.file !in loaded }.forEach { a ->
            withContext(Dispatchers.IO) { runCatching { photoSize(files.file(a)) }.getOrNull() }?.let { loaded[a.file] = it }
        }
    }
    return loaded
}

/** Width and height as the photo is shown: a camera photo turned by its EXIF orientation has them swapped. */
internal fun photoSize(file: java.io.File): Pair<Float, Float>? {
    if (!file.exists()) return null
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.path, o)
    if (o.outWidth <= 0 || o.outHeight <= 0) return null
    val turned = runCatching {
        android.media.ExifInterface(file.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) in 5..8
    }.getOrDefault(false)
    return if (turned) o.outHeight.toFloat() to o.outWidth.toFloat() else o.outWidth.toFloat() to o.outHeight.toFloat()
}

/** Space above and below a drawing's ink, and the limits on how tall its line can be. */
internal val DRAWING_GAP = 4.dp
internal val MIN_DRAWING = 40.dp
internal val MAX_DRAWING = 1600.dp

/** Every drawing in [doc] read from its file, by file name; one not downloaded yet (or unreadable) is missing. */
@Composable
internal fun rememberDrawings(doc: EditorDoc, files: com.margin.app.data.AttachmentFiles): Map<String, Drawing> {
    val names = doc.lines.mapNotNull { it.attachment?.takeIf { a -> a.isDrawing } }
    val loaded = remember { mutableStateMapOf<String, Drawing>() }
    LaunchedEffect(names.map { it.file }) {
        names.filter { it.file !in loaded }.forEach { a ->
            withContext(Dispatchers.IO) { runCatching { MarginJson.decodeFromString<Drawing>(files.file(a).readText()) }.getOrNull() }
                ?.let { loaded[a.file] = it }
        }
    }
    return loaded
}

/** A drawing as part of the text: ink on the page, no card. Tap to edit it. */
@Composable
internal fun InlineDrawingLine(d: Drawing, a: Attachment, onOpen: () -> Unit, onRemove: (() -> Unit)?, modifier: Modifier) {
    Box(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "Drawing. Tap to edit it." },
    ) {
        InlineDrawing(d, Modifier.fillMaxSize())
        if (onRemove != null) {
            Box(
                Modifier.align(Alignment.TopEnd).size(28.dp).clip(CircleShape).background(jc.surface)
                    .clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = "Remove ${a.name}" },
                contentAlignment = Alignment.Center,
            ) { JIcon(Ico.CLOSE, jc.muted, size = 12.dp, stroke = 2.6f) }
        }
    }
}

internal val IMAGE_CARD = 162.dp // design: a 150dp card plus 6dp space above and below
internal val FILE_CARD = 76.dp
internal val PDF_CARD = 112.dp

/** A photo (whole, at its own aspect ratio) or a file (icon and name). Tap opens it in another app. */
@Composable
internal fun AttachmentCard(a: Attachment, file: java.io.File, onOpen: (Drawing?) -> Unit, onRemove: (() -> Unit)?, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, file) {
        value = if (a.isImage) withContext(Dispatchers.IO) { runCatching { decodePreview(file) }.getOrNull() } else null
    }
    val drawing by produceState<Drawing?>(null, file) {
        value = if (a.isDrawing) withContext(Dispatchers.IO) { runCatching { MarginJson.decodeFromString<Drawing>(file.readText()) }.getOrNull() } else null
    }
    if (a.isLink) {
        val context = LocalContext.current
        val url by produceState<String?>(null, file) { value = withContext(Dispatchers.IO) { runCatching { file.readText().trim() }.getOrNull() } }
        // The page's picture and description, fetched once and kept in the cache; the card shows without them until then.
        val preview by produceState<LoadedPreview?>(null, url) {
            val u = url ?: return@produceState
            value = withContext(Dispatchers.IO) { runCatching { LinkPreviews(context.cacheDir).load(u) }.getOrNull() }
        }
        val thumb = remember(preview) { preview?.image?.asImageBitmap() }
        Box(
            modifier.clip(JShape.sm).background(jc.surface).clickable(role = Role.Button) {
                val u = url ?: return@clickable
                try { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u))) } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "No app on this phone can open web links.", Toast.LENGTH_SHORT).show()
                }
            }.semantics { contentDescription = a.name + ", web link" },
        ) {
            Row(Modifier.fillMaxSize().padding(end = 14.dp, start = if (thumb == null) 14.dp else 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (thumb != null) Image(thumb, null, Modifier.fillMaxHeight().aspectRatio(1f), contentScale = ContentScale.Crop)
                else JIcon(Ico.GLOBE, jc.accent, size = 22.dp)
                Column(Modifier.weight(1f).padding(end = 28.dp)) {
                    Text(a.name, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    preview?.description?.let { Text(it, style = JType.small, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(
                        url?.let(::hostOf) ?: if (file.exists()) "" else "Not downloaded yet. It arrives with the next sync.",
                        style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onRemove != null) RemoveButton(a, onRemove, Modifier.align(Alignment.TopEnd))
        }
        return
    }
    if (a.isAudio) {
        Box(modifier.clip(JShape.sm).background(jc.surface)) {
            AudioCard(a, file, Modifier.fillMaxSize())
            if (onRemove != null) RemoveButton(a, onRemove, Modifier.align(Alignment.TopEnd))
        }
        return
    }
    // A photo keeps its own shape: as tall as the card, as wide as its aspect ratio allows, in a frame like a print.
    image?.takeIf { drawing == null && it.width > 0 && it.height > 0 }?.let { img ->
        Box(modifier) {
            val shape = JShape.sm
            Box(
                Modifier.padding(2.dp).shadow(JElevation.card, shape).clip(shape).background(jc.card).border(1.dp, jc.line, shape)
                    .clickable(role = Role.Button) { onOpen(null) }.semantics { contentDescription = a.name }.padding(6.dp),
            ) {
                Image(
                    img, null, Modifier.aspectRatio(img.width.toFloat() / img.height, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Fit,
                )
                if (onRemove != null) RemoveButton(a, onRemove, Modifier.align(Alignment.TopEnd))
            }
        }
        return
    }
    Box(modifier.clip(JShape.sm).background(jc.surface).clickable(role = Role.Button) { onOpen(drawing) }.semantics { contentDescription = a.name }) {
        drawing?.let { InlineDrawing(it, Modifier.fillMaxSize()) } ?: Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            JIcon(Ico.ATTACH, jc.accent, size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(a.name, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (file.exists()) a.mime.substringAfter('/') else "Not downloaded yet. It arrives with the next sync.",
                    style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onRemove != null) RemoveButton(a, onRemove, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
internal fun RemoveButton(a: Attachment, onRemove: () -> Unit, modifier: Modifier) {
    Box(
        modifier.padding(6.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
            .clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = "Remove ${a.name}" },
        contentAlignment = Alignment.Center,
    ) { JIcon(Ico.CLOSE, Color.White, size = 12.dp, stroke = 2.6f) }
}

/** A screen-sized preview of a photo. */
internal fun decodePreview(file: java.io.File, maxSide: Int = 1440): ImageBitmap? = decodeImage(file, maxSide)?.asImageBitmap()

internal val MARKER_TYPES = setOf(BlockType.BULLET, BlockType.DASHED, BlockType.NUMBERED, BlockType.CHECK)

/** Calls [block] with each note line's vertical bounds (all of its wrapped rows) and its first row's bottom in [layout]. */
internal inline fun forEachLineBounds(doc: EditorDoc, layout: TextLayoutResult, block: (Int, Float, Float, Float) -> Unit) {
    if (layout.layoutInput.text.length != doc.text.length) return // layout is from an older frame
    var start = 0
    doc.texts.forEachIndexed { i, line ->
        val end = start + line.length
        val first = layout.getLineForOffset(start)
        val top = layout.getLineTop(first)
        val bottom = layout.getLineBottom(layout.getLineForOffset(end))
        block(i, top, bottom, layout.getLineBottom(first))
        start = end + 1
    }
}
