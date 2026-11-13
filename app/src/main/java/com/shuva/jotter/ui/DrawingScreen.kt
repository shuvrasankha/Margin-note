package com.shuva.jotter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.BoxScope
import com.shuva.jotter.data.Drawing
import com.shuva.jotter.data.GRID_STEP
import com.shuva.jotter.data.Paper
import com.shuva.jotter.data.paperStyle
import com.shuva.jotter.data.withPaper
import com.shuva.jotter.data.PenStroke
import com.shuva.jotter.data.PenTool
import com.shuva.jotter.data.bounds
import com.shuva.jotter.data.inkRows
import com.shuva.jotter.data.inkTop
import com.shuva.jotter.data.fitScale
import com.shuva.jotter.data.grownToFit
import android.view.MotionEvent
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import kotlin.math.roundToInt
import com.shuva.jotter.data.moved
import com.shuva.jotter.data.recognizeShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.shuva.jotter.data.straightLine
import com.shuva.jotter.data.strokesInside

/**
 * How a stored ink colour shows on the page. Drawings sit on the note's own background, so on a dark page the
 * inks are swapped for lighter versions of themselves (black ink turns white). The stored colours don't change.
 */
private val DARK_INKS = mapOf(
    0xFF111111 to 0xFFF5F5F7, 0xFFB25E00 to 0xFFFFB340, 0xFF0A66D9 to 0xFF6AAEFF, 0xFF1F7A4D to 0xFF5FD37E, 0xFF8E8E93 to 0xFFA1A1A8,
    0xFFD12F25 to 0xFFFF6B5E, 0xFF7440C9 to 0xFFB592FF,
)

/** The ink colours for the current page: as stored on a light page, lightened on a dark one. */
@Composable
fun rememberInk(): (Long) -> Color {
    val dark = jc.bg.luminance() < 0.5f
    return remember(dark) { if (dark) { c -> Color(DARK_INKS[c] ?: c) } else { c -> Color(c) } }
}

// Seven inks and three stroke widths.
private val INKS = listOf(
    0xFF111111 to "Black", 0xFFB25E00 to "Amber", 0xFFD12F25 to "Red", 0xFF0A66D9 to "Blue", 0xFF1F7A4D to "Green",
    0xFF7440C9 to "Purple", 0xFF8E8E93 to "Grey",
)
private val WIDTHS = listOf(Triple("Fine", 2f, 4.dp), Triple("Medium", 4f, 8.dp), Triple("Bold", 7f, 14.dp))

/** One stroke in drawing units: pen solid, marker wide and see-through, pencil light with a grainy second pass. */
private fun DrawScope.drawStroke(st: PenStroke, ink: (Long) -> Color) {
    val p = st.points
    if (p.size < 2) return
    val base = ink(st.color)
    val blend = if (st.erase) BlendMode.Clear else BlendMode.SrcOver
    val color = when {
        st.erase -> Color.Transparent
        st.tool == PenTool.MARKER -> base.copy(alpha = 0.35f)
        st.tool == PenTool.PENCIL -> base.copy(alpha = 0.5f)
        else -> base
    }
    if (p.size == 2) { drawCircle(color, st.width / 2, Offset(p[0], p[1]), blendMode = blend); return }
    val path = smoothPath(p)
    val cap = if (st.tool == PenTool.MARKER && !st.erase) StrokeCap.Square else StrokeCap.Round
    drawPath(path, color, style = Stroke(st.width, cap = cap, join = StrokeJoin.Round), blendMode = blend)
    if (st.tool == PenTool.PENCIL && !st.erase) {
        val grain = PathEffect.dashPathEffect(floatArrayOf(st.width * 0.6f, st.width * 0.9f))
        drawPath(path, base.copy(alpha = 0.55f), style = Stroke(st.width * 0.55f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = grain))
    }
}

/**
 * A curve through the points (x, y pairs): each point bends the line towards it, passing through the midpoints
 * between points, so fast strokes come out round instead of as straight segments.
 */
private fun smoothPath(p: List<Float>) = Path().apply {
    moveTo(p[0], p[1])
    val n = p.size / 2
    if (n == 2) { lineTo(p[2], p[3]); return@apply }
    for (i in 1 until n - 1) {
        val x = p[2 * i]; val y = p[2 * i + 1]
        quadraticTo(x, y, (x + p[2 * i + 2]) / 2, (y + p[2 * i + 3]) / 2)
    }
    lineTo(p[p.size - 2], p[p.size - 1])
}

/**
 * Draws [drawing] fitted and centred in this area, plus a stroke in progress. Strokes at [lifted] are drawn
 * shifted by [lift] (drawing units) while the lasso drags them. Erase strokes clear, so callers need an offscreen layer.
 */
