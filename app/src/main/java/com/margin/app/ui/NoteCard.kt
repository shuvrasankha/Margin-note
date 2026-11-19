package com.margin.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.margin.app.R
import com.margin.app.data.Block
import com.margin.app.data.shareLines

private const val CARD_W = 1080

/** The card's shape. Portrait 4:5 is what most feeds show uncropped; Story fills a phone screen. */
enum class CardSize(val label: String, val h: Int) {
    PORTRAIT("Portrait", 1350), SQUARE("Square", 1080), STORY("Story", 1920);
    val ratio get() = CARD_W / h.toFloat()
}

/** The card's lettering: Inter (the app's), or one of the phone's own serif, mono or casual faces. */
enum class CardFont(val label: String) {
    SANS("Sans"), SERIF("Serif"), MONO("Mono"), HAND("Casual");
    fun typeface(context: Context, bold: Boolean): android.graphics.Typeface = when (this) {
        SANS -> ResourcesCompat.getFont(context, if (bold) R.font.inter_bold else R.font.inter_regular) ?: android.graphics.Typeface.DEFAULT
        SERIF -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        MONO -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        HAND -> android.graphics.Typeface.create("casual", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
}

/** How a card looks: [foot] off leaves out the hairline, the Margin mark and the date. */
data class CardOptions(val style: CardStyle = CardStyle.PAPER, val size: CardSize = CardSize.PORTRAIT, val font: CardFont = CardFont.SANS, val foot: Boolean = true)

/** The text area of a card: from the top margin down to above the foot (or a matching margin when there's none). */
private fun CardOptions.textBottom() = size.h - if (foot) 130f else 120f

/**
 * A look for the card: the background (one colour, or two for a diagonal gradient), the text colours, the
 * margin rule, the foot's hairline, and optional paper: a dot grid, a square grid, or ruled lines that the
 * text sits on.
 */
enum class CardStyle(
    val label: String, val bg: Long, val bg2: Long? = null, val ink: Long, val body: Long, val muted: Long, val rule: Long,
    val hair: Long, val dots: Long? = null, val grid: Long? = null, val lines: Long? = null,
) {
    PAPER("Paper", 0xFFFAF7F0, ink = 0xFF16161A, body = 0xFF3A3934, muted = 0xFF8A877F, rule = 0xFFFFB340, hair = 0x1F000000),
    RULED("Ruled", 0xFFFFFFFF, ink = 0xFF16161A, body = 0xFF2C2C30, muted = 0xFF8E8E93, rule = 0xFFE5484D, hair = 0x1F000000, lines = 0xFFBFD4EC),
    LEGAL("Legal pad", 0xFFFFF3A6, ink = 0xFF1E1A05, body = 0xFF332D0E, muted = 0xFF7A6F3A, rule = 0xFFE5484D, hair = 0x26000000, lines = 0xFF9FC1E4),
    GRID("Grid", 0xFFFFFFFF, ink = 0xFF16161A, body = 0xFF2C2C30, muted = 0xFF8E8E93, rule = 0xFFFFB340, hair = 0x1F000000, grid = 0xFFE3E7ED),
    NIGHT("Night", 0xFF111113, ink = 0xFFF5F5F7, body = 0xFFC9C9CF, muted = 0xFF8E8E93, rule = 0xFFFFB340, hair = 0x26FFFFFF),
    DOTS("Dots", 0xFFFFFFFF, ink = 0xFF16161A, body = 0xFF3A3A3C, muted = 0xFF8E8E93, rule = 0xFF16161A, hair = 0x1F000000, dots = 0xFFD8D8DD),
    AMBER("Amber", 0xFFFFCC66, 0xFFFF9500, ink = 0xFF1A1205, body = 0xFF3B2A0A, muted = 0xFF6B4A12, rule = 0xFF1A1205, hair = 0x33000000),
    OCEAN("Ocean", 0xFF0A4FB5, 0xFF36B5E8, ink = 0xFFFFFFFF, body = 0xFFE6F2FF, muted = 0xFFBFDDF7, rule = 0xCCFFFFFF, hair = 0x40FFFFFF),
    ROSE("Rose", 0xFFFFD1DC, 0xFFB794F6, ink = 0xFF2A1033, body = 0xFF47284F, muted = 0xFF7A5A82, rule = 0xFF2A1033, hair = 0x33000000),
    KRAFT("Kraft", 0xFFD9B98C, ink = 0xFF2B1D0E, body = 0xFF3D2A15, muted = 0xFF6E5233, rule = 0xFF8A3B12, hair = 0x33000000, dots = 0x1A2B1D0E),
    BLUEPRINT("Blueprint", 0xFF1D4E89, ink = 0xFFFFFFFF, body = 0xFFDCE9F7, muted = 0xFF9DBBDD, rule = 0xFFFFFFFF, hair = 0x40FFFFFF, grid = 0x26FFFFFF),
    CHALK("Chalkboard", 0xFF23332B, ink = 0xFFF4F1E8, body = 0xFFD9D5C9, muted = 0xFF9BA399, rule = 0xFFF2C14E, hair = 0x33FFFFFF),
    SAGE("Sage", 0xFFE4EBDD, ink = 0xFF1C2A1C, body = 0xFF34442F, muted = 0xFF6B7A63, rule = 0xFF5B7F4E, hair = 0x1F000000, lines = 0xFFC3D2B8),
    MINT("Mint", 0xFFB8F2D9, 0xFF5AC8C8, ink = 0xFF0B2A26, body = 0xFF173F39, muted = 0xFF3F6B64, rule = 0xFF0B2A26, hair = 0x33000000),
    SUNSET("Sunset", 0xFFFF7E5F, 0xFFC9406A, ink = 0xFFFFFFFF, body = 0xFFFFEDE6, muted = 0xFFFFD0C2, rule = 0xCCFFFFFF, hair = 0x40FFFFFF),
    MIDNIGHT("Midnight", 0xFF141E30, 0xFF35577D, ink = 0xFFF5F7FA, body = 0xFFD3DCE8, muted = 0xFF93A4BA, rule = 0xFFFFB340, hair = 0x33FFFFFF, dots = 0x1FFFFFFF),
    MONO("Mono", 0xFF000000, ink = 0xFFFFFFFF, body = 0xFFE5E5E5, muted = 0xFF8E8E93, rule = 0xFFFFFFFF, hair = 0x33FFFFFF),
}

/** The style row's sections, in order. */
private val CARD_GROUPS = listOf(
    "Paper" to listOf(CardStyle.PAPER, CardStyle.RULED, CardStyle.LEGAL, CardStyle.GRID, CardStyle.DOTS, CardStyle.KRAFT, CardStyle.SAGE),
    "Colour" to listOf(CardStyle.AMBER, CardStyle.OCEAN, CardStyle.ROSE, CardStyle.MINT, CardStyle.SUNSET),
    "Dark" to listOf(CardStyle.NIGHT, CardStyle.BLUEPRINT, CardStyle.CHALK, CardStyle.MIDNIGHT, CardStyle.MONO),
).let { g -> g + listOfNotNull(("More" to CardStyle.entries.filter { s -> g.none { s in it.second } }).takeIf { it.second.isNotEmpty() }) }

private const val CARD_PREFS = "share_card"
private const val LAST_STYLE = "style"
private const val LAST_SIZE = "size"
private const val LAST_FONT = "font"
private const val SHOW_FOOT = "foot"

/** A dark card: the Margin mark gets a hairline edge so it doesn't melt into the background. */
private val CardStyle.isDark get() = androidx.core.graphics.ColorUtils.calculateLuminance(bg.toInt()) < 0.2

/**
 * The note as pictures for social media: on a warm paper card with the app's amber margin rule, the title,
 * the text, and the Margin mark at the foot. A note that fits is one card; a longer one runs on over as many
 * cards as it needs, each foot numbered. Each entry draws its card when called, so a long note isn't held
 * in memory all at once. Photos, drawings and other attachments are left out; tables come as plain text.
 */
fun noteCards(context: Context, blocks: List<Block>, date: String, options: CardOptions = CardOptions()): List<() -> Bitmap> {
    val style = options.style
    val bold = options.font.typeface(context, true)
    val regular = options.font.typeface(context, false)
    val width = (CARD_W - 96 - 150)
    fun layout(text: CharSequence, paint: TextPaint, maxLines: Int, spacing: Float) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setLineSpacing(0f, spacing).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
    fun paints(size: Float) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = style.ink.toInt(); textSize = size * 1.35f; typeface = bold; letterSpacing = if (options.font == CardFont.SANS) -0.02f else 0f } to
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = style.body.toInt(); textSize = size; typeface = regular }
    // A long title steps down in size to fit three lines; only past that is it cut short.
    fun titleLayout(title: String, paint: TextPaint): StaticLayout {
        val full = paint.textSize
        for (f in listOf(1f, 0.86f, 0.74f, 0.64f)) {
            paint.textSize = full * f
            val l = layout(title, paint, Int.MAX_VALUE, 1.1f)
            if (l.lineCount <= 3) return l
        }
        return layout(title, paint, 4, 1.1f)
    }
    val room = (options.textBottom() - CARD_TOP).toInt()

