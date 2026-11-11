package com.shuva.jotter.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shuva.jotter.data.FAMILY_MONO
import com.shuva.jotter.data.FAMILY_SANS
import com.shuva.jotter.data.FAMILY_SERIF
import com.shuva.jotter.data.Pdf
import com.shuva.jotter.data.TextEdit
import com.shuva.jotter.data.TextRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val ICON_BOLD = "M7 5h6a3.5 3.5 0 0 1 0 7H7zM7 12h7a3.5 3.5 0 0 1 0 7H7z"
private const val ICON_SMALLER = "M4 18l4-11 4 11M5.5 14h5M15 12h6"
private const val ICON_BIGGER = "M4 18l4-11 4 11M5.5 14h5M15 12h6M18 9v6"
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/** One changed or new piece of text while editing: a line of the PDF ([run]) or text added at a tapped spot. */
private class Draft(
    val key: String, val page: Int, val run: TextRun?, text: String, val x: Float, val baseline: Float,
    size: Float, bold: Boolean, val color: Int, val coverColor: Int, val family: Int = FAMILY_SANS, val cover: List<Float>? = null,
) {
    var text by mutableStateOf(text)
    var size by mutableFloatStateOf(size)
    var bold by mutableStateOf(bold)
    val changed get() = if (run == null) text.isNotBlank() else text != run.text || size != run.size || bold != run.bold

    fun edit() = TextEdit(page, text, x, baseline, size, bold, family, color, cover, coverColor)
}

/**
 * Edit text: each line of text on the page is outlined; tap one to change it (or clear it to remove it), or tap an
 * empty spot to add text there. Save writes the changes into the PDF as real text, each covering the words it replaces.
 */
