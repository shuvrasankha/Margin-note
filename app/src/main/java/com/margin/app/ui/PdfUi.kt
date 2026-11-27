package com.margin.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.margin.app.data.Attachment
import com.margin.app.data.Drawing
import com.margin.app.data.PDF_MIME
import com.margin.app.data.PageEdit
import com.margin.app.data.Pdf
import com.margin.app.data.PdfInfo
import com.margin.app.data.parsePages
import com.margin.app.data.PageText
import com.margin.app.data.PdfMatch
import com.margin.app.data.findInPages
import com.margin.app.data.matchBoxes
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

const val ICON_PDF = "M6 3h8l4 4v14H6zM14 3v4h4M8.5 17v-4h1.3a1.2 1.2 0 0 1 0 2.4H8.5M12.5 13v4h.8a2 2 0 0 0 0-4zM16.5 17v-4h-1.5M15 15h1.3"
private const val ICON_SPLIT = "M4 4h7v16H4zM13 4h7v16h-7"
private const val ICON_MERGE = "M4 5h6v14H4zM14 5h6v14h-6M10 12h4M12 10l2 2-2 2"
private const val ICON_PAGES = "M8 3h11v15H8zM5 6v15h11"
private const val ICON_EDIT_TEXT = "M4 6h10M9 6v12M14 19l1-3 6-6 2 2-6 6z"
private const val ICON_OPEN = "M14 4h6v6M20 4l-9 9M18 14v6H4V6h6"

/** The card a PDF gets in the note: its first page, its name, and its pages (or that it's locked). */
@Composable
fun PdfCard(a: Attachment, file: File, onOpen: () -> Unit, onRemove: (() -> Unit)?, modifier: Modifier) {
    val context = LocalContext.current
    val info by produceState<PdfInfo?>(null, file) { value = withContext(Dispatchers.IO) { if (file.exists()) Pdf.info(context, file) else null } }
    val thumb by produceState<ImageBitmap?>(null, file, info) {
        if (info?.locked == false) value = Pdf.render(file, 0, 220)?.asImageBitmap()
    }
    Box(
        modifier.clip(JShape.sm).background(jc.surface).clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = context.getString(R.string.pdf_name_desc, a.name) },
    ) {
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.fillMaxHeight().aspectRatio(0.75f).clip(RoundedCornerShape(6.dp)).background(Color.White).border(1.dp, jc.line, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter) }
                    ?: JIcon(if (info?.locked == true) Ico.LOCK else ICON_PDF, Color(0xFFB0B0B5), size = 22.dp)
            }
            Column(Modifier.weight(1f).padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(a.name, style = JType.rowTitle, color = jc.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        !file.exists() -> stringResource(R.string.pdf_not_downloaded_yet_it_arrives)
                        info == null -> stringResource(R.string.pdf_pdf)
                        info!!.locked -> stringResource(R.string.pdf_password_protected_tap_to_unlock)
                        else -> pluralStringResource(R.plurals.pdf_info_pages, info!!.pages, info!!.pages)
                    },
                    style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onRemove != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                    .clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = context.getString(R.string.pdf_remove, a.name) },
                contentAlignment = Alignment.Center,
            ) { JIcon(Ico.CLOSE, Color.White, size = 12.dp, stroke = 2.6f) }
        }
    }
}