    val title = blocks.firstOrNull()?.text?.trim().orEmpty().ifEmpty { "Untitled" }
    val (body, ai) = cardBody(blocks.drop(1).filter { it.attachment == null }, style.rule.toInt())
    // A short note is set large and centred, like a quote; a long one steps down in size until it fits.
    for (size in listOf(64f, 56f, 50f, 44f, 40f)) {
        val (titlePaint, bodyPaint) = paints(size)
        val t = titleLayout(title, titlePaint)
        if (body.isEmpty()) return listOf { drawCard(context, options, date, t, null, 0, 0, center = true, ai) }
        val b = layout(body, bodyPaint, Int.MAX_VALUE, 1.4f)
        if (b.getLineBottom(b.lineCount - 1) <= room - t.height - 48f)
            return listOf { drawCard(context, options, date, t, b, 0, b.lineCount, center = true, ai) }
    }
    // Still too long: keep a readable size and run the text on over more cards, the title on the first only.
    val (titlePaint, bodyPaint) = paints(44f)
    val t = titleLayout(title, titlePaint)
    val b = layout(body, bodyPaint, Int.MAX_VALUE, 1.4f)
    // A card ends at a paragraph's end when one is near enough, so a lone line doesn't start the next card.
    val paraEnd = { line: Int -> line == b.lineCount - 1 || body.getOrNull(b.getLineEnd(line) - 1) == '\n' }
    val pages = cardPages(b.lineCount, b::getLineTop, b::getLineBottom, room - t.height - 48, room, paraEnd)
    return pages.mapIndexed { i, lines ->
        { drawCard(context, options, "$date  ·  ${i + 1}/${pages.size}", t.takeIf { i == 0 }, b, lines.first, lines.last + 1, center = false, ai) }
    }
}

