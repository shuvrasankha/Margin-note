package com.margin.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

private val SPACE = Regex("\\s+")

/** Words in [text]: runs of non-spaces with at least one letter or digit, so a lone "-" or "•" doesn't count. */
fun countWords(text: String): Int = text.split(SPACE).count { w -> w.any { it.isLetterOrDigit() } }

/** The note's body as words are counted: everything but the title, with files and photos left out. */
fun bodyText(blocks: List<Block>): String = blocks.drop(1).filter { it.attachment == null }.joinToString("\n") { it.plainText }

fun noteWords(blocks: List<Block>) = countWords(bodyText(blocks))

/** Minutes to read [words] at an everyday 230 words a minute, rounded up. */
fun readingMinutes(words: Int): Int = if (words == 0) 0 else ceil(words / 230.0).toInt()

/** "Under a minute", "3 min read". */
fun readingLabel(words: Int): String = readingMinutes(words).let { if (it <= 1) "Under a minute" else "$it min read" }

/** Paragraphs: lines of the body with any text on them. */
fun paragraphs(blocks: List<Block>) = blocks.drop(1).count { it.attachment == null && it.plainText.isNotBlank() }

/**
 * Days in a row the word goal was reached, ending today or, if today's goal isn't met yet, yesterday
 * (so the streak doesn't look broken first thing in the morning).
 */
fun goalStreak(words: Map<LocalDate, Int>, goal: Int, today: LocalDate): Int {
    if (goal <= 0) return 0
    var day = if ((words[today] ?: 0) >= goal) today else today.minusDays(1)
    var n = 0
    while ((words[day] ?: 0) >= goal) { n++; day = day.minusDays(1) }
    return n
}

/**
 * Words written each day, across every note, for the daily goal. Each save adds the change in the note's word count
 * (net, so pasting something in and taking it out again counts nothing). Kept on this phone for 90 days.
 */
@Singleton
class WritingLog @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("writing_stats", Context.MODE_PRIVATE)

    private val _goal = MutableStateFlow(prefs.getInt("goal", 0))
    /** Words a day to aim for; 0 for no goal. */
    val goal: StateFlow<Int> = _goal

    private val _showCount = MutableStateFlow(prefs.getBoolean("show", false))
    /** Whether the editor shows the word count under the note. */
    val showCount: StateFlow<Boolean> = _showCount

    private val _days = MutableStateFlow(read())
    /** Net words written each day. */
    val days: StateFlow<Map<LocalDate, Int>> = _days

    fun setGoal(words: Int) { prefs.edit().putInt("goal", words.coerceAtLeast(0)).apply(); _goal.value = words.coerceAtLeast(0) }

    fun setShowCount(on: Boolean) { prefs.edit().putBoolean("show", on).apply(); _showCount.value = on }

    fun record(delta: Int, day: LocalDate = LocalDate.now()) {
        if (delta == 0) return
        val e = prefs.edit()
        val total = prefs.getInt(key(day), 0) + delta
        e.putInt(key(day), total)
        val oldest = day.minusDays(90)
        prefs.all.keys.filter { it.startsWith("day:") }.forEach { k -> parse(k)?.takeIf { it.isBefore(oldest) }?.let { e.remove(k) } }
        e.apply()
        _days.value = _days.value.filterKeys { !it.isBefore(oldest) } + (day to total)
    }

    private fun key(day: LocalDate) = "day:$day"
    private fun parse(k: String) = runCatching { LocalDate.parse(k.removePrefix("day:")) }.getOrNull()
    private fun read() = prefs.all.mapNotNull { (k, v) -> if (k.startsWith("day:") && v is Int) parse(k)?.let { it to v } else null }.toMap()
}
