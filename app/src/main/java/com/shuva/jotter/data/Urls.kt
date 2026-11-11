package com.shuva.jotter.data

/**
 * A web address written in the text: one starting with http(s):// or www., or a bare domain with a common
 * ending ("github.com/me"), so file names like "notes.txt" don't count.
 */
private val URL = Regex(
    """(?<![\w@/.-])(?:https?://[^\s<>"]+|www\.[^\s<>"]+|[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)*\.(?:com|org|net|io|dev|app|co|in|edu|gov|me|ai|info|xyz|uk|us)\b(?:[/?#][^\s<>"]*)?)""",
    RegexOption.IGNORE_CASE,
)

/** Each web address's range and the URL to open (with "https://" added when it has no scheme). */
fun findUrls(text: String): List<Pair<IntRange, String>> = URL.findAll(text).mapNotNull { m ->
    var s = m.value
    // Punctuation after an address ends the sentence, not the address; a ")" only belongs when it closes a "(" inside.
    while (s.isNotEmpty()) {
        val last = s.last()
        s = when {
            last in ".,;:!?'\"" -> s.dropLast(1)
            last == ')' && s.count { it == '(' } < s.count { it == ')' } -> s.dropLast(1)
            else -> break
        }
    }
    if (s.isEmpty() || s.endsWith("://")) return@mapNotNull null
    val url = normalizeUrl(s) ?: return@mapNotNull null
    IntRange(m.range.first, m.range.first + s.length - 1) to url
}.toList()