/**
 * The note's text for a card, and the character ranges of its AI cards. Each AI card gets a "✦ AI" badge in
 * [badge]'s colour; blank lines at either end are left out.
 */
internal fun cardBody(blocks: List<Block>, badge: Int): Pair<CharSequence, List<IntRange>> {
    val lines = shareLines(blocks)
    val keep = lines.indices.filter { lines[it].isNotBlank() || blocks[it].ai }
    if (keep.isEmpty()) return "" to emptyList()
    val out = android.text.SpannableStringBuilder()
    val ranges = mutableListOf<IntRange>()
    var runStart = -1
    for (k in keep.first()..keep.last()) {
        if (k > keep.first()) out.append('\n')
        val ai = blocks[k].ai
        if (ai && runStart < 0) {
            runStart = out.length
            out.append("✦ AI")
            out.setSpan(android.text.style.ForegroundColorSpan(badge), runStart, out.length, 0)
            out.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), runStart, out.length, 0)
            out.setSpan(android.text.style.RelativeSizeSpan(0.7f), runStart, out.length, 0)
            if (lines[k].isNotEmpty()) out.append("  ") // an old card: text on the badge's line
        }
        out.append(lines[k])
        if (runStart >= 0 && blocks.getOrNull(k + 1)?.ai != true || runStart >= 0 && k == keep.last()) {
            ranges += runStart until out.length
            runStart = -1
        }
    }
    return out to ranges
}