fun DrawScope.drawStrokes(
    drawing: Drawing, ink: (Long) -> Color, extra: PenStroke? = null, lifted: Set<Int> = emptySet(), lift: Offset = Offset.Zero,
) {
    val s = drawing.fitScale(size.width, size.height)
    translate((size.width - drawing.width * s) / 2, (size.height - drawing.height * s) / 2) {
        scale(s, pivot = Offset.Zero) { drawInk(drawing.strokes, ink, extra, lifted, lift) }
    }
}

/** [strokes] in drawing units, with no fitting: the caller sets the transform. */
private fun DrawScope.drawInk(strokes: List<PenStroke>, ink: (Long) -> Color, extra: PenStroke?, lifted: Set<Int>, lift: Offset) {
    strokes.forEachIndexed { i, st ->
        if (i in lifted) translate(lift.x, lift.y) { drawStroke(st, ink) } else drawStroke(st, ink)
    }
    extra?.let { drawStroke(it, ink) }
}

/** Where the drawing sits on screen in the editor: drawing point p shows at origin + p * scale. */
private data class View(val scale: Float, val origin: Offset) {
    fun units(o: Offset) = (o - origin) / scale
}

/** The view that fits [d] in a [w] x [h] area, centred: how the canvas opens. */
private fun fitView(d: Drawing, w: Float, h: Float): View {
    val s = d.fitScale(w, h)
    return View(s, Offset((w - d.width * s) / 2, (h - d.height * s) / 2))
}

/** How far the endless canvas zooms, relative to the fitted view. */
private const val MIN_ZOOM = 0.2f
/** How long to hold still at the end of a stroke before it snaps to a shape. */
private const val HOLD_MS = 550L
private const val MAX_ZOOM = 8f

/** Touches wider than this are taken for a resting palm and ignored. */
private val PALM_SIZE = 110.dp

private val PointerType.isPen get() = this == PointerType.Stylus || this == PointerType.Eraser

/**
 * Paper is black and white, from the theme: [faint] for grids and guide lines, [strong] for writing lines and
 * margins. Grey on white in light mode, grey on black in dark mode, light enough to sit behind any ink.
 */
class PaperColors(val faint: Color, val strong: Color)

@Composable
fun paperColors() = PaperColors(jc.line, lerp(jc.line, jc.ink, 0.28f))
/** Where the margin sits, in drawing units from the left of the first page. */
private const val MARGIN_X = 2 * GRID_STEP

/**
 * Lines every [period] drawing units, starting at [offset], across the visible area at [view]: horizontal
 * ([across] true) or vertical. Skipped once zoomed so far out that they'd merge into a wash.
 */
private fun DrawScope.paperLines(
    view: View, period: Float, offset: Float, color: Color, width: Float, across: Boolean, below: Float = 0f,
) {
    if (period * view.scale < 4f) return
    val origin = if (across) view.origin.y else view.origin.x
    val extent = if (across) size.height + below else size.width
    val first = kotlin.math.ceil((-origin / view.scale - offset) / period).toInt()
    val last = kotlin.math.floor(((extent - origin) / view.scale - offset) / period).toInt()
    for (k in first..last) {
        val at = origin + (offset + k * period) * view.scale
        if (across) drawLine(color, Offset(0f, at), Offset(size.width, at), width) else drawLine(color, Offset(at, 0f), Offset(at, size.height + below), width)
    }
}

/** A single vertical line at [x] drawing units, the full height of the visible area. */
private fun DrawScope.margin(view: View, x: Float, color: Color, below: Float = 0f) {
    val at = view.origin.x + x * view.scale
    drawLine(color, Offset(at, 0f), Offset(at, size.height + below), 1.5.dp.toPx())
}

/**
 * [paper] across the whole visible area at [view] (the endless canvas has no edge), and [below] px past its
 * bottom. [line] is the grid grey.
 */
private fun DrawScope.drawEndlessPaper(view: View, paper: Paper, colors: PaperColors, below: Float = 0f) {
    val thin = 1.dp.toPx()
    val line = colors.faint
    val strong = colors.strong
    fun paperLines(view: View, period: Float, offset: Float, color: Color, width: Float, across: Boolean) =
        paperLines(view, period, offset, color, width, across, below)
    when (paper) {
        Paper.PLAIN -> Unit
        Paper.SQUARES -> {
            paperLines(view, GRID_STEP, 0f, line, thin, across = true)
            paperLines(view, GRID_STEP, 0f, line, thin, across = false)
        }
        // Fine squares with a heavier line every fifth, like engineering pads.
        Paper.GRAPH -> {
            val minor = GRID_STEP / 3
            val faint = line.copy(alpha = line.alpha * 0.55f)
            paperLines(view, minor, 0f, faint, thin * 0.75f, across = true)
            paperLines(view, minor, 0f, faint, thin * 0.75f, across = false)
            paperLines(view, minor * 5, 0f, line, thin * 1.2f, across = true)
            paperLines(view, minor * 5, 0f, line, thin * 1.2f, across = false)
        }
        // College ruled: lines and a margin.
        Paper.RULED -> {
            paperLines(view, GRID_STEP, 0f, strong, thin, across = true)
            margin(view, MARGIN_X, strong, below)
        }
        // Handwriting practice: sets of four lines (darker top and bottom, faint between) with a gap, and a double margin.
        Paper.FOUR_LINE -> {
            val gap = GRID_STEP / 2
            val block = gap * 5
            paperLines(view, block, 0f, strong, thin, across = true)
            paperLines(view, block, gap, line, thin, across = true)
            paperLines(view, block, gap * 2, line, thin, across = true)
            paperLines(view, block, gap * 3, strong, thin, across = true)
            margin(view, MARGIN_X, strong, below)
            margin(view, MARGIN_X + 6f, strong, below)
        }
    }
}

