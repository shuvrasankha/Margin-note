package com.margin.app.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.roundToInt

/**
 * [src] with [e] applied, as a new bitmap: turned, flipped, straightened, cropped, recoloured (unless
 * [withColor] is false, for a preview that recolours as it draws) and resized. [withCrop] false keeps the whole
 * photo, for the crop screen.
 */
fun renderPhotoEdit(src: Bitmap, e: PhotoEdit, withCrop: Boolean = true, withColor: Boolean = true): Bitmap {
    val (tw, th) = turnedSize(src.width, src.height, e.turns)
    val crop = if (withCrop) e.crop else CropRect.FULL
    val scale = if (withCrop) e.scale else 1f
    val outW = (tw * crop.w * scale).roundToInt().coerceAtLeast(1)
    val outH = (th * crop.h * scale).roundToInt().coerceAtLeast(1)
    val zoom = straightenScale(tw, th, e.straighten)
    val m = Matrix().apply {
        postTranslate(-src.width / 2f, -src.height / 2f)
        postRotate(90f * e.turns)
        postScale(if (e.flipH) -1f else 1f, if (e.flipV) -1f else 1f)
        postRotate(e.straighten)
        postScale(zoom, zoom)
        postTranslate(tw / 2f, th / 2f)
        postTranslate(-crop.l * tw, -crop.t * th)
        postScale(outW / (tw * crop.w), outH / (th * crop.h))
    }
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    if (withColor && e.hasColor) paint.colorFilter = ColorMatrixColorFilter(photoColorMatrix(e.brightness, e.contrast, e.saturation))
    Canvas(out).drawBitmap(src, m, paint)
    return out
}
