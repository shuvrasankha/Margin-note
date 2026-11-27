package com.margin.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** What a spoken (or typed) phrase asks Margin to do. */
sealed interface VoiceCommand {
    /** "Remind me to call Sam tomorrow at 5": a note titled [what] that rings at [at]. */
    data class Remind(val what: String, val at: LocalDateTime, val repeat: Repeat) : VoiceCommand
    /** "Add milk and eggs to my shopping list": tasks on the to-do list called [list]. */
    data class AddToList(val list: String, val items: List<String>) : VoiceCommand
    /** Anything else: kept as a note. */
    data class Note(val text: String) : VoiceCommand
}

/**
 * Reads phrases the way a speech recogniser writes them ("5 p.m.", "5:30 PM", "17:00", "noon", "tonight").
 * Times are found only where they look like times, so "meet Sam at the station at 6" keeps "at the station".
 * A reminder is never set in the past: a bare "at 5" is the next 5 o'clock to come.
 */
object VoiceCommands {
    /** The hour a day said without a time rings at ("tomorrow", "on Friday"). */
    private const val DEFAULT_HOUR = 9

    /**
     * What [spoken] asks for. With a [list] (said into a list widget), anything that isn't another command
     * ("milk and eggs") goes on that list.
     */
    fun parse(spoken: String, now: LocalDateTime, list: String? = null): VoiceCommand? {
        val command = parse(spoken, now)
        if (list == null || command !is VoiceCommand.Note) return command
        val items = clean(unprefixed(spoken)).trimEnd('.', '!', '?', ' ')
            .replace(Regex("^(?:add|put|buy|get)\\s+", RegexOption.IGNORE_CASE), "")
        return (list(list, items) as? VoiceCommand.AddToList)?.copy(list = list) ?: command
    }

    private fun parse(spoken: String, now: LocalDateTime): VoiceCommand? {
        // A note keeps what was said as it was said; commands read times written one way and no closing full stop.
        val said = unprefixed(spoken)
        val text = clean(said).trimEnd('.', '!', '?', ' ')
        if (text.isEmpty()) return null
        REMIND.find(text)?.let { m -> return reminder(m.groupValues[1], now) }
        LIST_TAIL.find(text)?.let { m -> return list(m.groupValues[2], m.groupValues[1]) }
        LIST_HEAD.find(text)?.let { m -> return list(m.groupValues[1], m.groupValues[2]) }
        BUY.find(text)?.let { m -> return list("shopping", m.groupValues[1]) }
        NOTE.find(said)?.let { m -> return m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { VoiceCommand.Note(capital(it)) } }
        return VoiceCommand.Note(capital(said))
    }

    /** The list a spoken name means, as its note title: "shopping" → "Shopping list", "to do" → "To-do list". */
    fun listTitle(name: String): String {
        val key = listKey(name)
        return if (key == "todo") "To-do list" else capital("$key list")
    }

    /** What two list names have in common when they mean the same list: no "my", "the" or "list", no spaces. */
    fun listKey(name: String): String = name.lowercase()
        .replace(Regex("\\b(my|the|our|a|list)\\b"), " ")
        .replace(Regex("to[ -]?do"), "todo")
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .trim().replace(Regex("\\s+"), " ")

    /** [s] without what is said before a command ("Hey Google, can you please…"), however many of those. */
    private fun unprefixed(s: String): String {
        var t = s.trim()
        while (true) PREFIX.find(t)?.let { t = t.substring(it.range.last + 1) } ?: return t
    }

    /** Times written one way for matching: "5 p.m." → "5 pm", "5 o'clock" → "5". */
    private fun clean(s: String) = s
        .replace(Regex("\\b([ap])\\.\\s?m\\.?", RegexOption.IGNORE_CASE)) { "${it.groupValues[1].lowercase()}m" }
        .replace(Regex("\\s*o'?clock\\b", RegexOption.IGNORE_CASE), "")

    private fun list(name: String, items: String): VoiceCommand? {
        val parts = items.split(Regex(",\\s*(?:and\\s+)?|\\s+and\\s+|\\s*&\\s*"))
            .map { it.trim().removePrefix("some ").trim() }.filter { it.isNotEmpty() }.map(::capital)
        val title = listTitle(name)
        if (parts.isEmpty() || listKey(name).isEmpty()) return null
        return VoiceCommand.AddToList(title, parts)
    }

