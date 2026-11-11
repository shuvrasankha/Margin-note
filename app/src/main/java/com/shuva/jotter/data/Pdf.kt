package com.shuva.jotter.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.contentstream.PDContentStream
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.DrawObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.contentstream.operator.text.BeginText
import com.tom_roush.pdfbox.contentstream.operator.text.EndText
import com.tom_roush.pdfbox.contentstream.operator.text.MoveText
import com.tom_roush.pdfbox.contentstream.operator.text.MoveTextSetLeading
import com.tom_roush.pdfbox.contentstream.operator.text.NextLine
import com.tom_roush.pdfbox.contentstream.operator.text.SetCharSpacing
import com.tom_roush.pdfbox.contentstream.operator.text.SetFontAndSize
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextHorizontalScaling
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextLeading
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextRenderingMode
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextRise
import com.tom_roush.pdfbox.contentstream.operator.text.SetWordSpacing
import com.tom_roush.pdfbox.contentstream.operator.text.ShowText
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextAdjusted
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextLine
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextLineAndSpace
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.util.Vector
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix as PdfMatrix
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

const val PDF_MIME = "application/pdf"

val Attachment.isPdf get() = mime == PDF_MIME || name.endsWith(".pdf", ignoreCase = true)

/** What a PDF's card shows: its page count, or that it needs a password to open. */
data class PdfInfo(val pages: Int, val locked: Boolean)

/** One page in an edited PDF: page [index] of the original, turned [rotation] degrees more (a multiple of 90). */
data class PageEdit(val index: Int, val rotation: Int = 0)

/**
 * A line of text on a page, in points from the page's top-left as it's shown (after its rotation): it starts at
 * [x], its letters sit on [baseline], it's [width] wide and [size] points tall.
 */
data class TextRun(
    val text: String, val x: Float, val baseline: Float, val width: Float, val size: Float, val bold: Boolean, val family: Int = FAMILY_SANS,
) {
    val top get() = baseline - size * 0.8f
    val bottom get() = baseline + size * 0.22f
}

/** Typeface kinds new text can match: a plain sans, a serif (like Times) or a typewriter mono (like Courier). */
const val FAMILY_SANS = 0
const val FAMILY_SERIF = 1
const val FAMILY_MONO = 2

/**
 * New text on page [page] (points, as in [TextRun]): [text] (lines split on "\n") starting at [x] on [baseline].
 * When it replaces a line of the PDF, [cover] (left, top, right, bottom) is painted over first in [coverColor],
 * hiding the old words. Colors are ARGB.
 */
data class TextEdit(
    val page: Int, val text: String, val x: Float, val baseline: Float, val size: Float, val bold: Boolean = false, val family: Int = FAMILY_SANS,
    val color: Int = 0xFF000000.toInt(), val cover: List<Float>? = null, val coverColor: Int = 0xFFFFFFFF.toInt(),
)

class WrongPassword : IOException("That password didn't open the PDF.")

/**
 * PDF work for attachments, all blocking (call off the main thread). Reading pages uses Android's own renderer;
 * unlocking, splitting, merging, editing pages, drawing on a page and reading text use PdfBox. Attachment files
 * never change, so every edit returns new bytes for a new file.
 */
object Pdf {
    @Volatile private var ready = false

    private fun ensure(context: Context) {
        if (!ready) synchronized(this) { if (!ready) { PDFBoxResourceLoader.init(context.applicationContext); ready = true } }
    }

    private fun load(context: Context, file: File, password: String? = null): PDDocument {
        ensure(context)
        return try {
            if (password == null) PDDocument.load(file) else PDDocument.load(file, password)
        } catch (_: InvalidPasswordException) { throw WrongPassword() }
    }

    private fun PDDocument.bytes(): ByteArray {
        // Saved without its old protection, so the result opens anywhere.
        if (isEncrypted) isAllSecurityToBeRemoved = true
        val out = ByteArrayOutputStream()
        save(out)
        if (out.size() > MAX_ATTACHMENT_BYTES) throw AttachmentFiles.TooLarge()
        return out.toByteArray()
    }

