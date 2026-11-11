package com.shuva.jotter.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** Attachments with this type are Jotter drawings: a [Drawing] as JSON, editable again. */
const val DRAWING_MIME = "application/vnd.jotter.drawing+json"

val Attachment.isDrawing get() = mime == DRAWING_MIME

/**
 * Points are in the units of a [width] x [height] canvas (the one it was first drawn on). [grid] shows squared
 * paper behind the ink; older versions don't know the key and show plain paper.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Drawing(
    val width: Float,
    val height: Float,
    val strokes: List<PenStroke> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val grid: Boolean = false,
)

/** How a stroke is drawn. */
@Serializable
enum class PenTool { PEN, PENCIL, MARKER }

/**
 * [points] are x, y pairs. An [erase] stroke clears what's under it instead of drawing. [tool] is left out of
 * the JSON for the pen, so older versions read every stroke (they draw pencil and marker as pen).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PenStroke(
    val color: Long,
    val width: Float,
    val points: List<Float>,
    val erase: Boolean = false,
    // shortcut: a PenTool added later would fail to decode on older versions (enum); add values only with a version check.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tool: PenTool = PenTool.PEN,
)

/** Grid spacing in drawing units. */
const val GRID_STEP = 60f

/** True when (x, y) is inside the closed polygon [poly] (x, y pairs), by ray casting. */
fun pointInPolygon(x: Float, y: Float, poly: List<Float>): Boolean {
    val n = poly.size / 2
    if (n < 3) return false
    var inside = false
    var j = n - 1
    for (i in 0 until n) {
        val xi = poly[2 * i]; val yi = poly[2 * i + 1]
        val xj = poly[2 * j]; val yj = poly[2 * j + 1]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/** Lasso: the strokes with most of their points inside [loop]. */
fun Drawing.strokesInside(loop: List<Float>): Set<Int> = strokes.indices.filter { i ->
    val p = strokes[i].points
    val n = p.size / 2
    n > 0 && (0 until n).count { pointInPolygon(p[2 * it], p[2 * it + 1], loop) } * 2 > n
}.toSet()

/** The strokes at [indices] shifted by [dx], [dy]. */
fun List<PenStroke>.moved(indices: Set<Int>, dx: Float, dy: Float) = mapIndexed { i, s ->
    if (i !in indices) s else s.copy(points = s.points.mapIndexed { k, v -> if (k % 2 == 0) v + dx else v + dy })
}

/** Left, top, right, bottom around the points of the strokes at [indices]; null when there are none. */
fun List<PenStroke>.bounds(indices: Set<Int>): FloatArray? {
    val pts = indices.mapNotNull { getOrNull(it) }.flatMap { it.points }
    if (pts.size < 2) return null
    val xs = pts.filterIndexed { k, _ -> k % 2 == 0 }
    val ys = pts.filterIndexed { k, _ -> k % 2 == 1 }
    return floatArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
}

/**
 * Ruler: a straight line from the first point to the end point. Within [snapDegrees] of a multiple of 45° it
 * snaps to that angle, keeping its length.
 */
fun straightLine(x0: Float, y0: Float, x1: Float, y1: Float, snapDegrees: Double = 5.0): List<Float> {
    val dx = (x1 - x0).toDouble()
    val dy = (y1 - y0).toDouble()
    val len = kotlin.math.hypot(dx, dy)
    if (len == 0.0) return listOf(x0, y0)
    val angle = Math.toDegrees(kotlin.math.atan2(dy, dx))
    val nearest = Math.round(angle / 45.0) * 45.0
    if (kotlin.math.abs(angle - nearest) > snapDegrees) return listOf(x0, y0, x1, y1)
    val r = Math.toRadians(nearest)
    return listOf(x0, y0, (x0 + len * kotlin.math.cos(r)).toFloat(), (y0 + len * kotlin.math.sin(r)).toFloat())
}

/** Scale that fits the drawing inside a [w] x [h] area, keeping its shape. */
fun Drawing.fitScale(w: Float, h: Float) = if (width <= 0f || height <= 0f) 1f else minOf(w / width, h / height)

/** The same drawing cut down to its ink (plus [pad] around it), for previews. Unchanged when there's no ink. */
fun Drawing.cropped(pad: Float): Drawing {
    val ink = strokes.filterNot { it.erase }
    if (ink.isEmpty()) return this
    val half = ink.maxOf { it.width } / 2 + pad
    val xs = ink.flatMap { s -> s.points.filterIndexed { i, _ -> i % 2 == 0 } }
    val ys = ink.flatMap { s -> s.points.filterIndexed { i, _ -> i % 2 == 1 } }
    val left = xs.min() - half
    val top = ys.min() - half
    return copy(
        width = xs.max() + half - left, height = ys.max() + half - top,
        strokes = strokes.map { s -> s.copy(points = s.points.mapIndexed { i, v -> if (i % 2 == 0) v - left else v - top }) },
    )
}

/**
 * The same drawing cut down to the rows its ink covers (plus [pad] above and below), keeping its full width
 * so strokes stay where they were drawn across the page. A drawing with no ink keeps a strip [pad] * 2 tall.
 */
fun Drawing.inkRows(pad: Float): Drawing {
    val ink = strokes.filterNot { it.erase }
    if (ink.isEmpty()) return copy(height = minOf(height, pad * 2))
    val half = ink.maxOf { it.width } / 2 + pad
    val ys = ink.flatMap { s -> s.points.filterIndexed { i, _ -> i % 2 == 1 } }
    val top = (ys.min() - half).coerceAtLeast(0f)
    val bottom = (ys.max() + half).coerceAtMost(height)
    return copy(height = bottom - top, strokes = strokes.map { s -> s.copy(points = s.points.mapIndexed { i, v -> if (i % 2 == 1) v - top else v }) })
}

/**
 * The drawing's page grown (never shrunk) to hold all its strokes plus [pad], for the endless canvas: ink drawn
 * left of or above the page moves the page's corner there, and every point is shifted to match, so points
 * stay at 0 or more.
 */
fun Drawing.grownToFit(pad: Float): Drawing {
    val pts = strokes.filter { it.points.size >= 2 }
    if (pts.isEmpty()) return this
    val half = pts.maxOf { it.width } / 2 + pad
    var l = 0f; var t = 0f; var r = width; var b = height
    pts.forEach { s ->
        for (i in s.points.indices step 2) {
            l = minOf(l, s.points[i] - half); r = maxOf(r, s.points[i] + half)
            t = minOf(t, s.points[i + 1] - half); b = maxOf(b, s.points[i + 1] + half)
        }
    }
    if (l == 0f && t == 0f && r == width && b == height) return this
    return copy(
        width = r - l, height = b - t,
        strokes = if (l == 0f && t == 0f) strokes else strokes.map { s -> s.copy(points = s.points.mapIndexed { i, v -> if (i % 2 == 0) v - l else v - t }) },
    )
}

/**
 * Shape snapping: the clean shape a hand-drawn stroke ([p], x, y pairs) was meant to be, or null when it's
 * just ink. A nearly straight stroke becomes a line (snapped to 45° like the ruler); a closed one becomes an
 * ellipse (a circle when nearly round), or a triangle or four-sided shape (an upright rectangle when its sides
 * are close to level). Corners are given extra points along each side, so the smoothed drawing keeps them sharp.
 */
fun recognizeShape(p: List<Float>): List<Float>? {
    val n = p.size / 2
    if (n < 4) return null
    val xs = FloatArray(n) { p[2 * it] }
    val ys = FloatArray(n) { p[2 * it + 1] }
    val left = xs.min(); val right = xs.max(); val top = ys.min(); val bottom = ys.max()
    val w = right - left; val h = bottom - top
    val diag = kotlin.math.hypot(w, h)
    if (diag < 20f) return null
    var length = 0f
    for (i in 1 until n) length += kotlin.math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
    val gap = kotlin.math.hypot(xs[n - 1] - xs[0], ys[n - 1] - ys[0])
    if (gap >= 0.9f * length) return straightLine(xs[0], ys[0], xs[n - 1], ys[n - 1])
    if (gap > 0.25f * length) return null // an open curve: leave it as drawn

    // Closed: an ellipse when every point sits close to the one that fits its box.
    val cx = (left + right) / 2; val cy = (top + bottom) / 2
    val rx = w / 2; val ry = h / 2
    if (rx > 0f && ry > 0f) {
        val err = (0 until n).sumOf { i ->
            val dx = (xs[i] - cx) / rx; val dy = (ys[i] - cy) / ry
            kotlin.math.abs(kotlin.math.sqrt(dx * dx + dy * dy) - 1f).toDouble()
        } / n
        if (err < 0.09) {
            val round = minOf(rx, ry) / maxOf(rx, ry) > 0.85f
            val r = (rx + ry) / 2
            return ellipse(cx, cy, if (round) r else rx, if (round) r else ry)
        }
    }

    // Otherwise a polygon: keep the corners, drop points along the sides.
    // Its ends meet, so split it at the point furthest from the start and simplify each half.
    val pts = (0 until n).map { xs[it] to ys[it] }
    val far = pts.indices.maxBy { kotlin.math.hypot(pts[it].first - xs[0], pts[it].second - ys[0]) }
    val corners = (simplify(pts.subList(0, far + 1), 0.07f * diag) + simplify(pts.subList(far, n), 0.07f * diag).drop(1)).toMutableList()
    if (corners.size > 2 && kotlin.math.hypot(corners.last().first - corners[0].first, corners.last().second - corners[0].second) < 0.2f * diag) {
        corners.removeAt(corners.lastIndex)
    }
    // A stroke started partway along a side leaves a "corner" that's flat; drop flat ones, going round the loop.
    var changed = true
    while (changed && corners.size > 3) {
        changed = false
        for (i in corners.indices) {
            val a = corners[(i + corners.size - 1) % corners.size]; val b = corners[i]; val c = corners[(i + 1) % corners.size]
            if (distanceToLine(b, a, c) < 0.07f * diag) { corners.removeAt(i); changed = true; break }
        }
    }
    return when (corners.size) {
        3 -> outline(corners)
        4 -> {
            val level = corners.indices.all { i ->
                val a = corners[i]; val b = corners[(i + 1) % 4]
                val deg = Math.toDegrees(kotlin.math.atan2((b.second - a.second).toDouble(), (b.first - a.first).toDouble()))
                val off = ((deg % 90) + 90) % 90
                off < 12 || off > 78
            }
            if (level) outline(listOf(left to top, right to top, right to bottom, left to bottom)) else outline(corners)
        }
        else -> null
    }
}

private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, steps: Int = 72): List<Float> = (0..steps).flatMap { k ->
    val t = 2 * Math.PI * k / steps
    listOf(cx + rx * kotlin.math.cos(t).toFloat(), cy + ry * kotlin.math.sin(t).toFloat())
}

/** A closed outline through [corners], with points every few units along each side. */
private fun outline(corners: List<Pair<Float, Float>>, step: Float = 4f): List<Float> {
    val out = mutableListOf<Float>()
    for (i in corners.indices) {
        val (x0, y0) = corners[i]
        val (x1, y1) = corners[(i + 1) % corners.size]
        val parts = maxOf(1, (kotlin.math.hypot(x1 - x0, y1 - y0) / step).toInt())
        for (k in 0 until parts) { out += x0 + (x1 - x0) * k / parts; out += y0 + (y1 - y0) * k / parts }
    }
    out += corners[0].first; out += corners[0].second
    return out
}

private fun distanceToLine(p: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
    val dx = b.first - a.first; val dy = b.second - a.second
    val len = kotlin.math.hypot(dx, dy)
    if (len == 0f) return kotlin.math.hypot(p.first - a.first, p.second - a.second)
    return kotlin.math.abs(dy * (p.first - a.first) - dx * (p.second - a.second)) / len
}

/** Ramer–Douglas–Peucker: the points of [pts] that stay further than [eps] from the line through their neighbours. */
private fun simplify(pts: List<Pair<Float, Float>>, eps: Float): List<Pair<Float, Float>> {
    if (pts.size < 3) return pts
    var far = 0; var most = 0f
    for (i in 1 until pts.lastIndex) {
        val d = distanceToLine(pts[i], pts[0], pts.last())
        if (d > most) { most = d; far = i }
    }
    if (most <= eps) return listOf(pts[0], pts.last())
    return simplify(pts.subList(0, far + 1), eps).dropLast(1) + simplify(pts.subList(far, pts.size), eps)
}
