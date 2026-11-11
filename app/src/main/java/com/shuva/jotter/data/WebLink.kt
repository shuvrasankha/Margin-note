package com.shuva.jotter.data

import java.net.HttpURLConnection
import java.net.URI

/** A web link is an attachment whose file holds just the URL; its name is the page title. */
const val LINK_MIME = "text/uri-list"

val Attachment.isLink get() = mime == LINK_MIME

/** What the user typed as a web address: "https://" is added when there's no scheme. Null unless it's http(s) with a host. */
fun normalizeUrl(input: String): String? {
    val t = input.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return null
    val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t)) t else "https://$t"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() || '.' !in uri.host) return null
    return withScheme
}

fun hostOf(url: String) = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url

/** The text of the page's <title>, with whitespace collapsed and common entities decoded. */
fun pageTitle(html: String): String? {
    val raw = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)
        ?: return null
    return tidy(raw, 200)
}

/** HTML text with common entities decoded (once) and whitespace collapsed, cut to [max] characters; null if empty. */
private fun tidy(raw: String, max: Int): String? = raw
    .replace(Regex("&#(\\d{1,6});")) { m -> m.groupValues[1].toInt().takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: "" }
    .replace(Regex("&#[xX]([0-9a-fA-F]{1,6});")) { m -> m.groupValues[1].toInt(16).takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: "" }
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&nbsp;", " ").replace("&amp;", "&")
    .replace(Regex("\\s+"), " ").trim().take(max).ifEmpty { null }

/** What a page says about itself for link previews: a short description and a picture's address (absolute, http(s)). */
@kotlinx.serialization.Serializable
data class PagePreview(val description: String? = null, val image: String? = null)

/** Reads the Open Graph / Twitter / plain meta tags of [html]; relative picture addresses are resolved against [pageUrl]. */
fun pagePreview(html: String, pageUrl: String): PagePreview {
    val meta = HashMap<String, String>()
    val attr = Regex("([a-zA-Z_:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).forEach { tag ->
        val attrs = attr.findAll(tag.value).associate { m ->
            m.groupValues[1].lowercase() to (m.groups[2] ?: m.groups[3] ?: m.groups[4])!!.value
        }
        val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: return@forEach
        val content = attrs["content"] ?: return@forEach
        meta.putIfAbsent(key, content) // the first of each wins, as browsers do
    }
    val description = listOf("og:description", "twitter:description", "description").firstNotNullOfOrNull { meta[it]?.let { d -> tidy(d, 300) } }
    val image = listOf("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src").firstNotNullOfOrNull { k ->
        meta[k]?.let { tidy(it, 2000) }?.let { raw ->
            runCatching { URI(pageUrl).resolve(raw.replace(" ", "%20")) }.getOrNull()
                ?.takeIf { it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank() }?.toString()
        }
    }
    return PagePreview(description, image)
}

/**
 * Fetches [url] and reads its title from the first 256 KB. Null on any failure (offline, not HTML, timeout).
 * Call off the main thread.
 */
fun fetchPageTitle(url: String): String? = fetchHtml(url)?.let(::pageTitle)

/** The first 256 KB of the page at [url] as text, or null if it isn't reachable HTML. Call off the main thread. */
fun fetchHtml(url: String): String? = fetch(url, "text/html", 256 * 1024) { type -> type.contains("html", ignoreCase = true) }?.let { (bytes, type) ->
    val charset = Regex("charset=([\\w-]+)").find(type)?.groupValues?.get(1)
    String(bytes, runCatching { charset(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8))
}

/** A picture at [url], at most [max] bytes, or null. Call off the main thread. */
fun fetchImage(url: String, max: Int = 3_000_000): ByteArray? =
    fetch(url, "image/*", max, whole = true) { type -> type.startsWith("image/", ignoreCase = true) }?.first

/**
 * GETs [url] (following up to 5 redirects, including http→https, http(s) only) and returns its body, up to [max]
 * bytes, with its content type. Null on failure, a type [accept] refuses, or (with [whole]) a body over [max].
 */
private fun fetch(url: String, acceptHeader: String, max: Int, whole: Boolean = false, accept: (String) -> Boolean): Pair<ByteArray, String>? = runCatching {
    var target = URI(url)
    repeat(6) {
        if (target.scheme?.lowercase() !in setOf("http", "https")) return@runCatching null
        val conn = target.toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", acceptHeader)
            val code = conn.responseCode
            if (code in 300..399) {
                target = target.resolve(conn.getHeaderField("Location") ?: return@runCatching null)
                return@repeat
            }
            val type = conn.contentType.orEmpty()
            if (code !in 200..299 || !accept(type)) return@runCatching null
            if (whole && conn.contentLengthLong > max) return@runCatching null
            val bytes = conn.inputStream.use { it.readNBytesCompat(if (whole) max + 1 else max) }
            if (whole && bytes.size > max) return@runCatching null
            return@runCatching bytes to type
        } finally {
            conn.disconnect()
        }
    }
    null
}.getOrNull()

/** InputStream.readNBytes needs API 33. */
private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
