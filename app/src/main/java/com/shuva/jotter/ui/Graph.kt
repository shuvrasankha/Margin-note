package com.shuva.jotter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shuva.jotter.data.Plot
import com.shuva.jotter.data.formatNumber
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A colour per curve, in order; they repeat after the fifth. */
private val CURVES = listOf(Color(0xFFE8890C), Color(0xFF3B82F6), Color(0xFF22A06B), Color(0xFFE5487A), Color(0xFF8B5CF6))

/**
 * Every function in the note drawn on one grid. Drag to move around, pinch to zoom, double-tap to zoom in;
 * Reset goes back to -10…10 around the origin. [onAdd] (when the note can hold images) gets the graph as
 * it's shown, as a picture for the note.
 */
@Composable
fun GraphDialog(plots: List<Plot>, onDismiss: () -> Unit, onAdd: ((ImageBitmap) -> Unit)? = null) {
    // The view: the maths point at the middle of the canvas, and how many pixels one unit is.
    var cx by remember { mutableFloatStateOf(0f) }
    var cy by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableStateOf<Float?>(null) } // set from the canvas width on first draw
    val measurer = rememberTextMeasurer()
    val gridColor = jc.line
    val axisColor = jc.muted
    val labelStyle = JType.small.copy(color = jc.muted)
    val bg = jc.bg
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(bg).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextAction("Reset", { cx = 0f; cy = 0f; scale = null })
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("Graph", style = JType.rowTitle, color = jc.ink) }
                if (onAdd != null) TextAction("Add to note", {
                    val s = scale ?: return@TextAction
                    // The same part of the plane as on screen, stretched to the picture's shape.
                    onAdd(graphImage(plots, cx, cy, s * GRAPH_IMAGE_WIDTH / canvasSize.width, s * GRAPH_IMAGE_HEIGHT / canvasSize.height, measurer))
                }, enabled = scale != null && canvasSize.width > 0 && canvasSize.height > 0)
                TextAction("Done", onDismiss, bold = true)
            }
            Canvas(
                Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { canvasSize = it }
                    .semantics { contentDescription = "Graph of " + plots.joinToString(", ") { it.label } }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val s = scale ?: return@detectTransformGestures
                            // Keep the maths point under the fingers where it is while zooming.
                            val mx = cx + (centroid.x - size.width / 2f) / s
                            val my = cy - (centroid.y - size.height / 2f) / s
                            val ns = (s * zoom).coerceIn(1e-3f, 1e7f)
                            cx = mx - (centroid.x - size.width / 2f) / ns - pan.x / ns
                            cy = my + (centroid.y - size.height / 2f) / ns + pan.y / ns
                            scale = ns
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { p ->
                            val s = scale ?: return@detectTapGestures
                            cx += (p.x - size.width / 2f) / s / 2
                            cy -= (p.y - size.height / 2f) / s / 2
                            scale = s * 2
                        })
                    },
            ) {
                val s = scale ?: (size.width / 20f).also { scale = it }
                drawGrid(cx, cy, s, s, gridColor, axisColor, measurer, labelStyle)
                plots.forEachIndexed { i, p -> drawCurve(p, cx, cy, s, s, CURVES[i % CURVES.size]) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                plots.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(CURVES[i % CURVES.size]))
                        Text(p.label, Modifier.padding(start = 10.dp), style = JType.secondary, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private const val GRAPH_IMAGE_WIDTH = 1440
/** About as wide for its height as a picture card in a note, so little is cut off there. */
private const val GRAPH_IMAGE_HEIGHT = 640

/**
 * The graph as a picture: around ([cx], [cy]) with [sx] and [sy] pixels per unit across and up (so it can
 * show the same stretch of both axes as the screen did), with a key of the curves in the top left. Always
 * light, like paper, so it reads the same in either theme.
 */
fun graphImage(plots: List<Plot>, cx: Float, cy: Float, sx: Float, sy: Float, measurer: TextMeasurer): ImageBitmap {
    val w = GRAPH_IMAGE_WIDTH
    val h = GRAPH_IMAGE_HEIGHT
    val image = ImageBitmap(w, h)
    // Text and lines sized as on a phone about 360dp wide.
    val density = Density(w / 360f)
    val label = TextStyle(color = Color(0xFF6E6E73), fontSize = JType.small.fontSize)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w.toFloat(), h.toFloat())) {
        drawRect(Color.White)
        drawGrid(cx, cy, sx, sy, Color(0xFFE5E5EA), Color(0xFF8E8E93), measurer, label, yGap = 40.dp.toPx())
        plots.forEachIndexed { i, p -> drawCurve(p, cx, cy, sx, sy, CURVES[i % CURVES.size]) }
        // The key: a dot and the line as written, for each curve.
        val pad = 8.dp.toPx()
        val keys = plots.map { measurer.measure(it.label, TextStyle(color = Color(0xFF1C1C1E), fontSize = JType.small.fontSize), maxLines = 1) }
        val dot = 8.dp.toPx()
        val boxW = keys.maxOf { it.size.width } + dot + pad * 3
        val row = keys.maxOf { it.size.height } + 2.dp.toPx()
        inset(pad, pad, size.width - pad - boxW, size.height - pad - row * keys.size - pad) {
            drawRoundRect(Color.White.copy(alpha = 0.92f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            drawRoundRect(Color(0xFFE5E5EA), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()), style = Stroke(1f))
            keys.forEachIndexed { i, t ->
                val y = pad / 2 + row * i
                drawCircle(CURVES[i % CURVES.size], dot / 2, Offset(pad + dot / 2, y + row / 2))
                drawText(t, topLeft = Offset(pad * 2 + dot, y + (row - t.size.height) / 2))
            }
        }
    }
    return image
}

/** A round step (1, 2 or 5 × 10ⁿ) so grid lines are about [minPx] apart. */
private fun niceStep(minPx: Float, scale: Float): Double {
    val raw = minPx / scale.toDouble()
    val mag = 10.0.pow(floor(log10(raw)))
    return listOf(1.0, 2.0, 5.0, 10.0).first { it * mag >= raw } * mag
}

/** Grid lines about 72dp apart across and [yGap] apart up, numbered, and the axes. [sx], [sy]: pixels per unit. */
private fun DrawScope.drawGrid(
    cx: Float, cy: Float, sx: Float, sy: Float, grid: Color, axis: Color, measurer: TextMeasurer, style: TextStyle,
    yGap: Float = 72.dp.toPx(),
) {
    val w = size.width
    val h = size.height
    fun px(x: Double) = (w / 2 + (x - cx) * sx).toFloat()
    fun py(y: Double) = (h / 2 - (y - cy) * sy).toFloat()
    var step = niceStep(72.dp.toPx(), sx)
    val left = cx - w / 2 / sx
    val right = cx + w / 2 / sx
    val bottom = cy - h / 2 / sy
    val top = cy + h / 2 / sy
    val ax = px(0.0).coerceIn(0f, w) // where the y axis is, or the edge it's past
    val ay = py(0.0).coerceIn(0f, h)
    val pad = 4.dp.toPx()

    var k = ceil(left / step)
    while (k * step <= right) {
        val v = k * step
        val x = px(v)
        drawLine(grid, Offset(x, 0f), Offset(x, h), 1f)
        if (abs(v) > step / 2) {
            val t = measurer.measure(formatNumber(v, 6), style)
            val y = (ay + pad).coerceAtMost(h - t.size.height - pad)
            drawText(t, topLeft = Offset((x - t.size.width / 2f).coerceIn(pad, w - t.size.width - pad), y))
        }
        k++
    }
    step = niceStep(yGap, sy)
    k = ceil(bottom / step)
    while (k * step <= top) {
        val v = k * step
        val y = py(v)
        drawLine(grid, Offset(0f, y), Offset(w, y), 1f)
        if (abs(v) > step / 2) {
            val t = measurer.measure(formatNumber(v, 6), style)
            val x = (ax + pad).coerceAtMost(w - t.size.width - pad)
            drawText(t, topLeft = Offset(x, y - t.size.height / 2f))
        }
        k++
    }
    drawLine(axis, Offset(0f, ay), Offset(w, ay), 1.5.dp.toPx())
    drawLine(axis, Offset(ax, 0f), Offset(ax, h), 1.5.dp.toPx())
}

/** The curve, sampled every 2px; it breaks where the function isn't defined or shoots off (tan's asymptotes). */
private fun DrawScope.drawCurve(p: Plot, cx: Float, cy: Float, sx: Float, sy: Float, color: Color) {
    val w = size.width
    val h = size.height
    val path = Path()
    var drawing = false
    var lastY = 0f
    var px = 0f
    while (px <= w) {
        val x = cx + (px - w / 2) / sx
        val y = p.f(x.toDouble())
        val py = (h / 2 - (y - cy) * sy).toFloat()
        val ok = y.isFinite() && py in -h * 4..h * 5
        when {
            !ok -> drawing = false
            drawing && abs(py - lastY) > h -> path.moveTo(px, py) // a jump across an asymptote
            drawing -> path.lineTo(px, py)
            else -> { path.moveTo(px, py); drawing = true }
        }
        lastY = py
        px += 2f
    }
    drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
