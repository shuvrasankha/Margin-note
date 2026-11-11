package com.shuva.jotter.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Attachment files: a private folder of app storage, one file per attachment, never changed once written. */
@Singleton
class AttachmentFiles @Inject constructor(@ApplicationContext private val context: Context) {
    val dir: File get() = File(context.filesDir, "attachments").apply { mkdirs() }

    fun file(a: Attachment) = File(dir, a.file)

    class TooLarge : IOException("Files over ${MAX_ATTACHMENT_BYTES / 1_000_000} MB can't be attached.")

    /** Copies a picked file into app storage. */
    suspend fun import(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "File"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size > MAX_ATTACHMENT_BYTES) throw TooLarge()
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        val stored = newId() + (ext?.let { ".$it" } ?: "")
        val target = File(dir, stored)
        val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't open the file")
        input.use { inp ->
            target.outputStream().use { out ->
                // Size from the picker can be missing or wrong; stop copying once over the limit.
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_ATTACHMENT_BYTES) { out.close(); target.delete(); throw TooLarge() }
                    out.write(buf, 0, n)
                }
            }
        }
        Attachment(stored, name, mime)
    }

    /** Stores [bytes] as a new attachment file. Files never change, so an edited drawing gets a new one. */
    suspend fun write(bytes: ByteArray, ext: String, name: String, mime: String): Attachment = withContext(Dispatchers.IO) {
        val stored = "${newId()}.$ext"
        val tmp = File(dir, "$stored.part")
        tmp.writeBytes(bytes)
        tmp.renameTo(File(dir, stored))
        Attachment(stored, name, mime)
    }

    /** A new empty file in the attachments folder and its content URI, for the camera app to write a photo into. */
    fun newPhotoTarget(): Pair<File, Uri> {
        val f = File(dir, newId() + ".jpg")
        return f to FileProvider.getUriForFile(context, context.packageName + ".files", f)
    }

    /**
     * A copy of photo [a] turned 90° clockwise, as a new file (files never change). Keeps the name; PNG stays
     * PNG, anything else becomes JPEG.
     */
    suspend fun rotated(a: Attachment): Attachment = withContext(Dispatchers.IO) {
        // shortcut: capped at 4096 px on the long side to stay inside memory, so a bigger photo loses some detail when rotated.
        val bitmap = decodeImage(file(a), 4096) ?: throw IOException("Couldn't read the photo")
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(90f) }, true)
        val png = a.mime == "image/png"
        val bytes = java.io.ByteArrayOutputStream().also { turned.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
        bitmap.recycle()
        turned.recycle()
        write(bytes, if (png) "png" else "jpg", a.name, if (png) "image/png" else "image/jpeg")
    }

    /** A file in the exports cache folder, replacing any older one of that name. */
    fun exportFile(name: String): File = File(File(context.cacheDir, "exports").apply { mkdirs() }, name)

    /** An intent that sends [file] (from [exportFile]) to another app. */
    fun sendIntent(file: File, mime: String, subject: String): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        return Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** An intent that opens the attachment in another app, read-only. */
    fun viewIntent(a: Attachment): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file(a))
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * Deletes files no note uses any more (deleted for good, or removed from the note). Only files older
     * than a day, so one just attached to a note that isn't saved yet is never removed.
     */
    suspend fun cleanUp(referenced: Set<String>, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        dir.listFiles()?.filter { it.name !in referenced && now - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
    }
}

const val MAX_ATTACHMENT_BYTES = 20_000_000L

/** Attachment files every stored note uses, including notes in Recently Deleted (they can be recovered). */
fun referencedAttachments(notes: List<NoteEntity>): Set<String> =
    notes.filter { !it.deleted }.flatMap { n -> n.blocks.mapNotNull { it.attachment?.file } }.toSet()

/** Names are made by [AttachmentFiles.import]; anything else (from Drive) is refused, so it can't escape the folder. */
fun isSafeAttachmentName(name: String) = name.matches(Regex("[A-Za-z0-9-]{1,64}(\\.[a-z0-9]{1,8})?"))

/** Decodes an image no larger than [maxSide] on its long side, upright where the platform can (Android 9+). */
fun decodeImage(file: File, maxSide: Int): Bitmap? {
    if (!file.exists()) return null
    if (android.os.Build.VERSION.SDK_INT >= 28) {
        return android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(file)) { decoder, info, _ ->
            val side = maxOf(info.size.width, info.size.height)
            if (side > maxSide) decoder.setTargetSampleSize(side / maxSide)
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE // Bitmap.createBitmap needs a software bitmap
        }
    }
    // shortcut: on Android 8 photos ignore their EXIF rotation; add androidx.exifinterface if that matters.
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
}