/** Splits [count] lines into runs that fit [firstRoom] px on the first card and [room] on the rest. */
internal fun cardPages(count: Int, top: (Int) -> Int, bottom: (Int) -> Int, firstRoom: Int, room: Int, paragraphEnd: (Int) -> Boolean = { false }): List<IntRange> {
    val pages = mutableListOf<IntRange>()
    var start = 0
    while (start < count) {
        val fits = if (pages.isEmpty()) firstRoom else room
        var end = start + 1 // at least one line a card, so a line taller than a card can't loop forever
        while (end < count && bottom(end) - top(start) <= fits) end++
        // More to come: end at the last paragraph end instead, if that still fills at least two thirds of the card.
        if (end < count) (end - 1 downTo start + 1).firstOrNull { paragraphEnd(it) && bottom(it) - top(start) >= fits * 2 / 3 }?.let { end = it + 1 }
        pages += start until end
        start = end
    }
    return pages
}

private const val CARD_TOP = 120f

/** One card: the paper, the [title] (if any) and lines [from] until [to] of [body], and the foot with [footNote]. */
private fun drawCard(context: Context, options: CardOptions, footNote: String, title: StaticLayout?, body: StaticLayout?, from: Int, to: Int, center: Boolean, ai: List<IntRange> = emptyList()): Bitmap {
    val style = options.style
    val CARD_H = options.size.h
    val CARD_BOTTOM = options.textBottom()
    // The foot's hairline, the same on every style: paper (dots, grid, ruled lines, the margin rule) stops above it.
    // With no foot, the paper runs to the edge.
    val FOOT_LINE = if (options.foot) CARD_H - 112f else CARD_H.toFloat()
    val bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    fun font(id: Int) = ResourcesCompat.getFont(context, id)
    val ink = style.ink.toInt()
    val muted = style.muted.toInt()
    val bg = Paint()
    style.bg2?.let { bg.shader = LinearGradient(0f, 0f, CARD_W.toFloat(), CARD_H.toFloat(), style.bg.toInt(), it.toInt(), Shader.TileMode.CLAMP) }
        ?: run { bg.color = style.bg.toInt() }
    c.drawRect(0f, 0f, CARD_W.toFloat(), CARD_H.toFloat(), bg)
    // Paper patterns stay above the foot, so every style's foot is the same clean band.
    c.save()
    c.clipRect(0f, 0f, CARD_W.toFloat(), FOOT_LINE - 1f)
    style.dots?.let { d ->
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = d.toInt() }
        var dy = 27f
        while (dy < CARD_H) { var dx = 27f; while (dx < CARD_W) { c.drawCircle(dx, dy, 3f, dot); dx += 54f }; dy += 54f }
    }
    style.grid?.let { g ->
        val p = Paint().apply { color = g.toInt(); strokeWidth = 2f }
        var at = 0f
        while (at < CARD_W) { c.drawLine(at, 0f, at, CARD_H.toFloat(), p); at += 54f }
        at = 0f
        while (at < CARD_H) { c.drawLine(0f, at, CARD_W.toFloat(), at, p); at += 54f }
    }
    c.restore()

    val left = 150f
    // The margin rule down the left, as on the app icon.
    val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.rule.toInt(); strokeWidth = 8f; strokeCap = Paint.Cap.ROUND }
    // Ruled paper runs it from the top edge, like a real pad; it always ends just above the foot.
    c.drawLine(100f, if (style.lines != null) 0f else 110f, 100f, if (options.foot) FOOT_LINE - 10f else if (style.lines != null) FOOT_LINE else CARD_H - 110f, rule)

    val titleH = title?.let { it.height + 48f } ?: 0f
    val slice = if (body != null && to > from) (body.getLineBottom(to - 1) - body.getLineTop(from)).toFloat() else 0f
    var y = CARD_TOP + if (center) ((CARD_BOTTOM - CARD_TOP - titleH - slice) / 2).coerceAtLeast(0f) else 0f
    val bodyTop = y + titleH
    // Ruled paper: lines at the body's line spacing, each under a line of text, from the title down to the foot.
    style.lines?.let { l ->
        val p = Paint().apply { color = l.toInt(); strokeWidth = 2f }
        val (first, pitch) = body?.takeIf { to > from }?.let { b ->
            (bodyTop + b.getLineBaseline(from) - b.getLineTop(from) + 10f) to
                (if (b.lineCount > 1) (b.getLineBaseline(1) - b.getLineBaseline(0)).toFloat() else b.paint.fontSpacing * 1.4f)
        } ?: ((bodyTop + 40f) to 70f)
        var at = first
        while (at - pitch > y + (title?.height ?: 0)) at -= pitch
        // The last line keeps clear of the foot's hairline rather than doubling it.
        while (at < FOOT_LINE - 24f) { c.drawLine(0f, at, CARD_W.toFloat(), at, p); at += pitch }
    }
    title?.let { c.save(); c.translate(left, y); it.draw(c); c.restore() }
    if (body != null && to > from) {
        c.save()
        c.clipRect(0f, bodyTop, CARD_W.toFloat(), bodyTop + slice)
        c.translate(left, bodyTop - body.getLineTop(from))
        // AI cards: a tinted, outlined box behind their lines (the part on this card).
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.rule.toInt(); alpha = 26 }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.rule.toInt(); alpha = 110; this.style = Paint.Style.STROKE; strokeWidth = 3f }
        for (r in ai) {
            val a = maxOf(body.getLineForOffset(r.first), from)
            val z = minOf(body.getLineForOffset(maxOf(r.last, r.first)), to - 1)
            if (a > z) continue
            val box = RectF(-28f, body.getLineTop(a) - 10f, body.width + 28f, body.getLineBottom(z) + 10f)
            c.drawRoundRect(box, 28f, 28f, fill)
            c.drawRoundRect(box, 28f, 28f, edge)
        }
        body.draw(c)
        c.restore()
    }

    if (!options.foot) return bmp
    // Foot: a hairline, then the mark and the name.
    val hair = Paint().apply { color = style.hair.toInt(); strokeWidth = 2f }
    c.drawLine(96f, FOOT_LINE, CARD_W - 96f, FOOT_LINE, hair)
    val mark = 64f
    val mx = 96f
    val my = CARD_H - 92f
    drawMark(c, mx, my, mark, edge = style.isDark)
    val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 38f; typeface = font(R.font.inter_semibold) }
    c.drawText("Margin", mx + mark + 22f, my + mark / 2 + 13f, name)
    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 30f; typeface = font(R.font.inter_regular); textAlign = Paint.Align.RIGHT }
    c.drawText(footNote, CARD_W - 96f, my + mark / 2 + 11f, small)
    return bmp
}