    private val infos = HashMap<String, PdfInfo>()

    /** Pages and whether it needs a password; remembered per file (files never change). Null if it isn't a readable PDF. */
    fun info(context: Context, file: File): PdfInfo? {
        synchronized(infos) { infos[file.path]?.let { return it } }
        val info = try { load(context, file).use { PdfInfo(it.numberOfPages, false) } } catch (_: WrongPassword) { PdfInfo(0, true) } catch (_: Exception) { null }
        if (info != null) synchronized(infos) { infos[file.path] = info }
        return info
    }

    /** An unlocked copy, opened with [password]. Throws [WrongPassword]. */
    fun unlock(context: Context, file: File, password: String): ByteArray = load(context, file, password).use { it.bytes() }

    /** A PDF of just pages [pages] (0-based, in that order). */
    fun extract(context: Context, file: File, pages: List<Int>): ByteArray = edit(context, file, pages.map { PageEdit(it) })

    /** Each page as its own PDF. */
    fun splitEach(context: Context, file: File): List<ByteArray> = load(context, file).use { src -> (0 until src.numberOfPages).map { i -> pagesOf(src, listOf(PageEdit(i))) } }

    /** The pages of [edits], in that order, each turned as it says: reorders, removes (by leaving out) and rotates. */
    fun edit(context: Context, file: File, edits: List<PageEdit>): ByteArray = load(context, file).use { pagesOf(it, edits) }

    private fun pagesOf(src: PDDocument, edits: List<PageEdit>): ByteArray = PDDocument().use { out ->
        for (e in edits) {
            val page = out.importPage(src.getPage(e.index))
            page.rotation = ((page.rotation + e.rotation) % 360 + 360) % 360
        }
        out.bytes()
    }

    /** [files] one after the other as one PDF. */
    fun merge(context: Context, files: List<File>): ByteArray {
        val docs = files.map { load(context, it) }
        try {
            return PDDocument().use { out ->
                docs.forEach { d -> for (i in 0 until d.numberOfPages) out.importPage(d.getPage(i)) }
                out.bytes()
            }
        } finally { docs.forEach { it.close() } }
    }

    /**
     * Page [page] with [overlay] drawn over it. [overlay] is the page as shown (turned by its rotation), so it's
     * turned back before it's placed over the page's visible area.
     */
    fun stamp(context: Context, file: File, page: Int, overlay: Bitmap): ByteArray = load(context, file).use { doc ->
        val p = doc.getPage(page)
        val upright = if (p.rotation % 360 == 0) overlay
        else Bitmap.createBitmap(overlay, 0, 0, overlay.width, overlay.height, Matrix().apply { postRotate(-p.rotation.toFloat()) }, true)
        val image = LosslessFactory.createFromImage(doc, upright)
        val box = p.cropBox
        PDPageContentStream(doc, p, PDPageContentStream.AppendMode.APPEND, true, true).use { it.drawImage(image, box.lowerLeftX, box.lowerLeftY, box.width, box.height) }
        doc.bytes()
    }

