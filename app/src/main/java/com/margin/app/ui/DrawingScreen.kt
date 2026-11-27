package com.margin.app.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.ColorFilter
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
import com.margin.app.data.Drawing
import com.margin.app.data.GRID_STEP
import com.margin.app.data.Paper
import com.margin.app.data.paperStyle
import com.margin.app.data.withPaper
import com.margin.app.data.PenStroke
import com.margin.app.data.PenTool
import com.margin.app.data.bounds
import com.margin.app.data.inkRows
import com.margin.app.data.inkTop
import com.margin.app.data.fitScale
import com.margin.app.data.grownToFit
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
import com.margin.app.data.moved
import com.margin.app.data.recognizeShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.margin.app.data.straightLine
import com.margin.app.data.strokesInside
import com.margin.app.data.hitBy
import com.margin.app.data.usableWeights
import com.margin.app.data.weightFromPressure
import com.margin.app.data.weightFromSpeed
import com.margin.app.data.argbToHsv
import com.margin.app.data.hsvToArgb
import com.margin.app.data.hexOf
import com.margin.app.data.parseHexColor
import com.margin.app.data.inkOnDarkPage
import com.margin.app.data.withRecentInk
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.drag
import com.margin.app.R
import androidx.compose.ui.res.stringResource

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
    return remember(dark) { if (dark) { c -> Color(DARK_INKS[c] ?: inkOnDarkPage(c)) } else { c -> Color(c) } }
}

// Seven inks and three stroke widths.
private val INKS = listOf(
    0xFF111111 to R.string.drawing_ink_black, 0xFFB25E00 to R.string.drawing_ink_amber, 0xFFD12F25 to R.string.drawing_ink_red, 0xFF0A66D9 to R.string.drawing_ink_blue, 0xFF1F7A4D to R.string.drawing_ink_green,
    0xFF7440C9 to R.string.drawing_ink_purple, 0xFF8E8E93 to R.string.drawing_ink_grey,
)
private val WIDTHS = listOf(Triple(R.string.drawing_width_fine, 2f, 4.dp), Triple(R.string.drawing_width_medium, 4f, 8.dp), Triple(R.string.drawing_width_bold, 7f, 14.dp))

