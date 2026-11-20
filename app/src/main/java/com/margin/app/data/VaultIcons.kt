package com.margin.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Icons for vault entries, kept as small PNGs inside the encrypted entry so no list of sites sits on disk
 * in the clear. A site's icon comes from the site itself, never a third-party favicon service.
 */
object VaultIcons {
    private const val SIZE = 96
    private const val MAX_BYTES = 512 * 1024
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).followRedirects(true).build()
    }

    /** An installed app's icon, or null when it isn't installed (or visible to Margin). */
    fun ofApp(context: Context, pkg: String): String? =
        runCatching { encode(context.packageManager.getApplicationIcon(pkg)) }.getOrNull()

    /** The app's name, or null. */
    fun appLabel(context: Context, pkg: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    /** Whether [pkg] is a web browser, whose reported web addresses can be trusted. */
    fun isBrowser(context: Context, pkg: String): Boolean = runCatching {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        context.packageManager.queryIntentActivities(view, PackageManager.MATCH_ALL).any { it.activityInfo.packageName == pkg }
    }.getOrDefault(false)

    /** [site]'s icon: its apple-touch-icon or the icon its home page names, else /favicon.ico. Null offline. */
    suspend fun ofSite(site: String): String? {
        val host = siteOf(site).takeIf { it.isNotEmpty() && '.' in it } ?: return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(10_000) {
                val base = "https://$host"
                val named = runCatching { fetch(base)?.let { iconLinks(String(it, Charsets.UTF_8), base) } }.getOrNull().orEmpty()
                (named + listOf("$base/apple-touch-icon.png", "$base/favicon.ico")).distinct().firstNotNullOfOrNull { url ->
                    runCatching { fetch(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.let(::encode) }.getOrNull()
                }
            }
        }
    }

    /** The icon a page names, biggest kinds first. */
    internal fun iconLinks(html: String, base: String): List<String> {
        val links = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html.take(200_000)).map { it.value }.toList()
        fun attr(tag: String, name: String) =
            Regex("\\b$name\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
        return links.mapNotNull { tag ->
            val rel = attr(tag, "rel")?.lowercase() ?: return@mapNotNull null
            val href = attr(tag, "href")?.takeIf { !it.endsWith(".svg", ignoreCase = true) } ?: return@mapNotNull null
            val rank = when { "apple-touch-icon" in rel -> 0; "icon" in rel.split(' ') -> 1; else -> return@mapNotNull null }
            rank to absolute(href, base)
        }.sortedBy { it.first }.mapNotNull { it.second }
    }

    private fun absolute(href: String, base: String): String? = when {
        href.startsWith("https://") -> href
        href.startsWith("//") -> "https:$href"
        href.startsWith("/") -> base + href
        href.startsWith("http://") || href.startsWith("data:") -> null // no plain-http or inline icons
        else -> "$base/$href"
    }

    private fun fetch(url: String): ByteArray? = http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) Margin").build()).execute().use { r ->
        if (!r.isSuccessful || !r.request.url.isHttps) return null
        val body = r.body ?: return null
        if (body.contentLength() > MAX_BYTES) return null
        body.byteStream().use { s -> s.readNBytesCompat(MAX_BYTES + 1) }.takeIf { it.size <= MAX_BYTES }
    }

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val out = ByteArrayOutputStream(); val buf = ByteArray(8192); var total = 0
        while (total < n) { val k = read(buf, 0, minOf(buf.size, n - total)); if (k < 0) break; out.write(buf, 0, k); total += k }
        return out.toByteArray()
    }

    private fun encode(d: Drawable): String {
        val b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, SIZE, SIZE); d.draw(Canvas(b))
        return png(b)
    }

    private fun encode(src: Bitmap): String? {
        if (src.width < 16 || src.height < 16) return null // a 1×1 placeholder isn't an icon
        return png(Bitmap.createScaledBitmap(src, SIZE, SIZE, true))
    }

    private fun png(b: Bitmap): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** A stored icon as a bitmap, or null. */
    fun decode(icon: String): Bitmap? = if (icon.isEmpty()) null else runCatching {
        val bytes = Base64.decode(icon, Base64.NO_WRAP); BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