    /** The lines of text on page [page], left to right and top to bottom; empty for a scanned page. */
    fun textRuns(context: Context, file: File, page: Int): List<TextRun> = load(context, file).use { doc ->
        val runs = mutableListOf<TextRun>()
        val stripper = object : PDFTextStripper() {
            val words = mutableListOf<List<TextPosition>>()
            override fun writeString(text: String, positions: MutableList<TextPosition>) { words += positions.toList() }
            override fun writeWordSeparator() {}
            override fun writeLineSeparator() = flush()
            override fun writePageEnd() = flush()
            fun flush() {
                // One line of the page can hold far-apart pieces (columns, a table's cells): each is its own run.
                var piece = mutableListOf<TextPosition?>()
                var lastEnd = Float.NaN
                for (w in words) {
                    val glyphs = w.filter { it.dir == 0f }
                    if (glyphs.isEmpty()) continue
                    val size = sizeOf(glyphs.first())
                    if (!lastEnd.isNaN() && glyphs.first().xDirAdj - lastEnd > size * 1.6f) { add(piece); piece = mutableListOf<TextPosition?>() }
                    if (piece.isNotEmpty()) piece += SPACE_MARK
                    piece += glyphs
                    lastEnd = glyphs.last().let { it.xDirAdj + it.widthDirAdj }
                }
                add(piece)
                words.clear()
            }
            fun add(piece: List<TextPosition?>) {
                // Spaces at either end (some PDFs pad lines out to the margin) aren't part of the line.
                val glyphs = piece.filterNotNull().dropWhile { it.unicode.isNullOrBlank() }.dropLastWhile { it.unicode.isNullOrBlank() }
                if (glyphs.isEmpty()) return
                val text = piece.joinToString("") { it?.unicode ?: " " }.trim()
                if (text.isEmpty()) return
                val first = glyphs.first(); val last = glyphs.last()
                val size = glyphs.maxOf { sizeOf(it) }
                val font = first.font
                val name = font?.name.orEmpty()
                val described = runCatching { font?.fontDescriptor }.getOrNull()
                // Monospaced when the font says so, by name, or when narrow letters are as wide as wide ones.
                val narrow = glyphs.filter { it.unicode in listOf("i", "l", ".", "I", "j") }.map { it.widthDirAdj }
                val wide = glyphs.filter { it.unicode in listOf("m", "w", "M", "W") }.map { it.widthDirAdj }
                val evenWidths = narrow.isNotEmpty() && wide.isNotEmpty() && narrow.average() > wide.average() * 0.85
                val family = when {
                    described?.isFixedPitch == true || evenWidths ||
                        Regex("courier|mono|consol|typewriter|monaco|menlo|inconsolata|code|andale|letter ?gothic|ocr", RegexOption.IGNORE_CASE).containsMatchIn(name) -> FAMILY_MONO
                    Regex("sans|arial|helvetica|roboto|verdana|calibri", RegexOption.IGNORE_CASE).containsMatchIn(name) -> FAMILY_SANS
                    described?.isSerif == true || Regex("times|serif|georgia|garamond|cambria|book|minion", RegexOption.IGNORE_CASE).containsMatchIn(name) -> FAMILY_SERIF
                    else -> FAMILY_SANS
                }
                runs += TextRun(
                    text, first.xDirAdj, glyphs.maxOf { it.yDirAdj }, last.xDirAdj + last.widthDirAdj - first.xDirAdj, size,
                    Regex("bold|black|heavy|semibold", RegexOption.IGNORE_CASE).containsMatchIn(name) || described?.isForceBold == true, family,
                )
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = page + 1
        stripper.endPage = page + 1
        stripper.getText(doc)
        runs
    }

    private val SPACE_MARK: TextPosition? = null
    private fun sizeOf(t: TextPosition) = t.yScale.takeIf { it > 1.5f } ?: t.fontSizeInPt.takeIf { it > 1.5f } ?: t.heightDir.coerceAtLeast(6f)

    /** [edits] written onto their pages as real text (in an embedded font), each covering the words it replaces. */
    fun editText(context: Context, file: File, edits: List<TextEdit>): ByteArray = load(context, file).use { doc ->
        val fonts = HashMap<Pair<Int, Boolean>, PDFont>()
        edits.groupBy { it.page }.forEach { (index, list) ->
            val page = doc.getPage(index)
            val shownHeight = if (page.rotation % 180 == 0) page.cropBox.height else page.cropBox.width
            // The replaced lines' old words are taken out of the page; a cover is only painted where some remain.
            val clear = runCatching { removeText(doc, page, list.mapNotNull { it.cover }) }.getOrNull()
            var coverIndex = 0
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                // From here on, x/y are points on the page as shown, measured from its bottom-left.
                cs.transform(shownToPage(page))
                for (e in list) {
                    e.cover?.takeUnless { clear?.getOrNull(coverIndex++) == true }?.let { (l, t, r, b) ->
                        cs.setNonStrokingColor(rgb(e.coverColor, 16), rgb(e.coverColor, 8), rgb(e.coverColor, 0))
                        cs.addRect(l, shownHeight - b, r - l, b - t)
                        cs.fill()
                    }
                    if (e.text.isBlank()) continue
                    val f = fonts.getOrPut(e.family to e.bold) { font(doc, e.bold, e.family) }
                    cs.setNonStrokingColor(rgb(e.color, 16), rgb(e.color, 8), rgb(e.color, 0))
                    e.text.split('\n').forEachIndexed { i, line ->
                        if (line.isBlank()) return@forEachIndexed
                        cs.beginText()
                        cs.setFont(f, e.size)
                        cs.newLineAtOffset(e.x, shownHeight - e.baseline - i * e.size * 1.2f)
                        cs.showText(writable(f, line))
                        cs.endText()
                    }
                }
            }
        }
        doc.bytes()
    }

    /**
     * Takes the text inside [covers] (areas on the page as shown, from its top-left) out of [page]'s own content:
     * each text-drawing step whose letters all fall inside one area becomes an equal blank space, so the rest of the
     * line doesn't move. Returns, per area, whether it's now free of text (text inside a form or image stays).
     */
    private fun removeText(doc: PDDocument, page: PDPage, covers: List<List<Float>>): BooleanArray {
        val clear = BooleanArray(covers.size)
        if (covers.isEmpty()) return clear
        val ops = TextSteps { x, y -> pageToShown(page, x, y) }
        ops.processPage(page)
        val tokens = PDFStreamParser(page as PDContentStream).apply { parse() }.tokens.toMutableList()
        val at = tokens.indices.filter { tokens[it] is Operator }
        // Only rewrite when the steps line up exactly with what was measured.
        if (at.size != ops.names.size || at.indices.any { (tokens[at[it]] as Operator).name != ops.names[it] }) return clear
        fun inside(c: List<Float>, p: FloatArray) = p[0] in c[0] - 1.5f..c[2] + 1.5f && p[1] in c[1] - 1.5f..c[3] + 1.5f
        val removed = HashSet<TextSteps.Step>()
        for (step in ops.shows) {
            if (step.centers.isEmpty() || step.blank.isNaN()) continue
            if (covers.none { c -> step.centers.all { inside(c, it) } }) continue
            val k = at[step.index]
            if (k == 0) continue
            tokens[k - 1] = COSArray().apply { add(COSFloat(step.blank)) }
            tokens[k] = Operator.getOperator("TJ")
            removed += step
        }
        if (removed.isEmpty()) return clear
        val stream = PDStream(doc)
        stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(tokens) }
        page.setContents(stream)
        covers.forEachIndexed { i, c -> clear[i] = ops.shows.none { it !in removed && it.centers.any { p -> inside(c, p) } } }
        return clear
    }