/** The app icon (see MarginMark), [s] px square at ([x], [y]). */
private fun drawMark(c: Canvas, x: Float, y: Float, s: Float, edge: Boolean) {
    val k = s / 1024f
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111111.toInt() }
    c.drawRoundRect(RectF(x, y, x + s, y + s), 230 * k, 230 * k, p)
    // On a dark card, a hairline edge keeps the dark icon off the background.
    if (edge) c.drawRoundRect(RectF(x + 4 * k, y + 4 * k, x + s - 4 * k, y + s - 4 * k), 228 * k, 228 * k,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3A3E.toInt(); style = Paint.Style.STROKE; strokeWidth = 16 * k })
    p.strokeWidth = 56 * k
    p.strokeCap = Paint.Cap.ROUND
    p.color = 0xFFFFB340.toInt()
    c.drawLine(x + 302 * k, y + 232 * k, x + 302 * k, y + 792 * k, p)
    p.color = 0xFFFFFFFF.toInt()
    listOf(360f to 722f, 512f to 662f, 664f to 562f).forEach { (ly, x2) -> c.drawLine(x + 402 * k, y + ly * k, x + x2 * k, y + ly * k, p) }
}

/** Share as image: the card as it will look, a row of styles to try, and Share. */
@androidx.compose.runtime.Composable
fun CardDialog(blocks: List<Block>, date: String, onDismiss: () -> Unit, onSave: (CardOptions) -> Unit, onShare: (CardOptions) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Opens on the style used last time.
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences(CARD_PREFS, Context.MODE_PRIVATE) }
    var style by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(runCatching { CardStyle.valueOf(prefs.getString(LAST_STYLE, null)!!) }.getOrDefault(CardStyle.PAPER))
    }
    var size by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(runCatching { CardSize.valueOf(prefs.getString(LAST_SIZE, null)!!) }.getOrDefault(CardSize.PORTRAIT))
    }
    var font by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(runCatching { CardFont.valueOf(prefs.getString(LAST_FONT, null)!!) }.getOrDefault(CardFont.SANS))
    }
    var foot by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(prefs.getBoolean(SHOW_FOOT, true)) }
    val options = CardOptions(style, size, font, foot)
    // Saved when the cards go out, so the next note opens the same way.
    fun remembered(o: CardOptions) = o.also {
        prefs.edit().putString(LAST_STYLE, it.style.name).putString(LAST_SIZE, it.size.name).putString(LAST_FONT, it.font.name).putBoolean(SHOW_FOOT, it.foot).apply()
    }
    var pages by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<() -> Bitmap>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(options) {
        pages = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { noteCards(context, blocks, date, options) }
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState { pages.size }
    androidx.compose.ui.window.Dialog(onDismiss, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(jc.bg).systemBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.align(Alignment.CenterStart)) { TextAction("Cancel", onDismiss) }
                Text("Share as image", style = JType.rowTitle, color = jc.ink)
            }
            androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), pageSpacing = 16.dp) { i ->
                val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pages, i) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { pages.getOrNull(i)?.invoke()?.asImageBitmap() }
                }
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // As large as fits, whichever way the card is shaped.
                    val r = size.ratio
                    val fitsWidth = maxWidth / maxHeight < r
                    image?.let {
                        androidx.compose.foundation.Image(
                            it, "Preview of the ${style.label} card, ${i + 1} of ${pages.size}",
                            Modifier.aspectRatio(r, matchHeightConstraintsFirst = !fitsWidth).shadow(JElevation.sheet, JShape.md).clip(JShape.md),
                        )
                    }
                }
            }
            if (pages.size > 1) Text("${pager.currentPage + 1} of ${pages.size} · swipe to see them all", style = JType.small, color = jc.muted)
            // Swatches in sections (Paper, Colour, Dark), scrolled so the chosen one shows on opening.
            val scroll = rememberScrollState()
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val before = CARD_GROUPS.flatMap { it.second }.indexOf(style) + CARD_GROUPS.indexOfFirst { style in it.second }
                scroll.scrollTo(with(density) { (before * 58).dp.roundToPx() } - with(density) { 80.dp.roundToPx() })
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CARD_GROUPS.forEachIndexed { gi, (group, styles) ->
                if (gi > 0) Box(Modifier.padding(top = 22.dp).size(1.dp, 40.dp).background(jc.line))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(group.uppercase(), style = JType.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = jc.muted, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                styles.forEach { s ->
                    val on = s == style
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.size(48.dp).border(2.dp, if (on) jc.accent else Color.Transparent, CircleShape).padding(5.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(s.bg), Color(s.bg2 ?: s.bg))))
                                .border(1.dp, jc.line, CircleShape)
                                .clickable(role = Role.RadioButton) { style = s }
                                .semantics { contentDescription = s.label; selected = on },
                            contentAlignment = Alignment.Center,
                        ) { Text("Aa", style = JType.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color(s.ink)) }
                        Text(s.label, style = JType.small, maxLines = 1, color = if (on) jc.ink else jc.muted)
                    }
                }
                }
                }
                }
            }
            // Shape, lettering, and whether the date and Margin mark show.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                CardSize.entries.forEach { z -> Pill(z.label, z == size, { size = z }) }
                Box(Modifier.size(1.dp, 24.dp).background(jc.line))
                CardFont.entries.forEach { f -> Pill(f.label, f == font, { font = f }) }
                Box(Modifier.size(1.dp, 24.dp).background(jc.line))
                Pill("Date & Margin", foot, { foot = !foot })
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(if (pages.size > 1) "Save ${pages.size}" else "Save", { onSave(remembered(options)) }, Modifier.weight(1f))
                PrimaryButton(if (pages.size > 1) "Share ${pages.size} images" else "Share", { onShare(remembered(options)) }, Modifier.weight(2f))
            }
        }
    }
}