@Composable
internal fun PdfTextEditor(file: File, sizes: List<Pair<Int, Int>>, startPage: Int, onCancel: () -> Unit, onSave: (List<TextEdit>) -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(startPage.coerceIn(0, (sizes.size - 1).coerceAtLeast(0))) }
    val drafts = remember { mutableStateMapOf<String, Draft>() }
    var active by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var added by remember { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    val runs by produceState<List<TextRun>?>(null, page) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { Pdf.textRuns(context, file, page) }.onFailure { android.util.Log.w("PdfText", "runs", it) }.getOrDefault(emptyList()) }
    }
    val changes = drafts.values.filter { it.changed }
    val current = active?.let { drafts[it] }

    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(active) { if (active == null) keyboard?.hide() }
    fun finish() {
        val d = current ?: return
        if (d.run == null && d.text.isBlank()) drafts.remove(d.key)
        active = null
    }
    val cancel = { finish(); if (drafts.values.any { it.changed }) leaving = true else onCancel() }

    Dialog(onDismissRequest = cancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FullWindow()
        Column(Modifier.fillMaxSize().background(jc.page).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).background(jc.bg).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Ico.CLOSE, "Cancel", cancel)
                Column(Modifier.weight(1f)) {
                    Text("Edit text", style = JType.rowTitle, color = jc.ink)
                    Text(if (changes.isEmpty()) "No changes yet" else plural(changes.size, "change"), style = JType.small, color = jc.muted)
                }
                TextAction("Save", { finish(); onSave(drafts.values.filter { it.changed }.map { it.edit() }) }, bold = true, enabled = changes.isNotEmpty())
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
            Text(
                when {
                    current != null -> "Type to change it. Clear it to remove the line."
                    runs == null -> "Finding the text…"
                    runs!!.isEmpty() -> "No text found on this page (it may be a scan). Tap anywhere to add text."
                    else -> "Tap a line to change it, or tap an empty spot to add text. Pinch to zoom."
                },
                Modifier.fillMaxWidth().background(jc.bg).padding(horizontal = 16.dp, vertical = 8.dp),
                style = JType.small, color = jc.muted, textAlign = TextAlign.Center,
            )

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val (w, h) = sizes.getOrElse(page) { 612 to 792 }
                val density = LocalDensity.current
                val fit = maxWidth - 24.dp
                val pageWidth = fit * zoom
                val scale = with(density) { pageWidth.toPx() } / w // pixels per point
                val renderPx = (with(density) { fit.toPx() } * zoom.coerceAtMost(2f)).roundToInt().coerceIn(200, 2400)
                val bmp by produceState<Bitmap?>(null, page, renderPx / 300) { value = Pdf.render(file, page, renderPx) }
                val image = remember(bmp) { bmp?.asImageBitmap() }

                Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                while (true) {
                                    val e = awaitPointerEvent(PointerEventPass.Initial)
                                    if (e.changes.none { it.pressed }) break
                                    if (e.changes.count { it.pressed } >= 2) {
                                        zoom = (zoom * e.calculateZoom()).coerceIn(1f, 4f)
                                        e.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        },
                ) {
                    Box(
                        Modifier.padding(12.dp).size(pageWidth, pageWidth * (h.toFloat() / w.coerceAtLeast(1))).background(Color.White).border(1.dp, jc.line)
                            .pointerInput(page, runs, scale, bmp) {
                                detectTapGestures { o ->
                                    val px = o.x / scale
                                    val py = o.y / scale
                                    finish()
                                    val list = runs.orEmpty()
                                    val pad = 3f / zoom
                                    val hit = list.indices.firstOrNull { i ->
                                        val r = list[i]
                                        px in r.x - pad..r.x + r.width + pad && py in r.top - pad..r.bottom + pad
                                    }
                                    if (hit != null) {
                                        val key = "$page:$hit"
                                        if (key !in drafts) {
                                            val r = list[hit]
                                            val (bg, ink) = bmp?.let { sampleColors(it, r, w) } ?: (WHITE to BLACK)
                                            drafts[key] = Draft(key, page, r, r.text, r.x, r.baseline, r.size, r.bold, ink, bg, r.family, coverOf(r, list))
                                        }
                                        active = key
                                    } else {
                                        // New text takes the size of the nearest line, so it fits in with the page.
                                        val near = list.minByOrNull { abs(it.baseline - py) }
                                        val size = near?.size?.coerceIn(6f, 48f) ?: 12f
                                        val key = "$page:new:${added++}"
                                        drafts[key] = Draft(key, page, null, "", px, py + size * 0.35f, size, false, BLACK, WHITE, near?.family ?: FAMILY_SANS)
                                        active = key
                                    }
                                }
                            }
                            .semantics { contentDescription = "Page ${page + 1}" },
                    ) {
                        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        // Lines that can be edited, lightly outlined.
                        runs?.forEachIndexed { i, r ->
                            val key = "$page:$i"
                            if (drafts[key]?.changed == true || active == key) return@forEachIndexed
                            Box(
                                Modifier.offset { IntOffset(((r.x - 1f) * scale).roundToInt(), ((r.top - 1f) * scale).roundToInt()) }
                                    .size(with(density) { ((r.width + 2f) * scale).toDp() }, with(density) { ((r.bottom - r.top + 2f) * scale).toDp() })
                                    .background(jc.accent.copy(alpha = 0.06f), RoundedCornerShape(2.dp))
                                    .border(1.dp, jc.accent.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
                            )
                        }
                        drafts.values.filter { it.page == page }.forEach { d -> DraftView(d, scale, active == d.key) { d.text = it } }
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

            Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
            Row(Modifier.fillMaxWidth().height(60.dp).background(jc.bg).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current != null) {
                    EditTool(ICON_SMALLER, "Smaller") { current.size = (current.size - 1f).coerceAtLeast(4f) }
                    EditTool(ICON_BIGGER, "Bigger") { current.size = (current.size + 1f).coerceAtMost(96f) }
                    EditTool(ICON_BOLD, "Bold", on = current.bold) { current.bold = !current.bold }
                    EditTool(Ico.TRASH, if (current.run != null) "Remove" else "Delete", danger = true) {
                        if (current.run != null) current.text = "" else drafts.remove(current.key)
                        active = null
                    }
                    if (current.changed && current.run != null) EditTool(Ico.UNDO, "Undo") { drafts.remove(current.key); active = null }
                    Spacer(Modifier.weight(1f))
                    TextAction("Done", { finish() }, bold = true)
                    Spacer(Modifier.width(8.dp))
                } else {
                    IconAction(Ico.BACK, "Previous page", { page-- }, enabled = page > 0)
                    Text("Page ${page + 1} of ${sizes.size}", Modifier.weight(1f), style = JType.secondary, color = jc.ink, textAlign = TextAlign.Center)
                    IconAction(Ico.CHEVRON, "Next page", { page++ }, enabled = page < sizes.lastIndex)
                }
            }
        }
    }

    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false },
        title = { Text("Discard your changes?") },
        text = { Text("The text you changed won't be saved to the PDF.") },
        confirmButton = { TextButton({ leaving = false; onCancel() }) { Text("Discard", color = jc.danger) } },
        dismissButton = { TextButton({ leaving = false }) { Text("Keep editing", color = jc.accent) } },
        containerColor = jc.bg,
    )
}

