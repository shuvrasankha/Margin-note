package com.shuva.jotter.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Reads the text in photos and PDFs attached to notes, on the phone, so search finds words inside them.
 * Runs in the background whenever notes change; each file is read once (files never change: an edit makes a new one).
 */
@Singleton
class AttachmentIndex @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: NoteRepository,
    private val files: AttachmentFiles,
) {
    @OptIn(FlowPreview::class)
    suspend fun run() {
        repo.liveNotes().debounce(3000).collect { notes ->
            runCatching { indexMissing(notes) }
        }
    }

    private suspend fun indexMissing(notes: List<NoteEntity>) {
        val wanted = notes.filter { !it.isLocked }.flatMap { n -> n.blocks.mapNotNull { it.attachment } }
            .filter { (it.isImage || it.isPdf) && !it.isDrawing }.associateBy { it.file }
        val done = repo.indexedAttachments()
        // Text of files no note uses any more (Recently Deleted counts as in use until it's emptied).
        repo.forgetAttachmentText(done - repo.attachmentsInUse())
        for ((name, a) in wanted) {
            if (name in done) continue
            val file = files.file(a)
            if (!file.exists()) continue // not downloaded yet; tried again after the next change
            val text = withContext(Dispatchers.IO) { runCatching { if (a.isPdf) pdfText(file) else imageText(file) }.getOrNull() } ?: continue
            repo.saveAttachmentText(name, text)
        }
    }

    private suspend fun imageText(file: File): String {
        val bmp = decodeImage(file, 2048) ?: return ""
        return recognize(bmp).also { bmp.recycle() }
    }

    /** A PDF's own text when it has some; a scan (only pictures) is read page by page, up to [MAX_PAGES]. */
    private suspend fun pdfText(file: File): String {
        val own = runCatching { Pdf.text(context, file) }.getOrNull()
        if (own == null && Pdf.info(context, file)?.locked != false) return "" // password protected, or not a PDF
        if (own != null && own.count { it.isLetterOrDigit() } > 40) return own
        val pages = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                (0 until minOf(r.pageCount, MAX_PAGES)).map { i ->
                    r.openPage(i).use { page ->
                        val scale = 1800f / maxOf(page.width, page.height)
                        val bmp = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }
        return pages.map { bmp -> recognize(bmp).also { bmp.recycle() } }.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private suspend fun recognize(bmp: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { r -> cont.resume(ocrToNoteText(r.textBlocks.map { b -> b.lines.map { it.text } })) }
                    .addOnFailureListener { cont.resume("") }
            }
        } finally {
            recognizer.close()
        }
    }

    private companion object { const val MAX_PAGES = 30 }
}
