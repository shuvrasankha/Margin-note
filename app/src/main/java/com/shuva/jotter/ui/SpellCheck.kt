package com.shuva.jotter.ui

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * A word the spell checker flagged: [start] until [end] in the text, and what it might have meant. [grammar]
 * when the checker called it a grammar slip rather than a typo (Gboard does for "a apple", "the the").
 */
data class Misspelling(val start: Int, val end: Int, val word: String, val suggestions: List<String>, val grammar: Boolean = false)

/**
 * The words in [text] that the phone's spell checker (the one chosen in Android settings) thinks are typos.
 * Checked after a pause in typing; until then the earlier results stay, minus any word that has changed.
 * Empty when [enabled] is false or spell checking is off on the phone.
 */
@Composable
fun rememberMisspellings(text: String, enabled: Boolean): List<Misspelling> {
    val context = LocalContext.current
    var found by remember { mutableStateOf(emptyList<Misspelling>()) }
    var checked by remember { mutableStateOf("") } // the text [found] belongs to
    val session = remember { SpellSession(context) { t, list -> checked = t; found = list } }
    DisposableEffect(session) { onDispose { session.close() } }
    LaunchedEffect(text, enabled) {
        if (!enabled) { found = emptyList(); return@LaunchedEffect }
        delay(600)
        session.check(text)
    }
    if (!enabled) return emptyList()
    return if (checked == text) found else found.stillIn(text)
}

/** The flagged words that are still in [text] at the same place. */
internal fun List<Misspelling>.stillIn(text: String) = filter { m -> m.end <= text.length && text.regionMatches(m.start, m.word, 0, m.word.length) }

/**
 * The flagged [ranges] (offset, length, suggestions) in [text], leaving out tags ("#word"), words in a
 * "[[note link]]" and lines with "=" (math), which aren't meant to be dictionary words.
 */
internal fun misspellingsIn(text: String, ranges: List<Triple<Int, Int, List<String>>>, grammarAt: Set<Int> = emptySet()): List<Misspelling> = ranges.mapNotNull { (start, length, suggestions) ->
    val end = start + length
    if (start < 0 || end > text.length || length <= 0) return@mapNotNull null
    if (start > 0 && text[start - 1] == '#') return@mapNotNull null
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    // Math lines ("x^2 + x - 6 = 0", "y = 3sin(x)") aren't words.
    val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
    if ('=' in text.substring(lineStart, lineEnd)) return@mapNotNull null
    val open = text.lastIndexOf("[[", start)
    if (open >= lineStart && text.indexOf("]]", open).let { it < 0 || it >= start }) return@mapNotNull null
    Misspelling(start, end, text.substring(start, end), suggestions, grammar = start in grammarAt)
}

/** SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR (API 31); older checkers never set it. */
private const val LOOKS_LIKE_GRAMMAR_ERROR = 0x0008

private class SpellSession(context: Context, private val onResult: (String, List<Misspelling>) -> Unit) : SpellCheckerSession.SpellCheckerSessionListener {
    // Null when the phone has spell checking turned off.
    private val session: SpellCheckerSession? = context.getSystemService(TextServicesManager::class.java)
        ?.newSpellCheckerSession(null, null, this, true)
    private var pending = ""
    private var sequence = 0

    fun check(text: String) {
        val s = session
        if (s == null || text.isBlank()) return onResult(text, emptyList()) // the checker rejects empty text
        pending = text
        sequence++
        // The checker treats '\n' as a word break, so the whole note goes in one request.
        s.getSentenceSuggestions(arrayOf(TextInfo(text, 0, sequence)), 3)
    }

    fun close() { session?.close() }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) = Unit

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        val info = results?.firstOrNull() ?: return
        // An answer to an older request (the text has changed since): a newer one is on its way.
        if (info.suggestionsCount > 0 && info.getSuggestionsInfoAt(0)?.sequence != sequence) return
        val grammarAt = mutableSetOf<Int>()
        val ranges = (0 until info.suggestionsCount).mapNotNull { i ->
            val s = info.getSuggestionsInfoAt(i) ?: return@mapNotNull null
            // Gboard reports some common slips ("teh") as grammar errors rather than typos; both get flagged.
            if (s.suggestionsAttributes and (SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or LOOKS_LIKE_GRAMMAR_ERROR) == 0) return@mapNotNull null
            if (s.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY != 0) return@mapNotNull null
            if (s.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO == 0) grammarAt += info.getOffsetAt(i)
            Triple(info.getOffsetAt(i), info.getLengthAt(i), (0 until maxOf(s.suggestionsCount, 0)).map { s.getSuggestionAt(it) })
        }
        onResult(pending, misspellingsIn(pending, ranges, grammarAt))
    }
}
