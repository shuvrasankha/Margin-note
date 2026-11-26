package com.margin.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.margin.app.data.CropHandle
import com.margin.app.data.CropRect
import com.margin.app.data.PhotoEdit
import com.margin.app.data.cropToAspect
import com.margin.app.data.decodeImage
import com.margin.app.data.dragCrop
import com.margin.app.data.editedSize
import com.margin.app.data.photoColorMatrix
import com.margin.app.data.renderPhotoEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import com.margin.app.R
import androidx.compose.ui.res.stringResource

private enum class PhotoTool(val label: String, @androidx.annotation.StringRes val labelRes: Int, val icon: String) {
    CROP("Crop", R.string.photo_tool_crop, Ico.CROP), ROTATE("Rotate", R.string.photo_tool_rotate, Ico.ROTATE), ADJUST("Adjust", R.string.photo_tool_adjust, Ico.SETTINGS), RESIZE("Resize", R.string.photo_tool_resize, Ico.RESIZE)
}

/** Crop shapes: width to height, or null for any shape. "Original" is the photo's own shape. */
private val CROP_SHAPES = listOf<Pair<String, Pair<Int, Int>?>>(
    "Free" to null, "Original" to (0 to 0), "Square" to (1 to 1), "3:4" to (3 to 4), "4:3" to (4 to 3), "9:16" to (9 to 16), "16:9" to (16 to 9),
)

private val SIZES = listOf("Original" to 1f, "75%" to 0.75f, "50%" to 0.5f, "25%" to 0.25f)

/**
 * The crop shape as [dragCrop] takes it: width ÷ height measured in fractions of a [w]×[h] photo, so a square
 * on a 2:1 photo is 0.5. Null is any shape.
 */
private fun shapeOnPhoto(shape: Pair<Int, Int>?, w: Int, h: Int): Float? = when {
    shape == null || w <= 0 || h <= 0 -> null
    shape.first == 0 -> 1f
    else -> (shape.first.toFloat() / shape.second) / (w.toFloat() / h)
}

/** Where a [w]×[h] picture sits, as big as fits, in the middle of [area] less [pad] each side. */
private fun fitRect(area: IntSize, w: Float, h: Float, pad: Float): Rect {
    if (w <= 0 || h <= 0 || area.width <= 0) return Rect.Zero
    val s = min((area.width - 2 * pad) / w, (area.height - 2 * pad) / h)
    val left = (area.width - w * s) / 2; val top = (area.height - h * s) / 2
    return Rect(left, top, left + w * s, top + h * s)
}

/** The part of the crop box at [p] (on screen), allowing [slop] around its edges; corners win. */
private fun handleAt(p: Offset, box: Rect, slop: Float): CropHandle? {
    if (p.x < box.left - slop || p.x > box.right + slop || p.y < box.top - slop || p.y > box.bottom + slop) return null
    val l = abs(p.x - box.left) < slop; val r = abs(p.x - box.right) < slop
    val t = abs(p.y - box.top) < slop; val b = abs(p.y - box.bottom) < slop
    return when {
        l && t -> CropHandle.TOP_LEFT
        r && t -> CropHandle.TOP_RIGHT
        l && b -> CropHandle.BOTTOM_LEFT
        r && b -> CropHandle.BOTTOM_RIGHT
        l -> CropHandle.LEFT
        r -> CropHandle.RIGHT
        t -> CropHandle.TOP
        b -> CropHandle.BOTTOM
        box.contains(p) -> CropHandle.MOVE
        else -> null
    }
}

/** The photo at [file] and the size it will be saved at, before any edit (upright, and capped like saving). */
private class EditSource(val bitmap: Bitmap, val width: Int, val height: Int)

private fun loadSource(file: File): EditSource? {
    val preview = decodeImage(file, 1440) ?: return null
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.path, bounds)
    var w = bounds.outWidth.takeIf { it > 0 } ?: preview.width
    var h = bounds.outHeight.takeIf { it > 0 } ?: preview.height
    // The file's own size ignores its turn; the decoded preview is upright.
    if ((w > h) != (preview.width > preview.height) && w != h) { val x = w; w = h; h = x }
    val sample = (maxOf(w, h) / 4096).coerceAtLeast(1) // as saving reads it
    return EditSource(preview, w / sample, h / sample)
}