/** Squared paper behind the ink (outside the offscreen layer, so the eraser doesn't remove it). */
fun DrawScope.drawGrid(drawing: Drawing, line: Color) {
    if (!drawing.grid) return
    val s = drawing.fitScale(size.width, size.height)
    val ox = (size.width - drawing.width * s) / 2
    val oy = (size.height - drawing.height * s) / 2
    val step = GRID_STEP * s
    var x = ox
    while (x <= ox + drawing.width * s) { drawLine(line, Offset(x, oy), Offset(x, oy + drawing.height * s), 1.dp.toPx()); x += step }
    var y = oy
    while (y <= oy + drawing.height * s) { drawLine(line, Offset(ox, y), Offset(ox + drawing.width * s, y), 1.dp.toPx()); y += step }
}

/** Space kept above and below a drawing's ink in a note, in drawing units. */
const val INLINE_PAD = 12f

/**
 * A drawing as part of the note: no card, just the ink on its paper, as wide as the text and cut to the rows
 * its ink covers (see [inkRows]). The paper's lines stay where they were while drawing. Size it to [inlineHeight].
 */
@Composable
fun InlineDrawing(drawing: Drawing, modifier: Modifier) {
    val shown = remember(drawing) { drawing.inkRows(INLINE_PAD) }
    val top = remember(drawing) { drawing.inkTop(INLINE_PAD) }
    val paper = drawing.paperStyle
    val ink = rememberInk()
    val line = paperColors()
    Canvas(
        modifier.clipToBounds()
            // The paper sits outside the ink's layer, so erase strokes never rub it out.
            .drawBehind {
                if (paper == Paper.PLAIN) return@drawBehind
                val s = shown.fitScale(size.width, size.height)
                val origin = Offset((size.width - shown.width * s) / 2, (size.height - shown.height * s) / 2 - top * s)
                drawEndlessPaper(View(s, origin), paper, line)
            }
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) { drawStrokes(shown, ink) }
}

/** How tall [drawing] is in a note [width] pixels wide (its ink rows, scaled to that width). */
fun inlineHeight(drawing: Drawing, width: Float): Float =
    drawing.inkRows(INLINE_PAD).let { if (it.width <= 0f) 0f else it.height * width / it.width }

/**
 * [paper] behind a note's text laid out as [layout]. Ruled and four-line paper rule each line of text where
 * it sits (titles and headings included); squared and graph paper cover the page, with squares as tall as a
 * [bodyLine] (px). Lines where [skip] is true (photos, drawings, tables, link cards) are left bare: they
 * carry their own background.
 */
fun DrawScope.drawNotePaper(
    paper: Paper, layout: TextLayoutResult, bodyLine: Float, bodyFont: Float, fontOf: (Int) -> Float,
    skip: (Int) -> Boolean, colors: PaperColors, below: Float,
) {
    if (paper == Paper.PLAIN || layout.lineCount == 0) return
    val thin = 1.dp.toPx()
    // Under the last line, the page carries on as empty body lines.
    val end = layout.getLineBottom(layout.lineCount - 1)
    val extra = generateSequence(end + bodyLine) { it + bodyLine }.takeWhile { it < size.height + below }.toList()
    when (paper) {
        Paper.PLAIN -> Unit
        Paper.SQUARES, Paper.GRAPH -> {
            val bare = Path()
            for (i in 0 until layout.lineCount) if (skip(i)) {
                bare.addRect(androidx.compose.ui.geometry.Rect(0f, layout.getLineTop(i), size.width, layout.getLineBottom(i)))
            }
            val square = if (paper == Paper.SQUARES) GRID_STEP else GRID_STEP / 3 * 5 // a graph block per text line
            clipPath(bare, androidx.compose.ui.graphics.ClipOp.Difference) {
                drawEndlessPaper(View(bodyLine / square, Offset.Zero), paper, colors, below)
            }
        }
        Paper.RULED -> {
            for (i in 0 until layout.lineCount) if (!skip(i)) {
                val y = layout.getLineBottom(i) - thin
                drawLine(colors.strong, Offset(0f, y), Offset(size.width, y), thin)
            }
            extra.forEach { b -> drawLine(colors.strong, Offset(0f, b - thin), Offset(size.width, b - thin), thin) }
        }
        // A copybook band for each line of text, fitted to its font (Inter's proportions): darker at the top of the
        // capitals and the bottom of the descenders, faint at the x-height and on the baseline, so lowercase
        // letters fill the middle and every line keeps a clear gap to the next band.
        Paper.FOUR_LINE -> {
            val pink = colors.strong
            val grey = colors.faint
            fun band(base: Float, font: Float) =
                listOf(-INTER_CAP to pink, -INTER_X to grey, 0f to grey, INTER_DESCENT to pink).forEach { (k, c) ->
                    val y = base + k * font
                    drawLine(c, Offset(0f, y), Offset(size.width, y), thin)
                }
            var bodyBase: Float? = null // where a body line's baseline sits above its bottom, to carry on the page
            for (i in 0 until layout.lineCount) if (!skip(i)) {
                val font = fontOf(i)
                band(layout.getLineBaseline(i), font)
                if (bodyBase == null && font == bodyFont) bodyBase = layout.getLineBottom(i) - layout.getLineBaseline(i)
            }
            val fromBottom = bodyBase ?: ((bodyLine - (INTER_CAP + INTER_DESCENT) * bodyFont) / 2 + INTER_DESCENT * bodyFont)
            extra.forEach { b -> band(b - fromBottom, bodyFont) }
        }
    }
}