/** Makes a full-screen dialog's window really fill the screen (some phones keep it narrower). */
@Composable
internal fun FullWindow() {
    val view = LocalView.current
    // Dark status bar icons on a light page, light ones on a dark page.
    val light = jc.bg.luminance() > 0.5f
    LaunchedEffect(view, light) {
        (view.parent as? DialogWindowProvider)?.window?.apply {
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            androidx.core.view.WindowCompat.getInsetsController(this, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
}

/**
 * The PDF on line [lineId], full screen: pages to scroll and pinch-zoom, and the tools in the ⋯ menu. Every tool
 * is a normal edit of the note (Undo reverses it); ones that change the PDF swap in a new copy, so the viewer
 * follows the line, not the file.
 */
@Composable
fun PdfViewer(vm: EditorViewModel, lineId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val a = vm.doc.lines.firstOrNull { it.id == lineId }?.attachment
    if (a == null) { LaunchedEffect(Unit) { onClose() }; return }
    val file = vm.fileOf(a)
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val info by produceState<PdfInfo?>(null, file) { value = withContext(Dispatchers.IO) { Pdf.info(context, file) } }
    val sizes by produceState(emptyList<Pair<Int, Int>>(), file, info) { if (info?.locked == false) value = Pdf.pageSizes(file) }
    val list = rememberLazyListState()
    val page by remember { derivedStateOf { list.firstVisibleItemIndex } }
    var zoom by remember(file) { mutableStateOf(1f) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var splitting by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var drawingOn by remember { mutableStateOf<Pair<Int, ImageBitmap>?>(null) }
    var editingText by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val canEdit = !vm.readOnly

    // Where you were: each PDF opens again at the page you left it on (it follows the line, so edits keep it).
    val prefs = remember { context.getSharedPreferences("margin_pdf", android.content.Context.MODE_PRIVATE) }
    var restored by remember(file) { mutableStateOf(false) }
    LaunchedEffect(sizes) {
        if (sizes.isEmpty() || restored) return@LaunchedEffect
        val saved = prefs.getInt("page:$lineId", 0).coerceIn(0, sizes.lastIndex)
        if (saved > 0) list.scrollToItem(saved)
        restored = true
    }
    LaunchedEffect(page, restored) { if (restored) prefs.edit().putInt("page:$lineId", page).apply() }
    var jumping by remember { mutableStateOf(false) }

    // Find: the PDF's text is read once, the first time Find opens.
    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var texts by remember(file) { mutableStateOf<List<PageText>?>(null) }
    LaunchedEffect(file, finding) {
        if (finding && texts == null) texts = withContext(Dispatchers.IO) { runCatching { Pdf.pageTexts(context, file) }.getOrDefault(emptyList()) }
    }
    var matches by remember(file) { mutableStateOf(emptyList<PdfMatch>()) }
    var current by remember(file) { mutableStateOf(0) }
    LaunchedEffect(query, texts, finding) {
        val t = texts
        matches = if (!finding || t == null) emptyList() else withContext(Dispatchers.Default) { findInPages(t.map { it.text }, query) }
        current = 0
    }
    // Highlights per page: each match's boxes, and whether it's the one being looked at.
    val marks = remember(matches, current, texts) {
        val t = texts ?: return@remember emptyMap<Int, List<Pair<Boolean, FloatArray>>>()
        matches.withIndex().groupBy({ it.value.page }) { (i, m) -> matchBoxes(t[m.page], m).map { (i == current) to it } }
            .mapValues { (_, v) -> v.flatten() }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FullWindow()
        Column(Modifier.fillMaxSize().background(jc.page).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).background(jc.bg).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Ico.BACK, stringResource(R.string.common_close), onClose)
                Column(
                    Modifier.weight(1f).clip(JShape.xs)
                        .clickable(enabled = sizes.size > 1, role = Role.Button, onClickLabel = stringResource(R.string.pdf_go_to_page)) { jumping = true },
                ) {
                    Text(a.name, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (sizes.isNotEmpty()) Text(stringResource(if (sizes.size > 1) R.string.pdf_page_of_jump else R.string.pdf_page_of, (page + 1).toString(), sizes.size.toString()), style = JType.small, color = jc.muted)
                }
                if (info?.locked == false && sizes.isNotEmpty()) IconAction(Ico.SEARCH, stringResource(R.string.pdf_find_in_pdf), { finding = !finding }, color = if (finding) jc.accent else jc.ink)
                if (info?.locked == false) Box {
                    IconAction(Ico.MORE, stringResource(R.string.pdf_pdf_tools), { menu = true }, fill = true)
                    PdfMenu(
                        open = menu, onDismiss = { menu = false }, canEdit = canEdit,
                        items = buildList {
                            if (canEdit) {
                                add(Triple(stringResource(R.string.pdf_edit_text), ICON_EDIT_TEXT) { editingText = page })
                                add(Triple(stringResource(R.string.pdf_draw_on_page, page + 1), Ico.PEN) {
                                    scope.launch {
                                        val (w, h) = sizes.getOrNull(page) ?: return@launch
                                        val px = 1400
                                        val bmp = Pdf.render(file, page, px) ?: return@launch
                                        drawingOn = page to bmp.asImageBitmap()
                                        if (w <= 0 || h <= 0) drawingOn = null
                                    }
                                })
                                add(Triple(stringResource(R.string.pdf_edit_pages), ICON_PAGES) { editing = true })
                                add(Triple(stringResource(R.string.pdf_split), ICON_SPLIT) { splitting = true })
                                add(Triple(stringResource(R.string.pdf_merge_with), ICON_MERGE) { merging = true })
                                add(Triple(stringResource(R.string.pdf_add_text_to_note), Ico.TEXT) {
                                    vm.pdfTool(context, lineId, context.getString(R.string.pdf_reading_text), toast, onDone = onClose) { ctx, _, f ->
                                        val text = Pdf.text(ctx, f)
                                        if (text.isBlank()) throw java.io.IOException(context.getString(R.string.pdf_no_text_found_it_may))
                                        EditorViewModel.PdfOutcome(text = text)
                                    }
                                })
                            }
                            add(Triple(stringResource(R.string.pdf_share_pdf), Ico.SHARE) {
                                runCatching { context.startActivity(Intent.createChooser(vm.files.sendIntent(file, PDF_MIME, a.name), null)) }
                            })
                            add(Triple(stringResource(R.string.pdf_open_in_another_app), ICON_OPEN) {
                                try { context.startActivity(vm.files.viewIntent(a)) } catch (_: android.content.ActivityNotFoundException) { toast(context.getString(R.string.pdf_no_other_app_on_this)) }
                            })
                        },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
            if (finding) {
                Row(
                    Modifier.fillMaxWidth().background(jc.bg).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SearchField(query, { query = it }, stringResource(R.string.pdf_find_in_pdf), Modifier.weight(1f), autoFocus = true)
                    val n = matches.size
                    Text(
                        when {
                            texts == null -> "…"
                            query.isBlank() -> ""
                            n == 0 -> stringResource(R.string.pdf_none)
                            else -> stringResource(if (n >= 2000) R.string.pdf_match_of_more else R.string.pdf_match_of, (current + 1).toString(), n.toString())
                        },
                        Modifier.widthIn(min = 44.dp).padding(horizontal = 4.dp), style = JType.small, color = jc.muted, textAlign = TextAlign.Center,
                    )
                    IconAction(Ico.ARROW_UP, stringResource(R.string.pdf_previous_match), { current = (current - 1 + n) % n }, enabled = n > 1)
                    IconAction(Ico.ARROW_DOWN, stringResource(R.string.pdf_next_match), { current = (current + 1) % n }, enabled = n > 1)
                    IconAction(Ico.CLOSE, stringResource(R.string.pdf_close_find), { finding = false; query = "" }, color = jc.muted)
                }
                if (texts?.all { it.text.isBlank() } == true) Text(
                    stringResource(R.string.pdf_this_pdf_has_no_text),
                    Modifier.fillMaxWidth().background(jc.bg).padding(horizontal = 16.dp, vertical = 6.dp), style = JType.small, color = jc.muted,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
            }
            vm.pdfBusy?.let { Text(it, Modifier.fillMaxWidth().background(jc.accent.copy(alpha = 0.12f)).padding(10.dp), style = JType.small, color = jc.ink, textAlign = TextAlign.Center) }
            when {
                info == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (file.exists()) stringResource(R.string.pdf_opening) else stringResource(R.string.pdf_this_pdf_isnt_downloaded_yet), style = JType.secondary, color = jc.muted)
                }
                info!!.locked -> LockedPdf(canEdit, Modifier.weight(1f)) { password ->
                    vm.pdfTool(context, lineId, context.getString(R.string.pdf_unlocking), toast) { ctx, _, f -> EditorViewModel.PdfOutcome(replace = Pdf.unlock(ctx, f, password)) }
                }
                else -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val pageWidth = (maxWidth - 24.dp) * zoom
                    val px = with(LocalDensity.current) { pageWidth.toPx() }.roundToInt()
                    val headroom = with(LocalDensity.current) { 96.dp.toPx() }
                    // Brings the match being looked at into view, a little below the top.
                    LaunchedEffect(matches, current) {
                        val m = matches.getOrNull(current) ?: return@LaunchedEffect
                        val top = texts?.getOrNull(m.page)?.let { matchBoxes(it, m).firstOrNull()?.get(1) } ?: 0f
                        val scale = px / (sizes.getOrNull(m.page)?.first ?: 1).coerceAtLeast(1).toFloat()
                        list.animateScrollToItem(m.page, (top * scale - headroom).roundToInt().coerceAtLeast(0))
                    }
                    Box(
                        Modifier.fillMaxSize().horizontalScroll(rememberScrollState())
                            // Two fingers pinch to zoom (1x to 4x); one finger scrolls as usual.
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    while (true) {
                                        val e = awaitPointerEvent(PointerEventPass.Initial)
                                        if (e.changes.none { it.pressed }) break
                                        if (e.changes.count { it.pressed } >= 2 && e.calculateCentroid().isSpecified) {
                                            zoom = (zoom * e.calculateZoom()).coerceIn(1f, 4f)
                                            e.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            },
                    ) {
                        LazyColumn(
                            Modifier.width(pageWidth + 24.dp).fillMaxHeight(), state = list,
                            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(sizes.size) { i ->
                                val (w, h) = sizes[i]
                                // Rendered at most 2x the screen width: sharper when zoomed, without running out of memory.
                                val bmp by produceState<ImageBitmap?>(null, file, i, (px.coerceAtMost(2400) / 200)) {
                                    value = Pdf.render(file, i, px.coerceAtMost(2400))?.asImageBitmap()
                                }
                                Box(
                                    Modifier.width(pageWidth).aspectRatio(w.toFloat() / h.coerceAtLeast(1)).background(Color.White)
                                        .border(1.dp, jc.line).pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = if (zoom > 1.05f) 1f else 2.5f }) }
                                        .semantics { contentDescription = context.getString(R.string.pdf_page, i + 1) }
                                        .drawWithContent {
                                            drawContent()
                                            // Found words: all of them in yellow, the one being looked at in orange.
                                            val k = size.width / w.coerceAtLeast(1)
                                            marks[i]?.forEach { (on, b) ->
                                                val pad = 1.5f * k
                                                drawRect(
                                                    if (on) Color(0xFFFF9500).copy(alpha = 0.5f) else Color(0xFFFFE14D).copy(alpha = 0.5f),
                                                    androidx.compose.ui.geometry.Offset(b[0] * k - pad, b[1] * k - pad),
                                                    androidx.compose.ui.geometry.Size((b[2] - b[0]) * k + 2 * pad, (b[3] - b[1]) * k + 2 * pad),
                                                    blendMode = androidx.compose.ui.graphics.BlendMode.Multiply,
                                                )
                                            }
                                        },
                                ) { bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) } }
                            }
                        }
                    }
                    if (zoom > 1.01f) {
                        Text(
                            "${(zoom * 100).roundToInt()}%", Modifier.align(Alignment.BottomStart).padding(12.dp).clip(CircleShape).background(jc.surface)
                                .clickable(role = Role.Button) { zoom = 1f }.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = JType.small, color = jc.ink,
                        )
                    }
                }
            }
        }
    }

    if (jumping) PageJump(file, sizes, page, onDismiss = { jumping = false }) { target ->
        jumping = false
        scope.launch { list.scrollToItem(target) }
    }
    if (editing) PageEditor(file, sizes, onCancel = { editing = false }, onExtract = { pages ->
        editing = false
        val label = pages.joinToString(", ") { (it + 1).toString() }
        vm.pdfTool(context, lineId, context.getString(R.string.pdf_extracting_pages), toast) { ctx, att, f ->
            EditorViewModel.PdfOutcome(add = listOf("${att.name.removeSuffix(".pdf")} (page${if (pages.size > 1) "s" else ""} $label).pdf" to Pdf.extract(ctx, f, pages)))
        }
    }) { edits ->
        editing = false
        vm.pdfTool(context, lineId, context.getString(R.string.pdf_saving_pages), toast) { ctx, _, f -> EditorViewModel.PdfOutcome(replace = Pdf.edit(ctx, f, edits)) }
    }
    if (splitting) SplitDialog(sizes.size, onDismiss = { splitting = false }) { groups ->
        splitting = false
        vm.pdfTool(context, lineId, context.getString(R.string.pdf_splitting), toast) { ctx, att, f ->
            val base = att.name.removeSuffix(".pdf").removeSuffix(".PDF")
            EditorViewModel.PdfOutcome(add = if (groups == null) Pdf.splitEach(ctx, f).mapIndexed { i, b -> "$base (page ${i + 1}).pdf" to b }
            else groups.map { (label, pages) -> "$base (page${if (pages.size > 1) "s" else ""} $label).pdf" to Pdf.extract(ctx, f, pages) })
        }
    }
    if (merging) MergeDialog(vm, vm.otherPdfs(context, lineId), onDismiss = { merging = false }) { other ->
        merging = false
        vm.pdfTool(context, lineId, context.getString(R.string.pdf_merging), toast) { ctx, att, f ->
            val o = other()
            EditorViewModel.PdfOutcome(add = listOf("${att.name.removeSuffix(".pdf")} + ${o.first.name}".let { if (it.endsWith(".pdf", true)) it else "$it.pdf" } to Pdf.merge(ctx, listOf(f, o.second))))
        }
    }
    editingText?.let { start ->
        PdfTextEditor(file, sizes, start, onCancel = { editingText = null }) { edits ->
            editingText = null
            vm.pdfTool(context, lineId, context.getString(R.string.pdf_saving_your_text), toast) { ctx, _, f -> EditorViewModel.PdfOutcome(replace = Pdf.editText(ctx, f, edits)) }
        }
    }
    drawingOn?.let { (index, bg) ->
        DrawingScreen(
            Drawing(bg.width.toFloat(), bg.height.toFloat()), a.name,
            onDone = { d ->
                drawingOn = null
                if (d == null || d.strokes.none { !it.erase }) return@DrawingScreen
                vm.pdfTool(context, lineId, context.getString(R.string.pdf_adding_your_drawing), toast) { ctx, _, f ->
                    EditorViewModel.PdfOutcome(replace = Pdf.stamp(ctx, f, index, renderInk(d, bg.width, bg.height)))
                }
            },
            background = bg, title = stringResource(R.string.pdf_page, index + 1),
        )
    }
}