/**
 * Full-screen photo editor: crop (any shape or a fixed one), turn, flip and straighten, brightness, contrast
 * and saturation, and a smaller size. Nothing changes until Save, which hands the edit to [onSave] with a
 * callback to close once it is stored.
 */
@Composable
internal fun PhotoEditor(file: File, name: String, onCancel: () -> Unit, onSave: (PhotoEdit, done: () -> Unit) -> Unit) {
    var edit by remember { mutableStateOf(PhotoEdit()) }
    var tool by remember { mutableStateOf(PhotoTool.CROP) }
    var shape by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<EditSource?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(file) {
        val s = withContext(Dispatchers.IO) { runCatching { loadSource(file) }.getOrNull() }
        if (s == null) failed = true else source = s
    }
    // The photo turned, flipped and straightened, uncropped; colours are applied as it is drawn.
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(source, edit.turns, edit.flipH, edit.flipV, edit.straighten) {
        val src = source ?: return@LaunchedEffect
        val shaped = PhotoEdit(turns = edit.turns, flipH = edit.flipH, flipV = edit.flipV, straighten = edit.straighten)
        preview = withContext(Dispatchers.Default) { renderPhotoEdit(src.bitmap, shaped, withCrop = false, withColor = false).asImageBitmap() }
    }

    Dialog(onDismissRequest = { if (!saving) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        // The whole screen, so a drag near the edge reaches the crop box; light bar icons on black.
        val view = androidx.compose.ui.platform.LocalView.current
        LaunchedEffect(view) {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.apply {
                setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                androidx.core.view.WindowCompat.getInsetsController(this, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction(stringResource(R.string.common_cancel), onCancel, color = Color.White, enabled = !saving)
                Text(stringResource(R.string.photo_editor_edit_photo), Modifier.weight(1f), style = JType.rowTitle, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextAction(stringResource(R.string.common_reset), { edit = PhotoEdit(); shape = null }, color = Color.White, enabled = !saving && !edit.isIdentity)
                if (saving) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = jc.accent, strokeWidth = 2.dp)
                } else TextAction(stringResource(R.string.common_save), {
                    saving = true
                    onSave(edit) { saving = false }
                }, bold = true, enabled = source != null)
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = preview
                if (image != null) PhotoCanvas(image, edit, tool == PhotoTool.CROP, shapeOnPhoto(shape, image.width, image.height)) { edit = edit.copy(crop = it) }
                else if (failed) Text(stringResource(R.string.photo_editor_couldnt_open_the_photo), color = Color.White, style = JType.secondary)
                else CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
            }

            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 4.dp)) {
                Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                    val img = preview
                    when (tool) {
                        PhotoTool.CROP -> PillRow {
                            CROP_SHAPES.forEach { (label, s) ->
                                DarkPill(pillLabel(label), shape == s) {
                                    shape = s
                                    if (img != null) edit = edit.copy(crop = cropToAspect(if (s == null) edit.crop else CropRect.FULL, shapeOnPhoto(s, img.width, img.height)))
                                }
                            }
                        }
                        PhotoTool.ROTATE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                // A fixed crop shape is fitted again after a turn, since the photo's shape swaps.
                                fun turned(e: PhotoEdit): PhotoEdit {
                                    val w = img?.height ?: return e; val h = img.width
                                    return if (shape == null) e else e.copy(crop = cropToAspect(CropRect.FULL, shapeOnPhoto(shape, w, h)))
                                }
                                IconAction(Ico.ROTATE_LEFT, stringResource(R.string.photo_editor_turn_left), { edit = turned(edit.turnedLeft()) }, color = Color.White)
                                IconAction(Ico.ROTATE, stringResource(R.string.photo_editor_turn_right), { edit = turned(edit.turnedRight()) }, color = Color.White)
                                IconAction(Ico.FLIP_H, stringResource(R.string.photo_editor_flip_left_to_right), { edit = edit.flippedH() }, color = Color.White)
                                IconAction(Ico.FLIP_V, stringResource(R.string.photo_editor_flip_upside_down), { edit = edit.flippedV() }, color = Color.White)
                            }
                            EditSlider(stringResource(R.string.photo_editor_straighten), "${edit.straighten.roundToInt()}°", edit.straighten, -45f..45f, { edit = edit.copy(straighten = (it * 2).roundToInt() / 2f) }) {
                                edit = edit.copy(straighten = 0f)
                            }
                        }
                        PhotoTool.ADJUST -> {
                            var which by remember { mutableStateOf(0) }
                            val names = listOf(stringResource(R.string.photo_editor_brightness), stringResource(R.string.photo_editor_contrast), stringResource(R.string.photo_editor_saturation))
                            val values = listOf(edit.brightness, edit.contrast, edit.saturation)
                            fun set(i: Int, v: Float) {
                                edit = when (i) { 0 -> edit.copy(brightness = v); 1 -> edit.copy(contrast = v); else -> edit.copy(saturation = v) }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PillRow { names.forEachIndexed { i, n -> DarkPill(n, which == i) { which = i } } }
                                val v = values[which]
                                EditSlider(names[which], "${if (v > 0.005f) "+" else ""}${(v * 100).roundToInt()}", v, -1f..1f, { set(which, (it * 100).roundToInt() / 100f) }) { set(which, 0f) }
                            }
                        }
                        PhotoTool.RESIZE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PillRow { SIZES.forEach { (label, s) -> DarkPill(pillLabel(label), edit.scale == s) { edit = edit.copy(scale = s) } } }
                            Spacer(Modifier.height(12.dp))
                            source?.let { s ->
                                val (w, h) = editedSize(s.width, s.height, edit)
                                Text(stringResource(R.string.photo_size_px, w.toString(), h.toString()), style = JType.secondary, color = Color.White.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    PhotoTool.entries.forEach { t -> ToolTab(t, tool == t) { tool = t } }
                }
            }
        }
    }
}