// Inter's cap height, x-height and descender, as fractions of the font size.
private const val INTER_CAP = 0.727f
private const val INTER_X = 0.546f
private const val INTER_DESCENT = 0.24f

/** A small square of [paper], for pickers. */
@Composable
fun PaperSwatch(paper: Paper, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val line = paperColors()
    Canvas(Modifier.size(size).clip(RoundedCornerShape(4.dp)).background(jc.surface).border(1.dp, line.faint, RoundedCornerShape(4.dp))) {
        drawEndlessPaper(View(this.size.width / (GRID_STEP * 3.2f), Offset.Zero), paper, line)
    }
}

/** Picks the paper behind a note's text. */
@Composable
fun PaperDialog(current: Paper, onPick: (Paper) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(jc.bg).padding(vertical = 16.dp)) {
            Text("Paper", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink)
            Text("Shown behind this note's text.", Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted)
            Spacer(Modifier.height(8.dp))
            Paper.entries.forEach { p ->
                val on = p == current
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onPick(p) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PaperSwatch(p)
                    Text(p.label, Modifier.weight(1f), style = JType.row.copy(fontSize = 16.sp), color = if (on) jc.accent else jc.ink)
                    if (on) JIcon(Ico.CHECK, jc.accent, size = 18.dp, stroke = 2.6f)
                }
            }
        }
    }
}

/** The paper switch: a menu of [Paper] kinds, each with a small swatch; lit while the paper isn't plain. */
@Composable
private fun PaperMenu(current: Paper, onPick: (Paper) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconAction(Ico.GRID, "Paper, ${current.label}", { open = true }, color = if (current != Paper.PLAIN) jc.accent else jc.muted)
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = jc.bg) {
            Text("Paper", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = JType.section, color = jc.muted)
            Paper.entries.forEach { p ->
                val on = p == current
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(p.label, style = JType.row, color = if (on) jc.accent else jc.ink) },
                    leadingIcon = { PaperSwatch(p) },
                    trailingIcon = { if (on) JIcon(Ico.CHECK, jc.accent, size = 18.dp, stroke = 2.6f) },
                    onClick = { onPick(p); open = false },
                )
            }
        }
    }
}

/**
 * A drawing tool shown standing in the tray, in a 30 x 84 box: body, cone, tip and band, plus an optional
 * dashed [loop] (the lasso).
 */
private class Tool(
    val mode: Mode, val body: String, val cone: String, val tip: String, val band: String,
    val coneColor: Color? = null, val loop: String = "",
)

/** What a drag on the canvas does. [factor] scales the chosen width. */
private enum class Mode(val label: String, val tool: PenTool, val factor: Float) {
    PEN("Pen", PenTool.PEN, 1f), PENCIL("Pencil", PenTool.PENCIL, 0.8f), MARKER("Marker", PenTool.MARKER, 3f),
    ERASER("Eraser", PenTool.PEN, 6f), LASSO("Lasso", PenTool.PEN, 1f),
}

private val TOOLS = listOf(
    Tool(Mode.PEN, "M7 30h16v54H7z", "M7 30L15 6l8 24z", "M12.3 14L15 6l2.7 8z", "M7 30h16v5H7z"),
    Tool(Mode.PENCIL, "M7 30h16v54H7z", "M7 30L15 8l8 22z", "M12.8 14.5L15 8l2.2 6.5z", "", coneColor = Color(0xFFE8C9A0)),
    Tool(Mode.MARKER, "M5 32h20v52H5z", "M5 32l3-14h14l3 14z", "M10 18h10l-1-7h-8z", "M5 32h20v5H5z"),
    Tool(Mode.ERASER, "M6 22h18v62H6z", "M6 22a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v8H6z", "", "M6 30h18v4H6z"),
    Tool(Mode.LASSO, "M7 36h16v48H7z", "", "", "", loop = "M15 30c-6.5 0-10-4-10-9.5S8.5 11 15 11s10 4 10 9.5S21.5 30 15 30z"),
)