/** A draft on the page: covering the old words, and its new text (a text field while it's being edited). */
@Composable
private fun DraftView(d: Draft, scale: Float, editing: Boolean, onText: (String) -> Unit) {
    val density = LocalDensity.current
    val run = d.run
    val c = d.cover
    if (c != null && (editing || d.changed)) Box(
        Modifier.offset { IntOffset((c[0] * scale).roundToInt(), (c[1] * scale).roundToInt()) }
            .size(with(density) { ((c[2] - c[0]) * scale).toDp() }, with(density) { ((c[3] - c[1]) * scale).toDp() })
            .background(Color(d.coverColor)),
    )
    if (!editing && !d.changed) return
    val shown = with(density) { (d.size * scale).toSp() }
    // While typing, small print is shown at a readable size; it goes back to its real size when done.
    val size = if (editing) max(shown.value, 15f).sp else shown
    val style = TextStyle(
        color = Color(d.color), fontSize = size, lineHeight = size * 1.2f,
        fontWeight = if (d.bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = when (d.family) { FAMILY_MONO -> FontFamily.Monospace; FAMILY_SERIF -> FontFamily.Serif; else -> FontFamily.SansSerif },
    )
    val top = (d.baseline - d.size * 0.95f) * scale
    val at = Modifier.offset { IntOffset((d.x * scale).roundToInt(), top.roundToInt()) }
    if (editing) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(d.key) { runCatching { focus.requestFocus() } }
        BasicTextField(
            d.text, onText,
            at.widthIn(min = with(density) { ((run?.width ?: 60f) * scale).toDp() }.coerceAtLeast(48.dp))
                .background(Color(d.coverColor)).border(1.5.dp, jc.accent, RoundedCornerShape(3.dp)).padding(horizontal = 2.dp)
                .focusRequester(focus),
            textStyle = style, cursorBrush = SolidColor(jc.accent),
        )
    } else if (d.text.isNotBlank()) {
        Text(d.text, at, style = style, softWrap = false)
    }
}

@Composable
private fun RowScope.EditTool(icon: String, label: String, on: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 56.dp).clip(RoundedCornerShape(10.dp)).background(if (on) jc.accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val c = if (danger) jc.danger else if (on) jc.accent else jc.ink
        JIcon(icon, c, size = 20.dp)
        Text(label, style = JType.small.copy(fontSize = 11.sp), color = c, maxLines = 1)
    }
}

/**
 * The area painted over line [r] to hide it: just around its letters, but never into the lines above and below it
 * (in tightly spaced text the letters' boxes overlap).
 */
private fun coverOf(r: TextRun, all: List<TextRun>): List<Float> {
    val beside = all.filter { it !== r && it.x < r.x + r.width && r.x < it.x + it.width }
    val above = beside.filter { it.baseline < r.baseline }.maxOfOrNull { it.baseline + it.size * 0.12f }
    val below = beside.filter { it.baseline > r.baseline }.minOfOrNull { it.baseline - it.size * 0.72f }
    val top = maxOf(r.top - 0.6f, above ?: Float.NEGATIVE_INFINITY).coerceAtMost(r.baseline - r.size * 0.6f)
    val bottom = minOf(r.bottom + 0.6f, below ?: Float.POSITIVE_INFINITY).coerceAtLeast(r.baseline + 0.5f)
    return listOf(r.x - 0.8f, top, r.x + r.width + 1f, bottom)
}

/**
 * The page's color behind [r] (the most common color just around it) and its text color (the pixel inside that's
 * least like the background), read from the rendered page [bmp] of a page [pageWidth] points wide.
 */
private fun sampleColors(bmp: Bitmap, r: TextRun, pageWidth: Int): Pair<Int, Int> {
    val k = bmp.width / pageWidth.toFloat()
    val l = (r.x * k).toInt(); val t = (r.top * k).toInt(); val rt = ((r.x + r.width) * k).toInt(); val b = (r.bottom * k).toInt()
    fun px(x: Int, y: Int) = bmp.getPixel(x.coerceIn(0, bmp.width - 1), y.coerceIn(0, bmp.height - 1))
    val around = buildList {
        val step = max(1, (rt - l) / 16)
        for (x in l..rt step step) { add(px(x, t - 3)); add(px(x, b + 3)) }
        add(px(l - 4, (t + b) / 2)); add(px(rt + 4, (t + b) / 2))
    }
    val bucket = around.groupingBy { it and 0xF0F0F0 }.eachCount().maxByOrNull { it.value }?.key
    val bg = around.firstOrNull { (it and 0xF0F0F0) == bucket } ?: WHITE
    fun dist(a: Int, c: Int) = abs((a shr 16 and 0xFF) - (c shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (c shr 8 and 0xFF)) + abs((a and 0xFF) - (c and 0xFF))
    var ink = BLACK
    var best = -1
    val stepX = max(1, (rt - l) / 200)
    for (y in t..b) for (x in l..rt step stepX) {
        val p = px(x, y)
        val d = dist(p, bg)
        if (d > best) { best = d; ink = p }
    }
    return (bg or 0xFF000000.toInt()) to (if (best < 60) BLACK else ink or 0xFF000000.toInt())
}
