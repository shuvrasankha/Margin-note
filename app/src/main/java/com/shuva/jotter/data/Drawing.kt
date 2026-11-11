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
