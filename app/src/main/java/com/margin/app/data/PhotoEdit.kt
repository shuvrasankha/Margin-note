package com.margin.app.data

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A crop as fractions (0–1) of the photo after it is turned, flipped and straightened: [l] and [r] across,
 * [t] and [b] down.
 */
data class CropRect(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f) {
    val w get() = r - l
    val h get() = b - t
    val isFull get() = l <= 0.0005f && t <= 0.0005f && r >= 0.9995f && b >= 0.9995f

    /** The same area after the photo turns a quarter clockwise. */
    fun turned() = CropRect(1 - b, l, 1 - t, r)
    fun flippedH() = CropRect(1 - r, t, 1 - l, b)
    fun flippedV() = CropRect(l, 1 - b, r, 1 - t)

    companion object { val FULL = CropRect() }
}

/**
 * Everything the photo editor does, applied in this order: [turns] quarter turns clockwise, flips, a
 * [straighten] tilt in degrees (zoomed in so no empty corners show), the [crop], the colour changes (each -1 to
 * 1, 0 is unchanged), then [scale] for the size.
 */
data class PhotoEdit(
    val turns: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val straighten: Float = 0f,
    val crop: CropRect = CropRect.FULL,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val scale: Float = 1f,
) {
    val isIdentity get() = crop.isFull && copy(crop = CropRect.FULL) == PhotoEdit()
    val hasColor get() = brightness != 0f || contrast != 0f || saturation != 0f

    /** The photo turned a quarter clockwise, the crop going with it. */
    fun turnedRight() = copy(turns = (turns + 1) % 4, crop = crop.turned(), flipH = flipV, flipV = flipH)
    fun turnedLeft() = turnedRight().turnedRight().turnedRight()
    fun flippedH() = copy(flipH = !flipH, crop = crop.flippedH(), straighten = -straighten)
    fun flippedV() = copy(flipV = !flipV, crop = crop.flippedV(), straighten = -straighten)
}

/** Width and height after the quarter turns. */
fun turnedSize(w: Int, h: Int, turns: Int) = if (turns % 2 == 1) h to w else w to h

/**
 * How much a [w]×[h] picture must grow when tilted by [degrees] so it still fills its own frame: the corners
 * that swing in are covered.
 */
fun straightenScale(w: Int, h: Int, degrees: Float): Float {
    if (degrees == 0f || w <= 0 || h <= 0) return 1f
    val a = Math.toRadians(abs(degrees).toDouble())
    val c = cos(a); val s = sin(a)
    return max((w * c + h * s) / w, (w * s + h * c) / h).toFloat()
}

/** The saved photo's size in pixels for a [w]×[h] original. */
fun editedSize(w: Int, h: Int, e: PhotoEdit): Pair<Int, Int> {
    val (tw, th) = turnedSize(w, h, e.turns)
    val cw = (tw * e.crop.w * e.scale).roundToInt().coerceAtLeast(1)
    val ch = (th * e.crop.h * e.scale).roundToInt().coerceAtLeast(1)
    return cw to ch
}

/**
 * The 4×5 colour matrix (as Android's ColorMatrix takes it) for brightness, contrast and saturation, each -1
 * to 1. Brightness shifts by up to a third of the range, contrast stretches around the middle grey (up to 2×,
 * down to flat), saturation runs from grey (-1) to twice as vivid (1).
 */
fun photoColorMatrix(brightness: Float, contrast: Float, saturation: Float): FloatArray {
    val s = 1f + saturation.coerceIn(-1f, 1f)
    // Luminance weights (Rec. 709).
    val lr = 0.2126f; val lg = 0.7152f; val lb = 0.0722f
    val sat = floatArrayOf(
        lr * (1 - s) + s, lg * (1 - s), lb * (1 - s),
        lr * (1 - s), lg * (1 - s) + s, lb * (1 - s),
        lr * (1 - s), lg * (1 - s), lb * (1 - s) + s,
    )
    val c = if (contrast >= 0) 1f + contrast.coerceAtMost(1f) else 1f + contrast.coerceAtLeast(-1f) * 0.95f
    val shift = 128f * (1 - c) + brightness.coerceIn(-1f, 1f) * 85f
    val m = FloatArray(20)
    for (row in 0 until 3) {
        for (col in 0 until 3) m[row * 5 + col] = sat[row * 3 + col] * c
        m[row * 5 + 4] = shift
    }
    m[18] = 1f // alpha unchanged
    return m
}

/** Which part of the crop box a finger holds. */
enum class CropHandle { MOVE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * The crop after dragging [handle] by [dx], [dy] (fractions of the photo). [aspect] is width ÷ height as a
 * fraction of the photo's own shape (null for free), kept by corners; sides then do nothing. No side gets closer
 * to its opposite than [min], and the box stays on the photo.
 */
fun dragCrop(c: CropRect, handle: CropHandle, dx: Float, dy: Float, aspect: Float?, min: Float = 0.06f): CropRect {
    if (handle == CropHandle.MOVE) {
        val mx = dx.coerceIn(-c.l, 1 - c.r); val my = dy.coerceIn(-c.t, 1 - c.b)
        return CropRect(c.l + mx, c.t + my, c.r + mx, c.b + my)
    }
    var l = c.l; var t = c.t; var r = c.r; var b = c.b
    val left = handle in setOf(CropHandle.LEFT, CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT)
    val right = handle in setOf(CropHandle.RIGHT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT)
    val top = handle in setOf(CropHandle.TOP, CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT)
    val bottom = handle in setOf(CropHandle.BOTTOM, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT)
    if (aspect != null) {
        if (!(left || right) || !(top || bottom)) return c // only corners when the shape is fixed
        // The width follows the finger; the height follows the width; both shrink to fit the photo.
        var w = (if (right) c.w + dx else c.w - dx)
        val maxW = if (right) 1 - c.l else c.r
        val maxH = if (bottom) 1 - c.t else c.b
        w = w.coerceIn(min, maxW)
        var h = w / aspect
        if (h > maxH) { h = maxH; w = h * aspect }
        if (h < min) { h = min; w = (h * aspect).coerceAtMost(maxW) }
        if (right) r = c.l + w else l = c.r - w
        if (bottom) b = c.t + h else t = c.b - h
        return CropRect(l, t, r, b)
    }
    if (left) l = (c.l + dx).coerceIn(0f, c.r - min)
    if (right) r = (c.r + dx).coerceIn(c.l + min, 1f)
    if (top) t = (c.t + dy).coerceIn(0f, c.b - min)
    if (bottom) b = (c.b + dy).coerceIn(c.t + min, 1f)
    return CropRect(l, t, r, b)
}

/**
 * The biggest crop of shape [aspect] (width ÷ height, as a fraction of the photo's shape) centred on [c]'s
 * middle that fits on the photo; null [aspect] keeps [c].
 */
fun cropToAspect(c: CropRect, aspect: Float?): CropRect {
    if (aspect == null) return c
    var w = 1f; var h = w / aspect
    if (h > 1f) { h = 1f; w = h * aspect }
    val cx = ((c.l + c.r) / 2).coerceIn(w / 2, 1 - w / 2)
    val cy = ((c.t + c.b) / 2).coerceIn(h / 2, 1 - h / 2)
    return CropRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
}