    /** A point in [p]'s own space as shown: from the top-left, after its rotation. */
    private fun pageToShown(p: PDPage, ux: Float, uy: Float): FloatArray {
        val b = p.cropBox
        val l = b.lowerLeftX; val bt = b.lowerLeftY; val w = b.width; val h = b.height
        val r = (p.rotation % 360 + 360) % 360
        val (x, y) = when (r) {
            90 -> (uy - bt) to (l + w - ux)
            180 -> (l + w - ux) to (bt + h - uy)
            270 -> (bt + h - uy) to (ux - l)
            else -> (ux - l) to (uy - bt)
        }
        val shownHeight = if (r % 180 == 0) h else w
        return floatArrayOf(x, shownHeight - y)
    }

    private fun rgb(color: Int, shift: Int) = ((color shr shift) and 0xFF) / 255f

    /** Maps points on page [p] as shown (from its bottom-left, after rotation) to the page's own space. */
    private fun shownToPage(p: PDPage): PdfMatrix {
        val b = p.cropBox
        val (l, bt, w, h) = listOf(b.lowerLeftX, b.lowerLeftY, b.width, b.height)
        return when ((p.rotation % 360 + 360) % 360) {
            90 -> PdfMatrix(0f, 1f, -1f, 0f, l + w, bt)
            180 -> PdfMatrix(-1f, 0f, 0f, -1f, l + w, bt + h)
            270 -> PdfMatrix(0f, -1f, 1f, 0f, l, bt + h)
            else -> PdfMatrix(1f, 0f, 0f, 1f, l, bt)
        }
    }