    private fun reminder(rest: String, now: LocalDateTime): VoiceCommand.Remind {
        var s = " $rest "
        fun take(r: Regex): MatchResult? = r.find(s)?.also { s = s.replaceRange(it.range, " ") }

        var repeat = Repeat.NEVER
        var weekday: DayOfWeek? = null
        var date: LocalDate? = null
        var part: Int? = null // the hour a part of the day ("evening") stands for
        var time: LocalTime? = null
        var meridiemFromPart = false

        take(EVERY)?.let { m ->
            val w = m.groupValues[1].lowercase()
            when {
                w.startsWith("day") || w == "morning" || w == "night" || w == "evening" || w == "afternoon" -> {
                    repeat = Repeat.DAILY; PARTS[w]?.let { part = it; meridiemFromPart = true }
                }
                w == "week" -> repeat = Repeat.WEEKLY
                w == "month" -> repeat = Repeat.MONTHLY
                w == "year" -> repeat = Repeat.YEARLY
                else -> { repeat = Repeat.WEEKLY; weekday = dayOf(w) }
            }
        }
        take(EVERY_WORD)?.let { m ->
            repeat = when (m.value.trim().lowercase()) { "daily" -> Repeat.DAILY; "weekly" -> Repeat.WEEKLY; "monthly" -> Repeat.MONTHLY; else -> Repeat.YEARLY }
        }

        val relative = take(IN)?.let { m ->
            val n = when (val a = m.groupValues[1].lowercase()) { "a", "an", "one" -> 1L; "half an", "half a" -> 0L; else -> a.toLongOrNull() ?: wordNumber(a)?.toLong() ?: 1L }
            val unit = m.groupValues[2].lowercase()
            if (m.groupValues[1].lowercase().startsWith("half")) now.plusMinutes(if (unit.startsWith("h")) 30 else 0)
            else when {
                unit.startsWith("min") -> now.plusMinutes(n)
                unit.startsWith("h") -> now.plusHours(n)
                unit.startsWith("d") -> now.plusDays(n)
                else -> now.plusWeeks(n)
            }
        }

        take(DAY_AFTER)?.let { date = now.toLocalDate().plusDays(2) }
        take(TOMORROW)?.let { m -> date = now.toLocalDate().plusDays(1); PARTS[m.groupValues[1].lowercase()]?.let { part = it; meridiemFromPart = true } }
        take(TONIGHT)?.let { date = now.toLocalDate(); part = 20; meridiemFromPart = true }
        take(TODAY)?.let { date = now.toLocalDate() }
        take(THIS_PART)?.let { m -> date = now.toLocalDate(); part = PARTS[m.groupValues[1].lowercase()]; meridiemFromPart = true }
        take(NEXT_WEEK)?.let { date = now.toLocalDate().plusWeeks(1) }
        take(WEEKDAY)?.let { m -> weekday = dayOf(m.groupValues[2]) }
        take(MONTH_DAY)?.let { m -> date = monthDay(m.groupValues[1], m.groupValues[2], now.toLocalDate()) }
            ?: take(DAY_MONTH)?.let { m -> date = monthDay(m.groupValues[2], m.groupValues[1], now.toLocalDate()) }

        take(NOON)?.let { m -> time = if ("midnight" in m.value.lowercase()) LocalTime.MIDNIGHT else LocalTime.NOON }
        var ambiguous: Pair<Int, Int>? = null // hour (1..12) and minute said without am/pm
        if (time == null) (take(CLOCK_AT) ?: take(CLOCK_MERIDIEM) ?: take(CLOCK_24))?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: wordNumber(m.groupValues[1]) ?: 0
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val mer = m.groupValues[3].lowercase()
            val trailing = PARTS[m.groupValues[4].lowercase()]
            if (trailing != null) { part = trailing; meridiemFromPart = true }
            when {
                h > 23 || min > 59 -> Unit
                mer == "am" -> time = LocalTime.of(h % 12, min)
                mer == "pm" -> time = LocalTime.of(h % 12 + 12, min)
                h == 0 || h > 12 -> time = LocalTime.of(h % 24, min)
                else -> ambiguous = h to min
            }
        }
        // "in the evening" said apart from the time ("at 7 ... in the evening").
        take(IN_PART)?.let { m -> part = PARTS[m.groupValues[1].lowercase()]; meridiemFromPart = true }

        val what = capital(
            s.replace(Regex("\\s+"), " ").trim()
                .replace(Regex("^(?:(?:me|to|that|about|for|of)\\s+)+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+(?:at|on|by|in|to)$", RegexOption.IGNORE_CASE), "")
                .trim(',', ' ', '.'),
        ).ifEmpty { "Reminder" }

        if (relative != null) return VoiceCommand.Remind(what, relative.truncatedTo(ChronoUnit.MINUTES), repeat)

