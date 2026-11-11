package com.shuva.jotter.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.MessageDigest

/** A link's preview ready to show: the page's description and a small picture, either may be missing. */
class LoadedPreview(val description: String?, val image: Bitmap?)

/**
 * Link previews kept in the app's cache folder: each address is fetched once, then read from disk. They aren't
 * synced (each phone fetches its own), and Android may clear them, in which case they're fetched again.
 * A page that couldn't be reached (offline) isn't remembered, so it's tried again next time.
 */
class LinkPreviews(cacheDir: File) {
    private val dir = File(cacheDir, "link-previews")

    /** The preview for [url], from the cache or the web. Null if the page couldn't be reached. Call off the main thread. */
    fun load(url: String): LoadedPreview? {
        val key = key(url)
        val meta = File(dir, "$key.json")
        val picture = File(dir, "$key.png")
        val preview = runCatching { JotterJson.decodeFromString<PagePreview>(meta.readText()) }.getOrNull()
            ?: fetch(url, meta, picture) ?: return null
        val image = if (preview.image != null && picture.exists()) runCatching { BitmapFactory.decodeFile(picture.path) }.getOrNull() else null
        return LoadedPreview(preview.description, image)
    }

    private fun fetch(url: String, meta: File, picture: File): PagePreview? {
        val preview = pagePreview(fetchHtml(url) ?: return null, url)
        dir.mkdirs()
        // A picture that won't download or decode is left out; the description still shows.
        val saved = preview.image?.let(::fetchImage)?.let { thumbnail(it) }?.let { bmp ->
            runCatching { picture.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }.isSuccess
        } == true
        val kept = if (saved) preview else preview.copy(image = null)
        runCatching { meta.writeText(JotterJson.encodeToString(kept)) }
        return kept
    }

    /** [bytes] decoded no larger than [side] px on its short side, which is plenty for the card. */
    private fun thumbnail(bytes: ByteArray, side: Int = 240): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun key(url: String) = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