    /** The phone's own font, embedded (only the letters used), so most languages print; Helvetica if it's missing. */
    private fun font(doc: PDDocument, bold: Boolean, family: Int): PDFont {
        val names = when {
            family == FAMILY_MONO -> listOf("DroidSansMono.ttf", "CutiveMono.ttf")
            family == FAMILY_SERIF && bold -> listOf("NotoSerif-Bold.ttf", "NotoSerif-Regular.ttf")
            family == FAMILY_SERIF -> listOf("NotoSerif-Regular.ttf")
            bold -> listOf("DroidSans-Bold.ttf", "Roboto-Bold.ttf")
            else -> listOf("RobotoStatic-Regular.ttf", "DroidSans.ttf", "Roboto-Regular.ttf")
        }
        for (n in names) {
            val f = File("/system/fonts", n)
            if (f.canRead()) runCatching { return PDType0Font.load(doc, f) }
        }
        return when (family) {
            FAMILY_MONO -> if (bold) PDType1Font.COURIER_BOLD else PDType1Font.COURIER
            FAMILY_SERIF -> if (bold) PDType1Font.TIMES_BOLD else PDType1Font.TIMES_ROMAN
            else -> if (bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
        }
    }

    /** [text] with any letter [font] can't draw swapped for "?" (rather than failing the whole save). */
    private fun writable(font: PDFont, text: String): String {
        if (runCatching { font.encode(text) }.isSuccess) return text
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val s = String(Character.toChars(cp))
            out.append(if (runCatching { font.encode(s) }.isSuccess) s else "?")
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /** The text in it, page by page; empty for a scanned PDF (pictures of text). */
    fun text(context: Context, file: File): String = load(context, file).use { doc ->
        PDFTextStripper().getText(doc).replace("\r\n", "\n").lines().joinToString("\n") { it.trimEnd() }.trim()
    }

    // ---------- Showing pages ----------

    private val renderLock = Mutex()

    /** Page sizes in points, as shown (turned by rotation); empty when Android can't open it (locked). */
    suspend fun pageSizes(file: File): List<Pair<Int, Int>> = renderLock.withLock {
        open(file) { r -> (0 until r.pageCount).map { i -> r.openPage(i).use { it.width to it.height } } } ?: emptyList()
    }

    /** Page [index] drawn [width] pixels wide on white, or null. */
    suspend fun render(file: File, index: Int, width: Int): Bitmap? = renderLock.withLock {
        open(file) { r ->
            if (index !in 0 until r.pageCount) return@open null
            r.openPage(index).use { page ->
                val w = width.coerceIn(16, 2400)
                val h = (w.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(16, 4000)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    // The last file opened stays open (a PDF is parsed once, not for every page); always used under [renderLock].
    private var openPath: String? = null
    private var openFd: ParcelFileDescriptor? = null
    private var openRenderer: PdfRenderer? = null

    private fun <T> open(file: File, block: (PdfRenderer) -> T): T? = try {
        if (openPath != file.path || openRenderer == null) {
            runCatching { openRenderer?.close() }; runCatching { openFd?.close() }
            openRenderer = null; openFd = null; openPath = null
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            openRenderer = try { PdfRenderer(fd) } catch (e: Exception) { fd.close(); throw e }
            openFd = fd; openPath = file.path
        }
        block(openRenderer!!)
    } catch (_: Exception) { null }
}

/** "1-3, 5, 8-6" → 0-based page numbers in that order, for a [count]-page PDF; null when it doesn't make sense. */
fun parsePages(text: String, count: Int): List<Int>? {
    val out = mutableListOf<Int>()
    for (part in text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }) {
        val m = Regex("""(\d+)\s*(?:[-–]\s*(\d+))?""").matchEntire(part) ?: return null
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].ifEmpty { null }?.toInt() ?: a
        if (a !in 1..count || b !in 1..count) return null
        out += if (a <= b) (a..b).map { it - 1 } else (a downTo b).map { it - 1 }
    }
    return out.ifEmpty { null }
}

/**
 * Goes through a page's own drawing steps (not those inside forms), noting each step's name and, for the text
 * steps (Tj and TJ), where each letter's middle lands and the blank TJ that would move the pen just as far.
 */
private class TextSteps(private val toShown: (Float, Float) -> FloatArray) : PDFStreamEngine() {
    class Step(val index: Int) {
        val centers = mutableListOf<FloatArray>()
        var blank = Float.NaN
    }

    val names = mutableListOf<String>()
    val shows = mutableListOf<Step>()
    private var depth = 0
    private var current: Step? = null

    init {
        listOf(
            BeginText(), EndText(), SetFontAndSize(), ShowText(), ShowTextAdjusted(), ShowTextLine(), ShowTextLineAndSpace(),
            MoveText(), MoveTextSetLeading(), NextLine(), SetCharSpacing(), SetWordSpacing(), SetTextHorizontalScaling(),
            SetTextLeading(), SetTextRenderingMode(), SetTextRise(), SetMatrix(), Save(), Restore(), Concatenate(), DrawObject(),
        ).forEach { addOperator(it) }
    }

    override fun processChildStream(contentStream: PDContentStream, page: PDPage) {
        depth++
        try { super.processChildStream(contentStream, page) } finally { depth-- }
    }

    override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
        if (depth > 0) return super.processOperator(operator, operands)
        names += operator.name
        val show = operator.name == "Tj" || operator.name == "TJ"
        if (!show) return super.processOperator(operator, operands)
        val step = Step(names.size - 1)
        val before = textMatrix?.clone()
        current = step
        try { super.processOperator(operator, operands) } finally { current = null }
        val after = textMatrix
        val state = graphicsState.textState
        val unit = state.fontSize * state.horizontalScaling / 100f
        if (before != null && after != null && unit != 0f) {
            val a = before.scaleX; val b = before.shearY
            val len = a * a + b * b
            if (len > 0f) {
                val advance = ((after.translateX - before.translateX) * a + (after.translateY - before.translateY) * b) / len
                step.blank = -advance * 1000f / unit
            }
        }
        shows += step
    }

    override fun showGlyph(textRenderingMatrix: PdfMatrix, font: PDFont, code: Int, unicode: String?, displacement: Vector) =
        note(textRenderingMatrix, displacement, unicode)

    override fun showGlyph(textRenderingMatrix: PdfMatrix, font: PDFont, code: Int, displacement: Vector) =
        note(textRenderingMatrix, displacement, runCatching { font.toUnicode(code) }.getOrNull())

    private fun note(m: PdfMatrix, d: Vector, unicode: String?) {
        val step = current ?: return
        if (depth > 0) return
        // Spaces don't count: some PDFs pad each line with them far past its last word.
        if (unicode != null && unicode.isBlank()) return
        // The middle of the letter: half its width along, a third of the way up.
        val gx = d.x / 2f; val gy = 0.33f
        val x = m.scaleX * gx + m.shearX * gy + m.translateX
        val y = m.shearY * gx + m.scaleY * gy + m.translateY
        step.centers += toShown(x, y)
    }
}