/**
 * The photo, as big as fits. While cropping, the whole photo with the crop box over it: drag a corner or side
 * to resize the box, or inside it to move it. Otherwise just the cropped part.
 */
@Composable
private fun PhotoCanvas(image: ImageBitmap, edit: PhotoEdit, cropping: Boolean, aspect: Float?, onCrop: (CropRect) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var area by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val pad = with(density) { 24.dp.toPx() }
    val slop = with(density) { 28.dp.toPx() }
    val pw = image.width.toFloat(); val ph = image.height.toFloat()
    val crop = edit.crop
    val shown = if (cropping) fitRect(area, pw, ph, pad) else fitRect(area, pw * crop.w, ph * crop.h, pad)
    val filter = if (edit.hasColor) ColorFilter.colorMatrix(ColorMatrix(photoColorMatrix(edit.brightness, edit.contrast, edit.saturation))) else null
    val now by rememberUpdatedState(crop)
    val shape by rememberUpdatedState(aspect)
    val photo by rememberUpdatedState(shown)
    val accent = jc.accent

    Canvas(
        Modifier.fillMaxSize().onSizeChanged { area = it }
            .semantics { contentDescription = if (cropping) context.getString(R.string.photo_editor_photo_with_crop_box_drag) else context.getString(R.string.photo_editor_photo) }
            // The left handles sit near the screen edge, where a drag would otherwise be the Back gesture.
            .then(if (!cropping) Modifier else Modifier.systemGestureExclusion { Rect(0f, photo.top, it.size.width.toFloat(), photo.bottom) }.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val img = photo
                    val c = now
                    val box = Rect(img.left + c.l * img.width, img.top + c.t * img.height, img.left + c.r * img.width, img.top + c.b * img.height)
                    val handle = handleAt(down.position, box, slop) ?: return@awaitEachGesture
                    down.consume()
                    var current = c
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val d = ch.positionChange()
                        if (d != Offset.Zero && img.width > 0) {
                            current = dragCrop(current, handle, d.x / img.width, d.y / img.height, shape)
                            onCrop(current)
                        }
                        ch.consume()
                    }
                }
            }),
    ) {
        if (shown == Rect.Zero) return@Canvas
        if (cropping) {
            drawImage(image, dstOffset = IntOffset(shown.left.roundToInt(), shown.top.roundToInt()), dstSize = IntSize(shown.width.roundToInt(), shown.height.roundToInt()), colorFilter = filter)
            val box = Rect(shown.left + crop.l * shown.width, shown.top + crop.t * shown.height, shown.left + crop.r * shown.width, shown.top + crop.b * shown.height)
            val dim = Color.Black.copy(alpha = 0.6f)
            drawRect(dim, Offset(shown.left, shown.top), Size(shown.width, box.top - shown.top))
            drawRect(dim, Offset(shown.left, box.bottom), Size(shown.width, shown.bottom - box.bottom))
            drawRect(dim, Offset(shown.left, box.top), Size(box.left - shown.left, box.height))
            drawRect(dim, Offset(box.right, box.top), Size(shown.right - box.right, box.height))
            val thin = Color.White.copy(alpha = 0.45f)
            for (i in 1..2) {
                val x = box.left + box.width * i / 3; val y = box.top + box.height * i / 3
                drawLine(thin, Offset(x, box.top), Offset(x, box.bottom), 1.dp.toPx())
                drawLine(thin, Offset(box.left, y), Offset(box.right, y), 1.dp.toPx())
            }
            drawRect(Color.White, box.topLeft, box.size, style = Stroke(1.5.dp.toPx()))
            // Corner brackets.
            val len = min(18.dp.toPx(), min(box.width, box.height) / 3); val w = 4.dp.toPx()
            listOf(box.topLeft to Offset(1f, 1f), box.topRight to Offset(-1f, 1f), box.bottomLeft to Offset(1f, -1f), box.bottomRight to Offset(-1f, -1f)).forEach { (p, dir) ->
                drawLine(accent, p, p + Offset(len * dir.x, 0f), w)
                drawLine(accent, p, p + Offset(0f, len * dir.y), w)
            }
        } else {
            drawImage(
                image,
                srcOffset = IntOffset((crop.l * pw).roundToInt(), (crop.t * ph).roundToInt()),
                srcSize = IntSize((crop.w * pw).roundToInt().coerceAtLeast(1), (crop.h * ph).roundToInt().coerceAtLeast(1)),
                dstOffset = IntOffset(shown.left.roundToInt(), shown.top.roundToInt()),
                dstSize = IntSize(shown.width.roundToInt(), shown.height.roundToInt()),
                colorFilter = filter,
            )
        }
    }
}