@Composable
private fun ToolPen(tool: Tool, ink: Color, picked: Boolean, onPick: () -> Unit) {
    val paths = remember(tool) {
        listOf(tool.body, tool.cone, tool.tip, tool.band, tool.loop).map { d -> if (d.isEmpty()) null else PathParser().parsePathString(d).toPath() }
    }
    val lift by animateDpAsState(if (picked) 0.dp else 18.dp, label = "lift")
    val outline = jc.line
    val bodyFill = Color(0xFFF7F7F8)
    val erase = tool.mode == Mode.ERASER
    Canvas(
        Modifier.size(44.dp, 84.dp).offset(y = lift).clickable(role = Role.RadioButton, onClick = onPick)
            .semantics { contentDescription = tool.mode.label; selected = picked },
    ) {
        val k = size.height / 84f
        translate((size.width - 30f * k) / 2, 0f) {
            scale(k, pivot = Offset.Zero) {
                val (body, cone, tip, band) = paths
                val loop = paths[4]
                val line = Stroke(1f)
                body?.let { drawPath(it, Color.White); drawPath(it, outline, style = line) }
                cone?.let { drawPath(it, tool.coneColor ?: if (erase) Color(0xFFE6E6EA) else bodyFill); drawPath(it, outline, style = line) }
                tip?.let { drawPath(it, if (erase) Color(0xFF8E8E93) else ink) }
                band?.let { drawPath(it, if (erase) Color(0xFFD4D4D8) else ink) }
                loop?.let { drawPath(it, Color(0xFF5C5C63), style = Stroke(1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2.5f)))) }
            }
        }
    }
}

/**
 * Full-screen canvas laid out like the design: Cancel / Sketch / Done on top, and a tray with undo and redo,
 * pen, pencil, marker, eraser and lasso, the ruler and grid switches, three widths and five inks. Done (or Back)
 * hands back the drawing if anything changed; Cancel drops the changes. A new drawing takes the canvas's
 * size; an old one is fitted in and new strokes are mapped into its units.
 */