        ambiguous?.let { (h, m) ->
            time = if (meridiemFromPart) {
                LocalTime.of(if ((part ?: 0) >= 12) h % 12 + 12 else h % 12, m)
            } else {
                // Said with no clue, 1–6 is most likely afternoon and 7–11 morning; for today, the next one to come.
                val likely = LocalTime.of(if (h == 12) 12 else if (h <= 6) h + 12 else h, m)
                val other = likely.plusHours(12)
                if (date == null && weekday == null && !now.toLocalDate().atTime(likely).isAfter(now) && now.toLocalDate().atTime(other).isAfter(now)) other else likely
            }
        }
        val at = time ?: part?.let { LocalTime.of(it, 0) }
        val day = date ?: weekday?.let { wd ->
            val today = now.toLocalDate()
            if (repeat != Repeat.NEVER && today.dayOfWeek == wd && at != null && today.atTime(at).isAfter(now)) today
            else today.with(TemporalAdjusters.next(wd))
        }
        var result = when {
            day != null -> day.atTime(at ?: LocalTime.of(DEFAULT_HOUR, 0))
            at != null -> now.toLocalDate().atTime(at)
            else -> now.plusHours(1)
        }.truncatedTo(ChronoUnit.MINUTES)
        if (repeat == Repeat.DAILY && date == null) result = now.toLocalDate().atTime(result.toLocalTime())
        while (!result.isAfter(now)) result = result.plusDays(1)
        return VoiceCommand.Remind(what, result, repeat)
    }

    private fun capital(s: String) = s.trim().replaceFirstChar { it.uppercaseChar() }

    private fun dayOf(name: String): DayOfWeek? = DayOfWeek.entries.firstOrNull { it.name.startsWith(name.uppercase().take(3)) }

    private fun monthDay(month: String, day: String, today: LocalDate): LocalDate? {
        val m = Month.entries.firstOrNull { it.name.startsWith(month.uppercase().take(3)) } ?: return null
        val d = day.toIntOrNull() ?: return null
        val date = runCatching { LocalDate.of(today.year, m, d) }.getOrNull() ?: return null
        return if (date.isBefore(today)) date.plusYears(1) else date
    }

    private val NUMBERS = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
    private fun wordNumber(w: String) = NUMBERS.indexOf(w.lowercase()).takeIf { it >= 0 }?.plus(1)

    private val PARTS = mapOf("morning" to 9, "afternoon" to 15, "evening" to 19, "night" to 21, "tonight" to 20)
    private const val DAYS = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private const val MONTHS = "january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec"
    private val NUM = "\\d{1,2}|${NUMBERS.joinToString("|")}"
    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val PREFIX = rx("^(?:(?:ok|okay|hey) google|margin|please|can you|could you|would you|will you)\\b[,\\s]*")
    private val REMIND = rx("^(?:remind me|set (?:a |me a )?reminder|create (?:a )?reminder|add (?:a )?reminder|new reminder|reminder)\\b[:,]?(.*)$")
    private val LIST_TAIL = rx("^(?:add|put|include|write)\\s+(.+?)\\s+(?:to|on|onto|in|into)\\s+(?:my|the|our)?\\s*(.+?)\\s*list$")
    private val LIST_HEAD = rx("^(?:add|put)\\s+(?:to|on)\\s+(?:my|the|our)?\\s*(.+?)\\s*list[:,]?\\s+(.+)$")
    private val BUY = rx("^(?:i|we)?\\s*(?:need to|have to|must)?\\s*buy\\s+(.+)$")
    private val NOTE = rx("^(?:(?:take|make|create|write|new|add)\\s+(?:a\\s+)?note|note to self|note|jot down|write down)\\b(?:\\s+(?:that|saying|to self))?[:,]?\\s*(.*)$")

    private val EVERY = rx("\\bevery\\s+(day|morning|evening|afternoon|night|week|month|year|$DAYS)\\b")
    private val EVERY_WORD = rx("\\b(?:daily|weekly|monthly|yearly|annually)\\b")
    private val IN = rx("\\bin\\s+(\\d+|a|an|one|half an|half a|${NUMBERS.joinToString("|")})\\s+(minutes?|mins?|hours?|hrs?|days?|weeks?)\\b")
    private val DAY_AFTER = rx("\\b(?:the\\s+)?day after tomorrow\\b")
    private val TOMORROW = rx("\\btomorrow(?:\\s+(morning|afternoon|evening|night))?\\b")
    private val TONIGHT = rx("\\btonight\\b")
    private val TODAY = rx("\\btoday\\b")
    private val THIS_PART = rx("\\bthis\\s+(morning|afternoon|evening)\\b")
    private val NEXT_WEEK = rx("\\bnext week\\b")
    private val WEEKDAY = rx("\\b(?:on\\s+|next\\s+|this\\s+)?()($DAYS)\\b")
    private val MONTH_DAY = rx("\\b(?:on\\s+)?($MONTHS)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b")
    private val DAY_MONTH = rx("\\b(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($MONTHS)\\b")
    private val NOON = rx("\\b(?:at\\s+)?(?:noon|midday|midnight)\\b")
    // Each clock pattern has the same groups: hour, minutes, am/pm, and a part of the day said after it.
    private const val AFTER = "(?:\\s+in the\\s+(morning|afternoon|evening|night))?"
    private val CLOCK_AT = rx("\\bat\\s+($NUM)(?::(\\d{2}))?(?:\\s*(am|pm))?\\b$AFTER")
    private val CLOCK_MERIDIEM = rx("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b$AFTER")
    private val CLOCK_24 = rx("\\b(\\d{1,2}):(\\d{2})()\\b$AFTER")
    private val IN_PART = rx("\\bin the\\s+(morning|afternoon|evening)\\b")
}
