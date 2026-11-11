package com.shuva.jotter.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import java.io.File
import java.io.OutputStream

/** A note as an A4 PDF: text with its styles and lists, photos scaled to the page width. */
object NotePdf {
    private const val W = 595 // A4 in points
    private const val H = 842
    private const val M = 48f
    private const val INDENT = 18f
    private const val QUOTE = 12f

    private val highlight = mapOf(
        Tint.AMBER to 0x59E8A33D, Tint.BLUE to 0x593B82F6, Tint.GREEN to 0x5934A853, Tint.GREY to 0x598E8E93,
    )

    /** [fileOf] finds an attachment's file. Blocking (decodes photos): call off the main thread. */
    fun write(blocks: List<Block>, fileOf: (Attachment) -> File, out: OutputStream) {
        val doc = PdfDocument()
        val width = W - 2 * M
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = M
        fun newPage() {
            page?.let(doc::finishPage)
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
            y = M
        }
        /** Starts a new page unless [h] more points fit (or the page is still empty). */
        fun room(h: Float) { if (y + h > H - M && y > M) newPage() }
        newPage()
        val bar = Paint().apply { color = 0xFFAAAAAA.toInt() }
        var number = 0
        blocks.forEachIndexed { i, b ->
            if (b.type == BlockType.NUMBERED) number++ else number = 0
            val a = b.attachment
            if (a != null && a.isImage) {
                val bitmap = runCatching { decodeImage(fileOf(a), 1600) }.getOrNull()
                if (bitmap != null) {
                    val scale = minOf(width / bitmap.width, (H - 2 * M) / bitmap.height, 1f)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    room(h + 8)
                    page!!.canvas.drawBitmap(bitmap, null, RectF(M, y, M + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                    bitmap.recycle()
                    y += h + 8
                    return@forEachIndexed
                }
            }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = when { i == 0 -> 22f; b.type == BlockType.HEADING -> 17f; b.type == BlockType.SUBHEADING -> 14f; b.type == BlockType.MONOSPACED -> 10f; else -> 11f }
                typeface = when {
                    b.type == BlockType.MONOSPACED -> Typeface.MONOSPACE
                    i == 0 || b.type == BlockType.HEADING || b.type == BlockType.SUBHEADING -> Typeface.DEFAULT_BOLD
                    else -> Typeface.DEFAULT
                }
            }
            val prefix = when {
                a != null || i == 0 -> ""
                b.type == BlockType.BULLET -> "•  "
                b.type == BlockType.DASHED -> "–  "
                b.type == BlockType.NUMBERED -> "$number.  "
                b.type == BlockType.CHECK -> if (b.checked) "☑  " else "☐  "
                else -> ""
            }
            val body = when {
                b.table != null -> b.table.plainText
                a == null -> b.text
                a.isLink -> a.name + "  " + runCatching { fileOf(a).readText().trim() }.getOrDefault("")
                a.isDrawing -> "[Drawing]"
                else -> "[${a.name}]"
            }
            val text = SpannableStringBuilder(prefix + body)
            if (a == null) b.marks.clampedTo(b.text.length).forEach { m ->
                val span: Any = when (m.mark) {
                    Mark.BOLD -> StyleSpan(Typeface.BOLD)
                    Mark.ITALIC -> StyleSpan(Typeface.ITALIC)
                    Mark.UNDERLINE -> UnderlineSpan()
                    Mark.HIGHLIGHT -> BackgroundColorSpan(highlight.getValue(m.color))
                }
                text.setSpan(span, prefix.length + m.start, prefix.length + m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (b.type == BlockType.CHECK && b.checked) {
                text.setSpan(StrikethroughSpan(), prefix.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(ForegroundColorSpan(Color.GRAY), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (a != null) text.setSpan(ForegroundColorSpan(Color.DKGRAY), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val lead = INDENT * b.indent + if (b.quote) QUOTE else 0f
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, (width - lead).toInt().coerceAtLeast(40))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.2f).build()
            val before = if (b.type == BlockType.HEADING || b.type == BlockType.SUBHEADING) 6f else 0f
            y += before
            // Line by line, so a long paragraph can continue on the next page.
            for (line in 0 until layout.lineCount) {
                val top = layout.getLineTop(line).toFloat()
                val lh = layout.getLineBottom(line) - top
                room(lh)
                val canvas = page!!.canvas
                if (b.quote) canvas.drawRect(M + INDENT * b.indent, y, M + INDENT * b.indent + 2f, y + lh, bar)
                canvas.save()
                canvas.translate(M + lead, y - top)
                canvas.clipRect(0f, top, width - lead, top + lh)
                layout.draw(canvas)
                canvas.restore()
                y += lh
            }
            y += if (i == 0) 10f else 3f
        }
        page?.let(doc::finishPage)
        doc.writeTo(out)
        doc.close()
    }
}

/** Prints an already written PDF [file] (from [NotePdf.write]) through the system print dialog. */
class PdfFilePrintAdapter(private val file: File, private val name: String) : android.print.PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: android.print.PrintAttributes?, newAttributes: android.print.PrintAttributes,
        cancellationSignal: android.os.CancellationSignal, callback: LayoutResultCallback, extras: android.os.Bundle?,
    ) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        val info = android.print.PrintDocumentInfo.Builder(name).setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out android.print.PageRange>, destination: android.os.ParcelFileDescriptor,
        cancellationSignal: android.os.CancellationSignal, callback: WriteResultCallback,
    ) {
        try {
            file.inputStream().use { input -> java.io.FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
            callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