/** One stroke in drawing units: pen solid, marker wide and see-through, pencil light with a grainy second pass. */
private fun DrawScope.drawStroke(st: PenStroke, ink: (Long) -> Color) {
    val p = st.points
    if (p.size < 2) return
    val base = ink(st.color)
    val blend = if (st.erase) BlendMode.Clear else BlendMode.SrcOver
    val color = when {
        st.erase -> Color.Transparent
        st.tool == PenTool.MARKER -> base.copy(alpha = 0.35f)
        st.tool == PenTool.PENCIL -> base.copy(alpha = 0.22f) // a soft wash under the graphite grain
        else -> base
    }
    if (p.size == 2) { drawCircle(color, st.width / 2, Offset(p[0], p[1]), blendMode = blend); return }
    st.usableWeights()?.let { w -> drawWeighted(p, w, st.width, color); return }
    val path = smoothPath(p)
    val cap = if (st.tool == PenTool.MARKER && !st.erase) StrokeCap.Square else StrokeCap.Round
    drawPath(path, color, style = Stroke(st.width, cap = cap, join = StrokeJoin.Round), blendMode = blend)
    if (st.tool == PenTool.PENCIL && !st.erase) {
        // Graphite: the same line through a speckled grain, so it reads like pencil on paper rather than a dashed pen.
        drawPath(
            path, PENCIL_GRAIN, style = Stroke(st.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            colorFilter = ColorFilter.tint(base, BlendMode.SrcIn),
        )
    }
}

/**
 * A pen line whose width follows [weights] (one per point): the same smooth curve as [smoothPath], walked in
 * small steps, drawn as a few runs of round-capped path, a new run wherever the width moves on a step.
 */
private fun DrawScope.drawWeighted(p: List<Float>, weights: List<Float>, width: Float, color: Color) {
    val n = p.size / 2
    // Samples along the curve: x, y and weight.
    val xs = ArrayList<Float>(n * 4); val ys = ArrayList<Float>(n * 4); val ws = ArrayList<Float>(n * 4)
    fun add(x: Float, y: Float, w: Float) { xs += x; ys += y; ws += w }
    // Long segments (a quick swipe gives few points) are cut into short steps, so the width changes gently.
    fun steps(len: Float) = (len / (width * 0.6f).coerceAtLeast(2f)).toInt().coerceIn(4, 64)
    add(p[0], p[1], weights[0])
    if (n == 2) {
        val k = steps(Offset(p[2] - p[0], p[3] - p[1]).getDistance())
        for (j in 1..k) { val t = j / k.toFloat(); add(p[0] + (p[2] - p[0]) * t, p[1] + (p[3] - p[1]) * t, weights[0] + (weights[1] - weights[0]) * t) }
    } else {
        var sx = p[0]; var sy = p[1]; var sw = weights[0]
        for (i in 1 until n - 1) {
            val cx = p[2 * i]; val cy = p[2 * i + 1]; val cw = weights[i]
            val ex = (cx + p[2 * i + 2]) / 2; val ey = (cy + p[2 * i + 3]) / 2; val ew = (cw + weights[i + 1]) / 2
            val steps = steps(Offset(cx - sx, cy - sy).getDistance() + Offset(ex - cx, ey - cy).getDistance())
            for (k in 1..steps) {
                val t = k / steps.toFloat(); val u = 1 - t
                add(u * u * sx + 2 * u * t * cx + t * t * ex, u * u * sy + 2 * u * t * cy + t * t * ey, u * u * sw + 2 * u * t * cw + t * t * ew)
            }
            sx = ex; sy = ey; sw = ew
        }
        add(p[p.size - 2], p[p.size - 1], weights[n - 1])
    }
    // Widths in steps of a thirtieth of the full width: too small to see, and only a few runs per line.
    fun step(w: Float) = Math.round(w * 30) / 30f
    var run = Path().apply { moveTo(xs[0], ys[0]) }
    var runW = step((ws[0] + ws[1]) / 2)
    for (j in 1 until xs.size) {
        val w = step((ws[j - 1] + ws[j]) / 2)
        if (w != runW) {
            drawPath(run, color, style = Stroke(width * runW, cap = StrokeCap.Round, join = StrokeJoin.Round))
            run = Path().apply { moveTo(xs[j - 1], ys[j - 1]) }
            runW = w
        }
        run.lineTo(xs[j], ys[j])
    }
    drawPath(run, color, style = Stroke(width * runW, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A tile of uneven grey dots (mostly solid, some gaps), repeated along pencil strokes. */
private val PENCIL_GRAIN: ShaderBrush by lazy {
    val n = 32
    val rnd = java.util.Random(7)
    val px = IntArray(n * n) {
        val a = when (val r = rnd.nextFloat()) { in 0f..0.18f -> 0f; else -> 0.45f + 0.5f * r }
        ((a * 255).toInt() shl 24) or 0xFFFFFF
    }
    val bmp = android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888)
    // Half-size cells: fine grain at the usual zoom.
    ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated).also {
        it.setLocalMatrix(android.graphics.Matrix().apply { setScale(0.5f, 0.5f) })
    })
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
private fun DrawScope.drawInk(
    strokes: List<PenStroke>, ink: (Long) -> Color, extra: PenStroke?, lifted: Set<Int>, lift: Offset, hidden: Set<Int> = emptySet(),
) {
    strokes.forEachIndexed { i, st ->
        if (i in hidden) return@forEachIndexed
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
 * it sits; squared and graph paper cover the page, with squares as tall as a
 * [bodyLine] (px). Lines where [skip] is true (titles and headings, photos, drawings, tables, link cards)
 * are left bare.
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
        Column(Modifier.clip(JShape.lg).background(jc.bg).padding(vertical = 16.dp)) {
            Text(stringResource(R.string.drawing_paper), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink)
            Text(stringResource(R.string.drawing_shown_behind_this_notes_text), Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted)
            Spacer(Modifier.height(8.dp))
            Paper.entries.forEach { p ->
                val on = p == current
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onPick(p) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PaperSwatch(p)
                    Text(stringResource(p.labelRes), Modifier.weight(1f), style = JType.item, color = if (on) jc.accent else jc.ink)
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
        IconAction(Ico.GRID, stringResource(R.string.drawing_paper_desc, stringResource(current.labelRes)), { open = true }, color = if (current != Paper.PLAIN) jc.accent else jc.muted)
        JMenu(expanded = open, onDismissRequest = { open = false }, width = 220.dp) {
            Text(stringResource(R.string.drawing_paper), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = JType.section, color = jc.muted)
            Paper.entries.forEach { p ->
                val on = p == current
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(p.labelRes), style = JType.row, color = if (on) jc.accent else jc.ink) },
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
private enum class Mode(val label: String, @androidx.annotation.StringRes val labelRes: Int, val tool: PenTool, val factor: Float) {
    PEN("Pen", R.string.drawing_mode_pen, PenTool.PEN, 1f), PENCIL("Pencil", R.string.drawing_mode_pencil, PenTool.PENCIL, 0.8f), MARKER("Marker", R.string.drawing_mode_marker, PenTool.MARKER, 3f),
    ERASER("Eraser", R.string.drawing_mode_eraser, PenTool.PEN, 6f), LASSO("Lasso", R.string.drawing_mode_lasso, PenTool.PEN, 1f),
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val paths = remember(tool) {
        listOf(tool.body, tool.cone, tool.tip, tool.band, tool.loop).map { d -> if (d.isEmpty()) null else PathParser().parsePathString(d).toPath() }
    }
    val lift by animateDpAsState(if (picked) 0.dp else 18.dp, label = "lift")
    val outline = Color.Black.copy(alpha = 0.14f)
    val bodyFill = Color(0xFFF7F7F8)
    // A round barrel: lit from the left, shading off to the right.
    val barrel = remember { Brush.horizontalGradient(0f to Color.White, 0.45f to Color(0xFFF4F4F6), 1f to Color(0xFFDCDCE1), startX = 5f, endX = 25f) }
    val erase = tool.mode == Mode.ERASER
    Canvas(
        Modifier.size(44.dp, 84.dp).offset(y = lift).clickable(role = Role.RadioButton, onClick = onPick)
            .semantics { contentDescription = context.getString(tool.mode.labelRes); selected = picked },
    ) {
        val k = size.height / 84f
        translate((size.width - 30f * k) / 2, 0f) {
            scale(k, pivot = Offset.Zero) {
                val (body, cone, tip, band) = paths
                val loop = paths[4]
                val line = Stroke(1f)
                body?.let { drawPath(it, barrel); drawPath(it, outline, style = line) }
                cone?.let { drawPath(it, tool.coneColor ?: if (erase) Color(0xFFE6E6EA) else bodyFill); drawPath(it, barrel, alpha = 0.5f, blendMode = BlendMode.Multiply); drawPath(it, outline, style = line) }
                // Outlined too, so a white ink (black on a dark page) still shows against the white barrel.
                tip?.let { drawPath(it, if (erase) Color(0xFF8E8E93) else ink); drawPath(it, outline, style = line) }
                band?.let { drawPath(it, if (erase) Color(0xFFD4D4D8) else ink); drawPath(it, outline, style = line) }
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
    title: String = stringResource(R.string.drawing_sketch),
) {
    val density = LocalDensity.current
    var canvas by remember { mutableStateOf(initial) }
    var strokes by remember { mutableStateOf(initial?.strokes ?: emptyList()) }
    // Undo and redo keep whole stroke lists, so moving strokes with the lasso can be undone too.
    val past = remember { mutableStateListOf<List<PenStroke>>() }
    val future = remember { mutableStateListOf<List<PenStroke>>() }
    val current = remember { mutableStateListOf<Float>() }
    var ink by remember { mutableStateOf(INKS[0].first) }
    // Custom colours picked lately, newest first; they stay in the ink row from sketch to sketch.
    val context = androidx.compose.ui.platform.LocalContext.current
    val inkPrefs = remember { context.getSharedPreferences("margin_drawing", android.content.Context.MODE_PRIVATE) }
    var recentInks by remember {
        mutableStateOf(inkPrefs.getString("recent_inks", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() })
    }
    var picking by remember { mutableStateOf(false) }
    var width by remember { mutableStateOf(WIDTHS[1].second) }
    var mode by remember { mutableStateOf(Mode.PEN) }
    var ruler by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<Int>()) }
    var lift by remember { mutableStateOf(Offset.Zero) }
    var changed by remember { mutableStateOf(false) }
    /** Whether the stroke in progress erases (the eraser tool, or a pen's eraser end). */
    var liveErase by remember { mutableStateOf(false) }
    /** The eraser takes whole strokes it touches, rather than rubbing out the ink under it. */
    var eraseStrokes by remember { mutableStateOf(false) }
    /** Strokes the whole-stroke eraser has touched so far in this swipe: hidden now, removed when it lifts. */
    var doomed by remember { mutableStateOf(emptySet<Int>()) }
    var wiping by remember { mutableStateOf(false) }
    /** Line weights of the pen stroke in progress, one per point in [current]. */
    val currentW = remember { mutableStateListOf<Float>() }
    // Endless canvas: two fingers pan and zoom. [fit] is the opening view, [view] the current one.
    var area by remember { mutableStateOf(Size.Zero) }
    var view by remember { mutableStateOf<View?>(null) }
    val fit = canvas?.takeIf { area.width > 0f }?.let { fitView(it, area.width, area.height) }
    if (view == null && fit != null) view = fit
    // Palm rejection: once a pen touches the screen, only pens draw and fingers just move the canvas.
    var penSeen by remember { mutableStateOf(false) }
    val palm = with(density) { PALM_SIZE.toPx() }
    fun commit(next: List<PenStroke>) { past.add(strokes); future.clear(); strokes = next; changed = true }
    /** Picks an ink: with strokes held by the lasso it recolours them, otherwise it goes back to a drawing tool. */
    fun useInk(value: Long) {
        ink = value
        if (selection.isNotEmpty()) commit(strokes.mapIndexed { i, st -> if (i in selection && !st.erase) st.copy(color = value) else st })
        else if (mode == Mode.ERASER || mode == Mode.LASSO) mode = Mode.PEN
    }
    if (picking) InkPicker(ink, onDismiss = { picking = false }) { c ->
        picking = false
        if (INKS.none { it.first == c }) {
            recentInks = recentInks.withRecentInk(c)
            inkPrefs.edit().putString("recent_inks", recentInks.joinToString(",")).apply()
        }
        useInk(c)
    }
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
                TextAction(stringResource(R.string.common_cancel), { onDone(null) })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = JType.rowTitle, color = jc.ink)
                    if (noteTitle.isNotBlank()) {
                        Text(stringResource(R.string.drawing_in, noteTitle), style = JType.caption, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (onText != null) IconAction(Ico.HANDWRITING, stringResource(R.string.drawing_convert_handwriting_to_text), {
                    reading = strokes.indices.toSet()
                }, enabled = hasInk)
                TextAction(stringResource(R.string.common_done), finish, bold = true)
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
                            current.clear(); currentW.clear()
                            current.add(start.x); current.add(start.y)
                            liveErase = erasing
                            val straight = ruler && mode != Mode.LASSO && !erasing
                            // The whole-stroke eraser: picks strokes as it crosses them; no erase stroke is drawn.
                            val wipe = erasing && eraseStrokes
                            wiping = wipe
                            val eraserReach = penPx / v.scale / 2
                            var last = start
                            if (wipe) doomed = strokes.hitBy(start.x, start.y, start.x, start.y, eraserReach)
                            // The pen's line weight: pressure from a stylus, speed from a finger.
                            val weighted = mode == Mode.PEN && !erasing && !straight
                            var lastAt = down.uptimeMillis
                            var lastPos = down.position
                            if (weighted) currentW.add(if (pen) weightFromPressure(down.pressure) else weightFromSpeed(0f, null))
                            // Shape snapping: hold still at the end of a stroke and it becomes the shape it looks like.
                            val canSnap = !straight && !moving && mode != Mode.LASSO && !erasing
                            var stillAt = down.position
                            var stillSince = down.uptimeMillis
                            var snapped = false
                            fun trySnap() {
                                if (snapped || current.size < 8) return
                                val shape = recognizeShape(current.toList()) ?: return
                                current.clear(); current.addAll(shape); currentW.clear() // a clean shape is drawn at full width
                                snapped = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            down.consume()
                            while (true) {
                                val e = if (canSnap && !snapped) withTimeoutOrNull(HOLD_MS) { awaitPointerEvent() } else awaitPointerEvent()
                                if (e == null) { trySnap(); continue } // held perfectly still
                                // A second finger while drawing with a finger means pan or zoom: the stroke so far is dropped.
                                if (!pen && e.changes.count { it.pressed && !it.type.isPen } > 1 || cancelled(e)) {
                                    current.clear(); currentW.clear(); lift = Offset.Zero; doomed = emptySet(); wiping = false
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
                                    wipe -> { doomed = doomed + strokes.hitBy(last.x, last.y, u.x, u.y, eraserReach); last = u }
                                    straight -> { current.clear(); current.addAll(straightLine(start.x, start.y, u.x, u.y)) }
                                    else -> {
                                        current.add(u.x); current.add(u.y)
                                        if (weighted) {
                                            val dt = (c.uptimeMillis - lastAt).coerceAtLeast(1L)
                                            val dp = (c.position - lastPos).getDistance() / density.density
                                            currentW.add(if (pen) weightFromPressure(c.pressure) else weightFromSpeed(dp / dt, currentW.lastOrNull()))
                                            lastAt = c.uptimeMillis; lastPos = c.position
                                        }
                                    }
                                }
                                // With a pen, everything else on the screen (the palm) is swallowed.
                                if (pen) e.changes.forEach { it.consume() } else c.consume()
                                if (!c.pressed) break
                            }
                            when {
                                moving -> { if (lift != Offset.Zero) commit(strokes.moved(selection, lift.x, lift.y)); lift = Offset.Zero }
                                mode == Mode.LASSO -> selection = d.copy(strokes = strokes).strokesInside(current.toList())
                                // One commit for the whole swipe, so one undo brings every stroke back.
                                wipe -> if (doomed.isNotEmpty()) {
                                    commit(strokes.filterIndexed { i, _ -> i !in doomed })
                                    selection = emptySet() // the strokes after the wiped ones have moved up
                                }
                                else -> commit(
                                    strokes + PenStroke(
                                        ink, penPx / v.scale, current.toList(), erase = erasing, tool = mode.tool,
                                        weights = currentW.toList().takeIf { weighted && !snapped && it.size == current.size / 2 },
                                    ),
                                )
                            }
                            doomed = emptySet(); wiping = false
                            current.clear(); currentW.clear()
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
                    .semantics { contentDescription = context.getString(R.string.drawing_drawing_canvas) },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val v = view ?: return@Canvas
                    val live = if (current.isEmpty() || mode == Mode.LASSO || wiping) null
                    else PenStroke(
                        ink, strokePx / v.scale, current.toList(), erase = liveErase, tool = mode.tool,
                        weights = currentW.toList().takeIf { it.size == current.size / 2 },
                    )
                    translate(v.origin.x, v.origin.y) {
                        scale(v.scale, pivot = Offset.Zero) {
                            drawInk(strokes, inkOf, live, selection, lift, hidden = doomed)
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
                            .clickable(role = Role.Button) { view = fit }.semantics { contentDescription = context.getString(R.string.drawing_zoom_percent_tap_to_fit, (v.scale / fit.scale * 100).roundToInt()) }
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
                    IconAction(Ico.UNDO, stringResource(R.string.common_undo), {
                        future.add(strokes); strokes = past.removeAt(past.lastIndex); selection = emptySet(); changed = true
                    }, color = jc.ink, enabled = past.isNotEmpty())
                    IconAction(Ico.REDO, stringResource(R.string.drawing_redo), {
                        past.add(strokes); strokes = future.removeAt(future.lastIndex); selection = emptySet(); changed = true
                    }, color = jc.ink, enabled = future.isNotEmpty())
                }
                Spacer(Modifier.weight(1f))
                TOOLS.forEach { t ->
                    ToolPen(t, Color(ink), picked = mode == t.mode) { mode = t.mode; if (t.mode != Mode.LASSO) selection = emptySet() }
                }
                Spacer(Modifier.weight(1f))
                Column(Modifier.align(Alignment.CenterVertically)) {
                    IconAction(Ico.RULER, if (ruler) stringResource(R.string.drawing_ruler_on) else stringResource(R.string.drawing_ruler_off), { ruler = !ruler }, color = if (ruler) jc.accent else jc.muted)
                    PaperMenu(canvas?.paperStyle ?: Paper.PLAIN) { p -> canvas = canvas?.withPaper(p); changed = true }
                }
            }
            HorizontalDivider(color = jc.line)
            Row(
                Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WIDTHS.forEach { (name, w, dot) ->
                    val on = width == w
                    Box(
                        Modifier.size(48.dp).clip(CircleShape) // 48 dp touch area, 32 dp highlight
                            .clickable(role = Role.RadioButton) { width = w }.semantics { contentDescription = context.getString(R.string.drawing_stroke_desc, context.getString(name)); selected = on },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(32.dp).clip(CircleShape).background(if (on) jc.surface else Color.Transparent), contentAlignment = Alignment.Center) { Box(Modifier.size(dot).clip(CircleShape).background(if (mode == Mode.ERASER || mode == Mode.LASSO) jc.ink else inkOf(ink))) } }
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(1.dp, 28.dp).background(jc.line))
                Spacer(Modifier.width(6.dp))
                // The inks scroll sideways when the row is too narrow for them all.
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    // With the eraser, the inks give way to what it erases: the ink under it, or whole strokes.
                    if (mode == Mode.ERASER) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(stringResource(R.string.drawing_area), !eraseStrokes, { eraseStrokes = false })
                        Pill(stringResource(R.string.drawing_whole_strokes), eraseStrokes, { eraseStrokes = true })
                    }
                    else {
                        val scroll = rememberScrollState()
                        val custom = recentInks.filter { c -> INKS.none { it.first == c } }
                        // Keep the ink in use in view: the custom ones sit at the end, next to the colour button.
                        androidx.compose.runtime.LaunchedEffect(ink, custom.size) {
                            if (ink in custom) scroll.animateScrollTo(scroll.maxValue) else if (INKS.indexOfFirst { it.first == ink } < 3) scroll.animateScrollTo(0)
                        }
                        Row(Modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
                            (INKS.map { (v, r) -> v to stringResource(r) } + custom.reversed().map { it to stringResource(R.string.drawing_custom_colour, hexOf(it)) }).forEach { (value, name) ->
                                InkSwatch(inkOf(value), ink == value, if (selection.isNotEmpty()) stringResource(R.string.drawing_colour_selection, name) else name) { useInk(value) }
                            }
                        }
                    }
                }
                // Always in view, whatever the inks scroll to.
                if (mode != Mode.ERASER) Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { picking = true } // 48 dp touch area; the ring stays 24
                        .semantics { contentDescription = if (selection.isNotEmpty()) context.getString(R.string.drawing_colour_selection_more_colours) else context.getString(R.string.drawing_more_colours) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(24.dp).border(3.dp, Brush.sweepGradient(RAINBOW), CircleShape))
                    JIcon(Ico.PLUS, jc.ink, size = 12.dp, stroke = 2.4f)
                }
            }
        }
    }
}

private val RAINBOW = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/** One ink in the row: a dot, ringed when it is the ink in use. */
@Composable
private fun InkSwatch(color: Color, on: Boolean, name: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.RadioButton, onClick = onClick) // 48 dp touch area
            .semantics { contentDescription = name; selected = on },
        contentAlignment = Alignment.Center,
    ) {
        // Selected: the swatch gets a ring with a gap, as in the design.
        if (on) Box(Modifier.size(32.dp).border(2.dp, color, CircleShape))
        Box(Modifier.size(22.dp).clip(CircleShape).background(color))
    }
}

/**
 * Any ink colour: a square for how strong and how light, a bar for the hue, and the hex code to type one in.
 * Shown as the app's bottom sheet; [onPick] gets the colour as stored (not lightened for a dark page).
 */
@Composable
private fun InkPicker(start: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val first = remember { argbToHsv(start) }
    var h by remember { mutableStateOf(first[0]) }
    var sat by remember { mutableStateOf(first[1]) }
    var v by remember { mutableStateOf(first[2]) }
    var hex by remember { mutableStateOf(hexOf(start)) }
    val color = hsvToArgb(h, sat, v)
    fun moved() { hex = hexOf(hsvToArgb(h, sat, v)) }
    MarginDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onPick(color) }) { Text(stringResource(R.string.drawing_use_colour), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        title = { Text(stringResource(R.string.drawing_ink_colour)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Saturation left to right, brightness top to bottom.
                Box(
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(Color.White, Color.hsv(h, 1f, 1f))))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        .semantics { contentDescription = context.getString(R.string.drawing_strength_and_brightness) }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                fun at(p: Offset) {
                                    sat = (p.x / size.width).coerceIn(0f, 1f); v = (1 - p.y / size.height).coerceIn(0f, 1f); moved()
                                }
                                val down = awaitFirstDown(); at(down.position); down.consume()
                                drag(down.id) { at(it.position); it.consume() }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val c = Offset(sat * size.width, (1 - v) * size.height)
                            drawCircle(Color.Black.copy(alpha = 0.35f), 11.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                            drawCircle(Color.White, 10.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
                        },
                )
                Box(
                    Modifier.fillMaxWidth().height(28.dp).clip(CircleShape).background(Brush.horizontalGradient(RAINBOW))
                        .semantics { contentDescription = context.getString(R.string.drawing_hue) }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                fun at(p: Offset) { h = (p.x / size.width).coerceIn(0f, 1f) * 359.9f; moved() }
                                val down = awaitFirstDown(); at(down.position); down.consume()
                                drag(down.id) { at(it.position); it.consume() }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val c = Offset((h / 360f) * size.width, size.height / 2).let { it.copy(x = it.x.coerceIn(size.height / 2, size.width - size.height / 2)) }
                            drawCircle(Color.hsv(h, 1f, 1f), size.height / 2 - 3.dp.toPx(), c)
                            drawCircle(Color.White, size.height / 2 - 3.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                        },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(color)).border(1.dp, jc.line, CircleShape))
                    androidx.compose.material3.OutlinedTextField(
                        hex,
                        { t ->
                            hex = t.take(7)
                            parseHexColor(hex)?.let { c -> val (nh, ns, nv) = argbToHsv(c); h = nh; sat = ns; v = nv }
                        },
                        Modifier.weight(1f), singleLine = true, shape = JShape.sm, label = { Text(stringResource(R.string.drawing_hex)) },
                        isError = parseHexColor(hex) == null,
                    )
                }
            }
        },
    )
}

/** Over the canvas while the lasso holds strokes: turn them into text or delete them (drag the box to move them). */
@Composable
private fun SelectionActions(modifier: Modifier, onText: (() -> Unit)?, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    Row(modifier.padding(8.dp).clip(CircleShape).background(jc.ink.copy(alpha = 0.85f)).padding(horizontal = 4.dp)) {
        onText?.let { IconAction(Ico.HANDWRITING, stringResource(R.string.drawing_convert_selected_handwriting_to_text), it, color = jc.bg) }
        IconAction(Ico.COPY, stringResource(R.string.drawing_duplicate_selected_strokes), onDuplicate, color = jc.bg)
        IconAction(Ico.TRASH, stringResource(R.string.drawing_delete_selected_strokes), onDelete, color = jc.bg)
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
