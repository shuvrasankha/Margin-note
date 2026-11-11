package com.shuva.jotter.data

import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A grammar mistake: [start] until [end] in the text is [original]; [message] says why, [replacements] fix it. */
data class GrammarIssue(val start: Int, val end: Int, val original: String, val message: String, val replacements: List<String>)

/** The issues still in [text] at the same place (the text may have changed since they were found). */
fun List<GrammarIssue>.stillIn(text: String) = filter { g -> g.end <= text.length && text.regionMatches(g.start, g.original, 0, g.original.length) }

/*
 * Offline English rules: mistakes a pattern can catch reliably, so they rarely flag good writing.
 * Each works on one line at a time. Lines with "=" (math), tags and [[links]] are left alone.
 */

private class Rule(val pattern: Regex, val message: String, val fix: (MatchResult) -> String?)

private fun rule(pattern: String, message: String, fix: (MatchResult) -> String?) =
    Rule(Regex(pattern, setOf(RegexOption.IGNORE_CASE)), message, fix)

private val ABBREVIATIONS = setOf("e.g", "i.e", "etc", "vs", "mr", "mrs", "ms", "dr", "st", "approx", "no", "fig", "p", "pp", "cf", "al")

/** Words with a vowel letter but a consonant sound ("a user"), and the reverse ("an hour"). */
private val A_BEFORE = listOf("uni", "use", "usu", "uti", "ure", "uro", "eu", "one", "once", "ubiq", "ukulele", "ufo")
private val AN_BEFORE = listOf("hour", "honest", "honor", "honour", "heir")

private fun wantsAn(word: String): Boolean {
    // Acronyms go by how the first letter sounds: an FBI agent, a NASA engineer (close enough for 2+ capitals).
    if (word.length >= 2 && word.all { it.isUpperCase() }) return word[0] in "AEFHILMNORSX"
    val w = word.lowercase()
    if (AN_BEFORE.any { w.startsWith(it) }) return true
    if (A_BEFORE.any { w.startsWith(it) }) return false
    return w[0] in "aeiou"
}

private val APOSTROPHES = mapOf(
    "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't", "isnt" to "isn't", "wasnt" to "wasn't",
    "arent" to "aren't", "werent" to "weren't", "cant" to "can't", "couldnt" to "couldn't", "wouldnt" to "wouldn't",
    "shouldnt" to "shouldn't", "havent" to "haven't", "hasnt" to "hasn't", "hadnt" to "hadn't", "im" to "I'm",
    "ive" to "I've", "youre" to "you're", "theyre" to "they're", "thats" to "that's", "whats" to "what's",
)

private val RULES = listOf(
    rule("""\b(\p{L}+)(\s+)\1\b""", "This word is repeated.") { m ->
        m.groupValues[1].takeIf { it.lowercase() !in setOf("that", "had") }
    },
    rule("""\b(a|an)\s+([\p{L}][\p{L}'’-]*)""", "") { m -> // message set below
        val article = m.groupValues[1]
        val word = m.groupValues[2]
        // "Vitamin A and C": a capital A mid-sentence is a name, not the article.
        if (article == "A" && m.range.first > 0) return@rule null
        val an = wantsAn(word)
        if (an == (article.lowercase() == "an")) null else (if (an) "an" else "a").matchCase(article) + m.value.substring(article.length)
    },
    rule("""\b(could|should|would|must|might) of\b""", "Use “have” after could, should, would.") { m -> "${m.groupValues[1]} have" },
    rule("""\bits (a|an|the|not|been|going|getting|just|always|never|ok|okay|too|very|so)\b""", "Did you mean “it's” (it is)?") { m -> "it's ${m.groupValues[1]}".matchCase(m.value) },
    rule("""\bit['’]s own\b""", "Use “its” for belonging to it.") { m -> "its own".matchCase(m.value) },
    rule("""\byour (welcome|going|gonna|not|coming)\b""", "Did you mean “you're” (you are)?") { m -> "you're ${m.groupValues[1]}".matchCase(m.value) },
    rule(
        """\b(more|less|better|worse|rather|other|greater|bigger|smaller|faster|slower|higher|lower|larger|older|younger|easier|harder) then\b""",
        "Use “than” to compare.",
    ) { m -> "${m.groupValues[1]} than" },
    rule("""\btheir (is|are|was|were)\b""", "Did you mean “there”?") { m -> "there ${m.groupValues[1]}".matchCase(m.value) },
    rule("""\balot\b""", "“A lot” is two words.") { m -> "a lot".matchCase(m.value) },
    rule("""\b(he|she|it) don['’]t\b""", "Use “doesn't” with he, she or it.") { m -> "${m.groupValues[1]} doesn't" },
    rule("""\b(i|you|we|they) doesn['’]t\b""", "Use “don't” with I, you, we or they.") { m -> "${m.groupValues[1].fixI()} don't" },
    rule("""\b(we|they|you) was\b""", "Use “were” with we, you or they.") { m -> "${m.groupValues[1]} were" },
    rule("""\b(i|we|they|you) has\b""", "Use “have” with I, you, we or they.") { m -> "${m.groupValues[1].fixI()} have" },
    rule("""\b(${APOSTROPHES.keys.joinToString("|")})\b""", "Missing apostrophe.") { m ->
        if (m.value.all { it.isUpperCase() }) return@rule null // IM, CANT: probably an abbreviation
        APOSTROPHES.getValue(m.value.lowercase()).let { if (it.startsWith("I'")) it else it.matchCase(m.value) }
    },
    // "i" on its own, and i'm / i've / i'd / i'll (not the i in "i.e." or a file path).
    rule("""(?<![\p{L}\p{N}'’./\\-])i(?=(?:['’](?:m|ve|d|ll))?(?![\p{L}\p{N}.'’/\\-]))""", "Write “I” as a capital.") { m -> "I".takeIf { m.value == "i" } },
    rule("""(?<=\p{L})(\s+)([,;!?]|\.(?!\.))(?=\s|$)""", "No space before punctuation.") { m -> m.groupValues[2] },
)

