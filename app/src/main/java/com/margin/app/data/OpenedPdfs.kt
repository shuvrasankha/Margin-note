package com.margin.app.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Where an opened PDF lives in Margin: the note, and the line holding it. */
data class PdfPlace(val noteId: String, val folderId: String, val lineId: String)

/** SHA-256 of [input] and how many bytes it had, read in one pass. */
fun sha256(input: InputStream): Pair<String, Long> {
    val digest = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        digest.update(buf, 0, n)
        total += n
    }
    return digest.digest().joinToString("") { "%02x".format(it) } to total
}

/**
 * The live note still holding the PDF remembered as [noteId]/[lineId]: not trashed and, unless it's locked (its
 * body can't be read until it's unlocked), with a PDF on that line. Null when it's gone.
 */
fun rememberedPdf(notes: List<NoteEntity>, noteId: String, lineId: String): PdfPlace? {
    val n = notes.firstOrNull { it.id == noteId && !it.deleted && it.trashedAt == null } ?: return null
    if (!n.isLocked && n.blocks.none { it.id == lineId && it.attachment?.isPdf == true }) return null
    return PdfPlace(n.id, n.folderId, lineId)
}

/**
 * A PDF in an unlocked live note with the same bytes as the one opened ([sha], [size]): files of another size are
 * never read, so this stays cheap. [sizeOf] is -1 for a file that isn't on the phone.
 */
fun samePdf(notes: List<NoteEntity>, sha: String, size: Long, sizeOf: (Attachment) -> Long, hashOf: (Attachment) -> String?): PdfPlace? {
    for (n in notes) {
        if (n.deleted || n.trashedAt != null || n.isLocked) continue
        for (b in n.blocks) {
            val a = b.attachment?.takeIf { it.isPdf && !it.isSealed } ?: continue
            if (sizeOf(a) == size && hashOf(a) == sha) return PdfPlace(n.id, n.folderId, b.id)
        }
    }
    return null
}

/**
 * "Open with Margin" on a PDF: the note that already holds it, or a new note in Notes made for it. Each opened
 * file is remembered by its bytes, so it finds its note again however it arrives (Downloads, Files, Gmail) and
 * after the copy in Margin has been drawn on or edited.
 */
@Singleton
class OpenedPdfs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: NoteRepository,
    private val files: AttachmentFiles,
) {
    private val prefs get() = context.getSharedPreferences("margin_pdf", Context.MODE_PRIVATE)

    suspend fun open(uri: Uri): PdfPlace {
        val (sha, size) = withContext(Dispatchers.IO) {
            (context.contentResolver.openInputStream(uri) ?: throw IOException("Couldn't open the PDF")).use(::sha256)
        }
        val notes = repo.liveNotes().first()
        val known = prefs.getString("src:$sha", null)?.split('\n')?.takeIf { it.size == 2 }
            ?.let { (note, line) -> rememberedPdf(notes, note, line) }
        val place = known ?: withContext(Dispatchers.IO) {
            samePdf(notes, sha, size, sizeOf = { a -> files.file(a).takeIf(File::exists)?.length() ?: -1L }) { a ->
                runCatching { files.file(a).inputStream().use(::sha256).first }.getOrNull()
            }
        } ?: run {
            // Some apps send no type and no .pdf name; it's still a PDF.
            val a = files.import(uri).let { if (it.isPdf) it else it.copy(mime = PDF_MIME) }
            val blocks = sharedNoteBlocks(a.name.removeSuffix(".pdf").removeSuffix(".PDF"), null, a)
            val id = newId()
            repo.saveNote(id, DEFAULT_FOLDER_ID, blocks)
            PdfPlace(id, DEFAULT_FOLDER_ID, blocks.first { it.attachment != null }.id)
        }
        prefs.edit().putString("src:$sha", place.noteId + "\n" + place.lineId).apply()
        return place
    }
}
