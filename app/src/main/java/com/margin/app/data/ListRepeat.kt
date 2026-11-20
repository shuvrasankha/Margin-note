package com.margin.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * A checklist that unticks itself for the next round: a daily routine, the weekly shop, a monthly chore.
 * Stored on the note as a short string ([encode]), so it syncs with it.
 */
data class ListRepeat(val kind: Kind, val day: DayOfWeek = DayOfWeek.MONDAY) {
    enum class Kind { DAILY, WEEKLY, MONTHLY }

    fun encode(): String = if (kind == Kind.WEEKLY) "WEEKLY:${day.name}" else kind.name

    /** "Every day", "Every Monday", "Every month". */
    val label: String
        get() = when (kind) {
            Kind.DAILY -> "Every day"
            Kind.WEEKLY -> "Every " + day.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Kind.MONTHLY -> "Every month"
        }

    /** When the round that [now] is in began: midnight today, the last [day], or the 1st of the month. */
    fun roundStart(now: ZonedDateTime): ZonedDateTime {
        val midnight = now.toLocalDate().atStartOfDay(now.zone)
        return when (kind) {
            Kind.DAILY -> midnight
            Kind.WEEKLY -> midnight.with(TemporalAdjusters.previousOrSame(day))
            Kind.MONTHLY -> midnight.withDayOfMonth(1)
        }
    }

    /** When the next round begins, and the list unticks. */
    fun nextRound(now: ZonedDateTime): ZonedDateTime {
        val start = roundStart(now)
        return when (kind) {
            Kind.DAILY -> start.plusDays(1)
            Kind.WEEKLY -> start.plusWeeks(1)
            Kind.MONTHLY -> start.plusMonths(1)
        }
    }

    companion object {
        fun parse(s: String?): ListRepeat? {
            if (s.isNullOrBlank()) return null
            val kind = runCatching { Kind.valueOf(s.substringBefore(':')) }.getOrNull() ?: return null
            val day = s.substringAfter(':', "").let { d -> runCatching { DayOfWeek.valueOf(d) }.getOrNull() } ?: DayOfWeek.MONDAY
            return ListRepeat(kind, day)
        }
    }
}

val NoteEntity.listRepeat: ListRepeat? get() = ListRepeat.parse(resetEvery)

/**
 * Whether [note]'s list has moved into a new round since it last unticked (or since the repeat was turned on).
 * Locked notes are left alone: their tasks are sealed.
 */
fun roundOver(note: NoteEntity, now: ZonedDateTime): Boolean {
    val repeat = note.listRepeat ?: return false
    if (note.isLocked || note.deleted || note.trashedAt != null) return false
    val last = Instant.ofEpochMilli(note.resetAt).atZone(now.zone)
    return repeat.roundStart(now).isAfter(last)
}

/** Every task unticked, for the next round. */
fun unticked(blocks: List<Block>): List<Block> = blocks.map { if (it.type == BlockType.CHECK && it.checked) it.copy(checked = false) else it }

/** Whether any task is ticked. */
fun anyTicked(blocks: List<Block>) = blocks.any { it.type == BlockType.CHECK && it.checked }

fun zonedNow(millis: Long = System.currentTimeMillis()): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