/** First letter of a sentence: after . ! or ? within a line (line starts are often list items, so they're left). */
private val SENTENCE = Regex("""([\p{L}\p{N})'"’”]+)([.!?])(\s+)(\p{Ll}[\p{L}'’-]*)""")

private fun String.matchCase(like: String) = if (like.firstOrNull()?.isUpperCase() == true) replaceFirstChar { it.uppercaseChar() } else this
private fun String.fixI() = if (this == "i") "I" else this

/** Grammar mistakes in [text] found by the offline rules, in order. */
fun grammarRules(text: String): List<GrammarIssue> {
    val found = mutableListOf<GrammarIssue>()
    var lineStart = 0
    for (line in text.split('\n')) {
        if ('=' !in line) {
            for (r in RULES) for (m in r.pattern.findAll(line)) {
                val fixed = r.fix(m) ?: continue
                if (fixed == m.value) continue
                val message = r.message.ifEmpty { if (fixed.startsWith("an", ignoreCase = true)) "Use “an” before a vowel sound." else "Use “a” before a consonant sound." }
                found += GrammarIssue(lineStart + m.range.first, lineStart + m.range.last + 1, m.value, message, listOf(fixed))
            }
            for (m in SENTENCE.findAll(line)) {
                val before = m.groupValues[1].lowercase()
                if (m.groupValues[2] == "." && (before in ABBREVIATIONS || before.length == 1 && before[0].isLetter())) continue
                val word = m.groups[4]!!
                found += GrammarIssue(lineStart + word.range.first, lineStart + word.range.last + 1, word.value, "Start a sentence with a capital letter.", listOf(word.value.replaceFirstChar { it.uppercaseChar() }))
            }
        }
        lineStart += line.length + 1
    }
    return withoutTagsAndLinks(text, found).sortedBy { it.start }.dropOverlaps()
}

/** Leaves out issues on a #tag or inside [[a link]]. */
private fun withoutTagsAndLinks(text: String, issues: List<GrammarIssue>): List<GrammarIssue> {
    val skip = findTags(text).map { it.first } + findLinks(text).map { it.first }
    return issues.filter { g -> skip.none { r -> g.start <= r.last && g.end > r.first } }
}

private fun List<GrammarIssue>.dropOverlaps(): List<GrammarIssue> {
    val out = mutableListOf<GrammarIssue>()
    for (g in this) if (out.none { it.start < g.end && g.start < it.end }) out += g
    return out
}

/** [first], plus the issues from [more] that don't overlap one of them. */
fun mergeIssues(first: List<GrammarIssue>, more: List<GrammarIssue>) = (first + more).dropOverlaps().sortedBy { it.start }

/*
 * LanguageTool (languagetool.org), the optional cloud check: much better at grammar, but the text leaves the
 * phone, so it only runs when switched on in Settings, and never for locked notes.
 * The free API allows 20 requests a minute and 20 KB of text per request.
 */
object LanguageTool {
    private const val URL_CHECK = "https://api.languagetool.org/v2/check"
    const val MAX_CHARS = 18_000

    /** The issues in [text] (only its first [MAX_CHARS] are checked). Blocking; throws on network errors. */
    fun check(text: String): List<GrammarIssue> {
        val sent = text.take(MAX_CHARS)
        val body = "text=" + URLEncoder.encode(sent, "UTF-8") + "&language=auto&preferredVariants=en-US,de-DE,pt-BR,ca-ES"
        val conn = URL(URL_CHECK).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) error("LanguageTool: HTTP ${conn.responseCode}")
            return parseLanguageTool(conn.inputStream.bufferedReader().use { it.readText() }, sent)
        } finally {
            conn.disconnect()
        }
    }
}

@Serializable private data class LtResponse(val matches: List<LtMatch> = emptyList())
@Serializable private data class LtMatch(
    val message: String = "",
    val shortMessage: String = "",
    val offset: Int,
    val length: Int,
    val replacements: List<LtReplacement> = emptyList(),
    val rule: LtRule? = null,
)
@Serializable private data class LtReplacement(val value: String = "")
@Serializable private data class LtRule(val issueType: String = "")

/** LanguageTool's answer for [text] as issues. Spelling is left to the phone's spell checker. */
internal fun parseLanguageTool(json: String, text: String): List<GrammarIssue> {
    val issues = JotterJson.decodeFromString<LtResponse>(json).matches.mapNotNull { m ->
        if (m.rule?.issueType == "misspelling") return@mapNotNull null
        val end = m.offset + m.length
        if (m.offset < 0 || m.length <= 0 || end > text.length) return@mapNotNull null
        val lineStart = text.lastIndexOf('\n', m.offset - 1) + 1
        val lineEnd = text.indexOf('\n', m.offset).let { if (it < 0) text.length else it }
        if ('=' in text.substring(lineStart, lineEnd)) return@mapNotNull null // math
        GrammarIssue(m.offset, end, text.substring(m.offset, end), m.message.ifBlank { m.shortMessage }, m.replacements.map { it.value }.filter { it.isNotEmpty() }.take(3))
    }
    return withoutTagsAndLinks(text, issues)
}