@Composable
fun DrawingScreen(
    initial: Drawing?, noteTitle: String, onDone: (Drawing?) -> Unit,
    onText: ((remaining: Drawing?, text: String) -> Unit)? = null,
    /** A picture to draw on (a PDF page), filling [initial]'s page; the page then keeps its size. */
    background: ImageBitmap? = null,
    title: String = "Sketch",
) {
    val density = LocalDensity.current
    var canvas by remember { mutableStateOf(initial) }
    var strokes by remember { mutableStateOf(initial?.strokes ?: emptyList()) }
    // Undo and redo keep whole stroke lists, so moving strokes with the lasso can be undone too.
    val past = remember { mutableStateListOf<List<PenStroke>>() }
    val future = remember { mutableStateListOf<List<PenStroke>>() }
    val current = remember { mutableStateListOf<Float>() }
    var ink by remember { mutableStateOf(INKS[0].first) }
    var width by remember { mutableStateOf(WIDTHS[1].second) }
    var mode by remember { mutableStateOf(Mode.PEN) }
    var ruler by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<Int>()) }
    var lift by remember { mutableStateOf(Offset.Zero) }
    var changed by remember { mutableStateOf(false) }
    /** Whether the stroke in progress erases (the eraser tool, or a pen's eraser end). */
    var liveErase by remember { mutableStateOf(false) }
    // Endless canvas: two fingers pan and zoom. [fit] is the opening view, [view] the current one.
    var area by remember { mutableStateOf(Size.Zero) }
    var view by remember { mutableStateOf<View?>(null) }
    val fit = canvas?.takeIf { area.width > 0f }?.let { fitView(it, area.width, area.height) }
    if (view == null && fit != null) view = fit
    // Palm rejection: once a pen touches the screen, only pens draw and fingers just move the canvas.
    var penSeen by remember { mutableStateOf(false) }
    val palm = with(density) { PALM_SIZE.toPx() }
    fun commit(next: List<PenStroke>) { past.add(strokes); future.clear(); strokes = next; changed = true }
    val strokePx = with(density) { (width * mode.factor).dp.toPx() }
    val penPx by rememberUpdatedState(strokePx)
    val grab = with(density) { 24.dp.toPx() }
    val stillSlop = with(density) { 6.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val accent = jc.accent
    val inkOf = rememberInk()
    val paper = jc.bg
    val gridLine = jc.line
    val paperInk = paperColors()
    val pageDesk = jc.page
    // Ink drawn past the page grows it, so nothing is cut off in the note.
    val finish = { onDone(if (changed) canvas?.copy(strokes = strokes)?.let { if (background != null) it else it.grownToFit(INLINE_PAD) } else null) }
    // Handwriting to text: the strokes being read (the lasso's, or all of them).
    var reading by remember { mutableStateOf<Set<Int>?>(null) }
    val hasInk = strokes.any { !it.erase }
    reading?.let { picked ->
        HandwritingDialog(strokes.filterIndexed { i, _ -> i in picked }, onAdd = { text, removeInk ->
            reading = null
            val left = if (removeInk) strokes.filterIndexed { i, _ -> i !in picked } else strokes
            // Nothing but eraser strokes left: the drawing goes and only the text stays.
            onText?.invoke(canvas?.takeIf { left.any { !it.erase } }?.copy(strokes = left)?.grownToFit(INLINE_PAD), text)
        }, onDismiss = { reading = null })
    }

    Dialog(onDismissRequest = finish, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(jc.bg).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextAction("Cancel", { onDone(null) })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = JType.rowTitle, color = jc.ink)
                    if (noteTitle.isNotBlank()) {
                        Text("In \u201C$noteTitle\u201D", style = JType.small.copy(fontSize = 12.sp), color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (onText != null) IconAction(Ico.HANDWRITING, "Convert handwriting to text", {
                    reading = strokes.indices.toSet()
                }, enabled = hasInk)
                TextAction("Done", finish, bold = true)
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().background(paper).clipToBounds()
                    .onSizeChanged {
                        if (canvas == null) canvas = Drawing(it.width.toFloat(), it.height.toFloat())
                        area = Size(it.width.toFloat(), it.height.toFloat())
                    }
                    .drawBehind {
                        val v = view
                        if (background != null && v != null) {
                            // The page under the ink, outside the ink's layer so erasing never rubs it out, on a grey
                            // desk with an outline, so its edges show.
                            drawRect(pageDesk)
                            val w = canvas!!.width * v.scale; val h = canvas!!.height * v.scale
                            drawImage(
                                background, dstOffset = IntOffset(v.origin.x.roundToInt(), v.origin.y.roundToInt()),
                                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                            )
                            drawRect(gridLine, v.origin, Size(w, h), style = Stroke(1.dp.toPx()))
                        }
                        canvas?.let { c -> v?.let { drawEndlessPaper(it, c.paperStyle, paperInk) } }
                    }
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .pointerInput(canvas != null) {
                        // Keyed on the canvas existing only: restarting on every tool change could drop a stroke already under way.
                        if (canvas == null) return@pointerInput

                        /** The system flagged the touch as accidental (a palm), so whatever it drew is dropped. */
                        fun cancelled(e: PointerEvent): Boolean {
                            val m = e.motionEvent ?: return false
                            return m.actionMasked == MotionEvent.ACTION_CANCEL ||
                                (android.os.Build.VERSION.SDK_INT >= 33 && m.flags and MotionEvent.FLAG_CANCELED != 0)
                        }

                        /** Draws (or erases, or lassos) with [down]. Returns true when a second finger turned it into a pan. */
                        suspend fun AwaitPointerEventScope.draw(down: PointerInputChange, pen: Boolean): Boolean {
                            val v = view ?: return false
                            val d = canvas ?: return false
                            val erasing = mode == Mode.ERASER || down.type == PointerType.Eraser // the pen's eraser end erases too
                            val start = v.units(down.position)
                            val pad = grab / v.scale
                            val box = strokes.bounds(selection)
                            // With the lasso, a drag that starts on the selection moves it; anywhere else draws a new loop.
                            val moving = mode == Mode.LASSO && box != null &&
                                start.x in box[0] - pad..box[2] + pad && start.y in box[1] - pad..box[3] + pad
                            if (mode == Mode.LASSO && !moving) selection = emptySet()
                            current.clear()
                            current.add(start.x); current.add(start.y)
                            liveErase = erasing
                            val straight = ruler && mode != Mode.LASSO && !erasing
                            // Shape snapping: hold still at the end of a stroke and it becomes the shape it looks like.
                            val canSnap = !straight && !moving && mode != Mode.LASSO && !erasing
                            var stillAt = down.position
                            var stillSince = down.uptimeMillis
                            var snapped = false
                            fun trySnap() {
                                if (snapped || current.size < 8) return
                                val shape = recognizeShape(current.toList()) ?: return
                                current.clear(); current.addAll(shape)
                                snapped = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            down.consume()
                            while (true) {
                                val e = if (canSnap && !snapped) withTimeoutOrNull(HOLD_MS) { awaitPointerEvent() } else awaitPointerEvent()
                                if (e == null) { trySnap(); continue } // held perfectly still
                                // A second finger while drawing with a finger means pan or zoom: the stroke so far is dropped.
                                if (!pen && e.changes.count { it.pressed && !it.type.isPen } > 1 || cancelled(e)) {
                                    current.clear(); lift = Offset.Zero
                                    return !cancelled(e)
                                }
                                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                val u = v.units(c.position) // the lift point counts too, so a quick ruler line ends where the finger left
                                if (!c.pressed && u == start) break
                                if (canSnap && c.pressed) {
                                    if ((c.position - stillAt).getDistance() > stillSlop) { stillAt = c.position; stillSince = c.uptimeMillis }
                                    else if (c.uptimeMillis - stillSince >= HOLD_MS) trySnap()
                                }
                                when {
                                    snapped -> Unit // the shape stays as it snapped until the pen lifts
                                    moving -> lift = u - start
                                    straight -> { current.clear(); current.addAll(straightLine(start.x, start.y, u.x, u.y)) }
                                    else -> { current.add(u.x); current.add(u.y) }
                                }
                                // With a pen, everything else on the screen (the palm) is swallowed.
                                if (pen) e.changes.forEach { it.consume() } else c.consume()
                                if (!c.pressed) break
                            }
                            when {
                                moving -> { if (lift != Offset.Zero) commit(strokes.moved(selection, lift.x, lift.y)); lift = Offset.Zero }
                                mode == Mode.LASSO -> selection = d.copy(strokes = strokes).strokesInside(current.toList())
                                else -> commit(strokes + PenStroke(ink, penPx / v.scale, current.toList(), erase = erasing, tool = mode.tool))
                            }
                            current.clear()
                            return false
                        }

                        /**
                         * Fingers pan and pinch-zoom until they lift. A pen touching down meanwhile wins: the canvas goes
                         * back where it was (the "fingers" were a palm landing first) and the pen is returned to draw.
                         */
                        suspend fun AwaitPointerEventScope.navigate(): PointerInputChange? {
                            val before = view
                            while (true) {
                                val e = awaitPointerEvent()
                                e.changes.firstOrNull { it.changedToDown() && it.type.isPen }?.let { view = before; return it }
                                if (e.changes.none { it.pressed }) return null
                                val v = view ?: return null
                                val f = fit ?: return null
                                val centroid = e.calculateCentroid()
                                if (centroid.isSpecified) {
                                    val scale = (v.scale * e.calculateZoom()).coerceIn(f.scale * MIN_ZOOM, f.scale * MAX_ZOOM)
                                    view = View(scale, centroid - (centroid - v.origin) * (scale / v.scale) + e.calculatePan())
                                }
                                e.changes.forEach { it.consume() }
                            }
                        }

                        awaitEachGesture {
                            val first = awaitPointerEvent()
                            val down = first.changes.firstOrNull { it.changedToDown() } ?: return@awaitEachGesture
                            if (down.type.isPen) {
                                penSeen = true
                                draw(down, pen = true)
                                return@awaitEachGesture
                            }
                            // A very wide touch is a palm: ignore it entirely.
                            val m = first.motionEvent
                            if (m != null && m.getToolType(m.actionIndex) == MotionEvent.TOOL_TYPE_FINGER && m.getTouchMajor(m.actionIndex) > palm) return@awaitEachGesture
                            val toNavigate = penSeen || first.changes.count { it.pressed } > 1 || draw(down, pen = false)
                            if (toNavigate) navigate()?.let { pen -> penSeen = true; draw(pen, pen = true) }
                        }
                    }
                    .semantics { contentDescription = "Drawing canvas" },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val v = view ?: return@Canvas
                    val live = if (current.isEmpty() || mode == Mode.LASSO) null
                    else PenStroke(ink, strokePx / v.scale, current.toList(), erase = liveErase, tool = mode.tool)
                    translate(v.origin.x, v.origin.y) {
                        scale(v.scale, pivot = Offset.Zero) {
                            drawInk(strokes, inkOf, live, selection, lift)
                            // The lasso loop being drawn, and a dashed box around the selection.
                            val s = v.scale
                            val dash = PathEffect.dashPathEffect(floatArrayOf(10f / s, 7f / s))
                            if (mode == Mode.LASSO && current.size >= 4) {
                                val loop = Path().apply {
                                    moveTo(current[0], current[1])
                                    for (i in 2 until current.size step 2) lineTo(current[i], current[i + 1])
                                }
                                drawPath(loop, accent, style = Stroke(2f / s, pathEffect = dash))
                            }
                            strokes.bounds(selection)?.let { b ->
                                val m = 10f / s
                                drawRect(
                                    accent, Offset(b[0] - m + lift.x, b[1] - m + lift.y), Size(b[2] - b[0] + 2 * m, b[3] - b[1] + 2 * m),
                                    style = Stroke(2f / s, pathEffect = dash),
                                )
                            }
                        }
                    }
                }
                // Moved or zoomed away from the opening view: the zoom level, tap to go back.
                val v = view
                if (v != null && fit != null && v != fit) {
                    Text(
                        "${(v.scale / fit.scale * 100).roundToInt()}%",
                        Modifier.align(Alignment.BottomStart).padding(12.dp).clip(CircleShape).background(jc.surface)
                            .clickable(role = Role.Button) { view = fit }.semantics { contentDescription = "Zoom ${(v.scale / fit.scale * 100).roundToInt()} percent. Tap to fit the page." }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = JType.small, color = jc.ink,
                    )
                }
                if (selection.isNotEmpty()) {
                    SelectionActions(
                        Modifier.align(Alignment.TopEnd), onText = onText?.let { { reading = selection } },
                        onDuplicate = {
                            // The copies land a little down and to the right, and become the selection, ready to drag.
                            val shift = 24f / (view?.scale ?: 1f)
                            val picked = selection.sorted().map { strokes[it] }
                            val copies = picked.moved(picked.indices.toSet(), shift, shift)
                            val first = strokes.size
                            commit(strokes + copies)
                            selection = (first until strokes.size).toSet()
                        },
                    ) {
                        commit(strokes.filterIndexed { i, _ -> i !in selection })
                        selection = emptySet()
                    }
                }
            }
            HorizontalDivider(color = jc.line)
            Row(
                Modifier.fillMaxWidth().height(92.dp).clipToBounds().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.align(Alignment.CenterVertically)) {
                    IconAction(Ico.UNDO, "Undo", {
                        future.add(strokes); strokes = past.removeAt(past.lastIndex); selection = emptySet(); changed = true
                    }, color = jc.ink, enabled = past.isNotEmpty())
                    IconAction(Ico.REDO, "Redo", {
                        past.add(strokes); strokes = future.removeAt(future.lastIndex); selection = emptySet(); changed = true
                    }, color = jc.ink, enabled = future.isNotEmpty())
                }
                Spacer(Modifier.weight(1f))
                TOOLS.forEach { t ->
                    ToolPen(t, inkOf(ink), picked = mode == t.mode) { mode = t.mode; if (t.mode != Mode.LASSO) selection = emptySet() }
                }
                Spacer(Modifier.weight(1f))
                Column(Modifier.align(Alignment.CenterVertically)) {
                    IconAction(Ico.RULER, if (ruler) "Ruler, on" else "Ruler, off", { ruler = !ruler }, color = if (ruler) jc.accent else jc.muted)
                    PaperMenu(canvas?.paperStyle ?: Paper.PLAIN) { p -> canvas = canvas?.withPaper(p); changed = true }
                }
            }
            HorizontalDivider(color = jc.line)
            Row(
                Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WIDTHS.forEach { (name, w, dot) ->
                    val on = width == w
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(if (on) jc.surface else Color.Transparent)
                            .clickable(role = Role.RadioButton) { width = w }.semantics { contentDescription = "$name stroke"; selected = on },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(dot).clip(CircleShape).background(jc.ink)) }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(1.dp, 28.dp).background(jc.line))
                Spacer(Modifier.weight(1f))
                INKS.forEach { (value, name) ->
                    val on = ink == value
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).clickable(role = Role.RadioButton) {
                            ink = value
                            if (selection.isNotEmpty()) {
                                // With strokes picked by the lasso, an ink recolours them.
                                commit(strokes.mapIndexed { i, st -> if (i in selection && !st.erase) st.copy(color = value) else st })
                            } else if (mode == Mode.ERASER || mode == Mode.LASSO) mode = Mode.PEN
                        }.semantics { contentDescription = if (selection.isNotEmpty()) "Colour selection $name" else name; selected = on },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Selected: the swatch gets a ring with a gap, as in the design.
                        if (on) Box(Modifier.size(34.dp).border(2.dp, inkOf(value), CircleShape))
                        Box(Modifier.size(24.dp).clip(CircleShape).background(inkOf(value)))
                    }
                }
            }
        }
    }
}