@Composable
private fun PdfMenu(open: Boolean, onDismiss: () -> Unit, canEdit: Boolean, items: List<Triple<String, String, () -> Unit>>) {
    JMenu(expanded = open, onDismissRequest = onDismiss, width = 250.dp) {
        items.forEach { (label, icon, onClick) ->
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(label, style = JType.item, color = jc.ink) },
                leadingIcon = { JIcon(icon, jc.ink, size = 20.dp) },
                onClick = { onDismiss(); onClick() },
            )
        }
    }
}

/** A PDF that needs its password: ask for it, and save an unlocked copy in its place. */
@Composable
private fun LockedPdf(canEdit: Boolean, modifier: Modifier, onUnlock: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(jc.accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) { JIcon(Ico.LOCK, jc.accent, size = 30.dp) }
        Text(stringResource(R.string.pdf_password_protected), style = JType.rowTitle.copy(fontSize = 19.sp), color = jc.ink)
        Text(
            if (canEdit) stringResource(R.string.pdf_enter_its_password_to_unlock)
            else stringResource(R.string.pdf_unlock_this_note_to_unlock),
            style = JType.secondary, color = jc.muted, textAlign = TextAlign.Center,
        )
        if (canEdit) {
            OutlinedTextField(
                password, { password = it }, Modifier.widthIn(max = 360.dp).fillMaxWidth(), label = { Text(stringResource(R.string.pdf_pdf_password)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            PrimaryButton(stringResource(R.string.pdf_unlock), { onUnlock(password) }, Modifier.widthIn(max = 360.dp).fillMaxWidth(), enabled = password.isNotEmpty())
        }
    }
}

/**
 * Edit pages: tap pages to pick them, then turn, move or delete them, or copy them out as a new PDF. Save keeps
 * the new order and turns as the PDF (Undo in the note brings the old one back).
 */
@Composable
private fun PageEditor(file: File, sizes: List<Pair<Int, Int>>, onCancel: () -> Unit, onExtract: (List<Int>) -> Unit, onSave: (List<PageEdit>) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pages = remember { mutableStateListOf<PageEdit>().apply { addAll(sizes.indices.map { PageEdit(it) }) } }
    val picked = remember { mutableStateListOf<Int>() } // positions in [pages]
    val changed = pages.toList() != sizes.indices.map { PageEdit(it) }
    fun move(by: Int) {
        val order = picked.sorted().let { if (by > 0) it.reversed() else it }
        if (order.any { it + by !in pages.indices || (it + by) in picked && (it + by) !in order }) return
        if (order.any { it + by !in pages.indices }) return
        order.forEach { i -> val p = pages.removeAt(i); pages.add(i + by, p) }
        val moved = order.map { it + by }
        picked.clear(); picked.addAll(moved)
    }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FullWindow()
        Column(Modifier.fillMaxSize().background(jc.page).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).background(jc.bg).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextAction(stringResource(R.string.common_cancel), onCancel)
                Text(stringResource(R.string.pdf_edit_pages), Modifier.weight(1f), style = JType.rowTitle, color = jc.ink, textAlign = TextAlign.Center)
                TextAction(stringResource(R.string.common_save), { onSave(pages.toList()) }, bold = true, enabled = changed && pages.isNotEmpty())
            }
            Text(
                if (picked.isEmpty()) stringResource(R.string.pdf_tap_pages_to_pick) else pluralStringResource(R.plurals.pdf_pages_picked, picked.size, picked.size),
                Modifier.fillMaxWidth().background(jc.bg).padding(horizontal = 16.dp, vertical = 6.dp), style = JType.small, color = jc.muted,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
            LazyVerticalGrid(
                GridCells.Adaptive(104.dp), Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(pages, key = { pos, p -> "${p.index}:$pos" }) { pos, p ->
                    val on = pos in picked
                    val thumb by produceState<ImageBitmap?>(null, p.index) { value = Pdf.render(file, p.index, 260)?.asImageBitmap() }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(JShape.xs)
                                .border(if (on) 3.dp else 1.dp, if (on) jc.accent else jc.line, JShape.xs)
                                .background(if (on) jc.accent.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable(role = Role.Checkbox) { if (on) picked.remove(pos) else picked.add(pos) }
                                .semantics { contentDescription = context.getString(R.string.pdf_page, p.index + 1); selected = on }.padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            thumb?.let {
                                Image(it, null, Modifier.fillMaxSize().rotate(p.rotation.toFloat()).padding(if (p.rotation % 180 != 0) 10.dp else 0.dp), contentScale = ContentScale.Fit)
                            }
                            if (on) Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(jc.accent), contentAlignment = Alignment.Center) {
                                JIcon(Ico.CHECK, Color.White, size = 13.dp, stroke = 2.8f)
                            }
                        }
                        Text("${p.index + 1}", style = JType.small, color = if (on) jc.accent else jc.muted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
            val any = picked.isNotEmpty()
            Row(Modifier.fillMaxWidth().background(jc.bg).padding(4.dp)) {
                PageTool(Ico.ROTATE, stringResource(R.string.pdf_rotate), any) { picked.forEach { i -> pages[i] = pages[i].copy(rotation = (pages[i].rotation + 90) % 360) } }
                PageTool(Ico.BACK, stringResource(R.string.pdf_earlier), any && picked.min() > 0) { move(-1) }
                PageTool(Ico.CHEVRON, stringResource(R.string.pdf_later), any && picked.max() < pages.lastIndex) { move(1) }
                PageTool(Ico.COPY, stringResource(R.string.pdf_extract), any) { onExtract(picked.sorted().map { pages[it].index }) }
                PageTool(Ico.TRASH, stringResource(R.string.common_delete), any && picked.size < pages.size, danger = true) {
                    picked.sortedDescending().forEach { pages.removeAt(it) }; picked.clear()
                }
            }
        }
    }
}

/** Go to page: every page as a picture, the one you're on ringed; tap one to go there. */
@Composable
private fun PageJump(file: File, sizes: List<Pair<Int, Int>>, at: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState(initialFirstVisibleItemIndex = (at - 3).coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FullWindow()
        Column(Modifier.fillMaxSize().background(jc.page).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).background(jc.bg).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextAction(stringResource(R.string.common_cancel), onDismiss)
                Text(stringResource(R.string.pdf_go_to_page), Modifier.weight(1f), style = JType.rowTitle, color = jc.ink, textAlign = TextAlign.Center)
                Spacer(Modifier.width(64.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
            LazyVerticalGrid(
                GridCells.Adaptive(96.dp), Modifier.weight(1f).fillMaxWidth(), state = grid, contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(sizes.size) { i ->
                    val on = i == at
                    val (w, h) = sizes[i]
                    val thumb by produceState<ImageBitmap?>(null, file, i) { value = Pdf.render(file, i, 240)?.asImageBitmap() }
                    Column(
                        Modifier.clip(JShape.xs).clickable(role = Role.Button) { onPick(i) }.semantics { contentDescription = context.getString(R.string.pdf_page, i + 1); selected = on },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(w.toFloat() / h.coerceAtLeast(1)).background(Color.White)
                                .border(if (on) 3.dp else 1.dp, if (on) jc.accent else jc.line),
                        ) { thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) } }
                        Text("${i + 1}", style = JType.small, color = if (on) jc.accent else jc.muted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PageTool(icon: String, label: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val color = when { !enabled -> jc.muted.copy(alpha = 0.45f); danger -> jc.danger; else -> jc.accent }
    Column(
        Modifier.weight(1f).height(58.dp).clip(JShape.sm).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        JIcon(icon, color, size = 20.dp, stroke = 1.9f)
        Text(label, style = JType.small, color = color, maxLines = 1)
    }
}

/** Split: every page on its own, or ranges ("1-3, 4-6"), one new PDF each. [onSplit] gets null for every page. */
@Composable
private fun SplitDialog(count: Int, onDismiss: () -> Unit, onSplit: (List<Pair<String, List<Int>>>?) -> Unit) {
    var each by remember { mutableStateOf(true) }
    var ranges by remember { mutableStateOf("") }
    val groups = remember(ranges, count) {
        ranges.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.map { r -> r to parsePages(r, count) }
    }
    val valid = groups.isNotEmpty() && groups.all { it.second != null }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_split_pdf)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pdf_each_part_is_added_to), style = JType.small, color = jc.muted)
                ChoiceLine(pluralStringResource(R.plurals.pdf_every_page_own, count, count), each) { each = true }
                ChoiceLine(stringResource(R.string.pdf_by_page_ranges), !each) { each = false }
                if (!each) {
                    OutlinedTextField(
                        ranges, { ranges = it }, Modifier.fillMaxWidth(), label = { Text(if (count >= 3) stringResource(R.string.pdf_ranges_e_g_1_2, count) else stringResource(R.string.pdf_ranges_e_g_1_2_2)) }, singleLine = true,
                        isError = ranges.isNotBlank() && !valid,
                    )
                    Text(
                        if (ranges.isBlank()) pluralStringResource(R.plurals.pdf_one_per_range, count, count) else if (valid) pluralStringResource(R.plurals.pdf_makes, groups.size, groups.size) else stringResource(R.string.pdf_use_page_numbers, count.toString()),
                        style = JType.small, color = if (ranges.isNotBlank() && !valid) jc.danger else jc.muted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSplit(if (each) null else groups.map { it.first to it.second!! }) }, enabled = each || valid) {
                Text(stringResource(R.string.pdf_split_2), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun ChoiceLine(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(JShape.xs).clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) jc.accent else jc.muted, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(jc.accent))
        }
        Text(label, style = JType.item, color = jc.ink)
    }
}

/**
 * Merge: pick another PDF in this note, or one from the phone; it goes after this one in a new PDF. [onMerge]
 * gets a function (run off the main thread) that returns the other PDF's name and file.
 */
@Composable
private fun MergeDialog(vm: EditorViewModel, others: List<Attachment>, onDismiss: () -> Unit, onMerge: (() -> Pair<Attachment, File>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val a = try { vm.importForMerge(uri) } catch (e: Exception) { Toast.makeText(context, errorText(context, e) ?: context.getString(R.string.pdf_couldnt_open_that_file), Toast.LENGTH_LONG).show(); return@launch }
            onMerge { a to vm.fileOf(a) }
        }
    }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_merge_with)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.pdf_the_other_pdfs_pages_go), style = JType.small, color = jc.muted)
                Spacer(Modifier.height(4.dp))
                others.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clip(JShape.xs).clickable { onMerge { o to vm.fileOf(o) } }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        JIcon(ICON_PDF, jc.accent, size = 22.dp)
                        Text(o.name, style = JType.item, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(JShape.xs).clickable { pick.launch(arrayOf(PDF_MIME)) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    JIcon(Ico.FILE, jc.accent, size = 22.dp)
                    Text(stringResource(R.string.pdf_choose_a_pdf_on_this), style = JType.item, color = jc.accent)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