@Composable
private fun PillRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) { content() }
}

@Composable
private fun DarkPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.background(if (selected) Color.White else Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, style = JType.small, color = if (selected) Color.Black else Color.White) }
}

/** A slider from [range] with its [name] and [shown] value; tapping the value sets it back with [onReset]. */
@Composable
private fun EditSlider(name: String, shown: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onReset: () -> Unit) {
    val sliderDesc = stringResource(R.string.photo_slider_desc, name, shown)
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = JType.small, color = Color.White.copy(alpha = 0.75f))
            Text(shown, Modifier.clickable(role = Role.Button, onClick = onReset).padding(horizontal = 6.dp, vertical = 2.dp)
                .semantics { contentDescription = sliderDesc }, style = JType.small, color = Color.White)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.fillMaxWidth().height(32.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = jc.accent, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
        )
    }
}

@Composable
private fun ToolTab(t: PhotoTool, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) jc.accent else Color.White.copy(alpha = 0.75f)
    Column(
        Modifier.width(72.dp).clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JIcon(t.icon, color)
        Spacer(Modifier.height(3.dp))
        Text(stringResource(t.labelRes), style = JType.small, color = color)
    }
}

/** A crop or size pill's label in the app's language; ratios and percentages stay as they are. */
@Composable
private fun pillLabel(key: String): String = when (key) {
    "Free" -> stringResource(R.string.photo_shape_free)
    "Original" -> stringResource(R.string.photo_shape_original)
    "Square" -> stringResource(R.string.photo_shape_square)
    else -> key
}