/** Over the canvas while the lasso holds strokes: turn them into text or delete them (drag the box to move them). */
@Composable
private fun SelectionActions(modifier: Modifier, onText: (() -> Unit)?, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    Row(modifier.padding(8.dp).clip(CircleShape).background(jc.ink.copy(alpha = 0.85f)).padding(horizontal = 4.dp)) {
        onText?.let { IconAction(Ico.HANDWRITING, "Convert selected handwriting to text", it, color = jc.bg) }
        IconAction(Ico.COPY, "Duplicate selected strokes", onDuplicate, color = jc.bg)
        IconAction(Ico.TRASH, "Delete selected strokes", onDelete, color = jc.bg)
    }
}


/**
 * The ink of [drawing] as a transparent picture [w] x [h] pixels, stretched over its whole page, in the colours it
 * was drawn in. Used to stamp a sketch onto a PDF page.
 */
fun renderInk(drawing: Drawing, w: Int, h: Int): android.graphics.Bitmap {
    val bmp = ImageBitmap(w, h)
    val canvas = androidx.compose.ui.graphics.Canvas(bmp)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat()),
    ) {
        scale(w / drawing.width, h / drawing.height, pivot = Offset.Zero) { drawInk(drawing.strokes, { Color(it) }, null, emptySet(), Offset.Zero) }
    }
    return bmp.asAndroidBitmap()
}
