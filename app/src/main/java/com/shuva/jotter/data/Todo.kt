package com.shuva.jotter.data

import java.time.Instant
import java.time.ZoneId

/** A to-do list's tasks (its checklist lines): how many are done of how many. */
data class TodoCount(val done: Int, val total: Int) {
    val fraction get() = if (total == 0) 0f else done.toFloat() / total
    val complete get() = total > 0 && done == total
}

/** Counts the checklist lines in [blocks]. */
fun todoCount(blocks: List<Block>): TodoCount {
    val tasks = blocks.filter { it.type == BlockType.CHECK && it.attachment == null }
    return TodoCount(tasks.count { it.checked }, tasks.size)
}

/** Where a list stands against its target date, in the order the report lists them. */
enum class TodoStatus { OVERDUE, DUE_TODAY, BEHIND, ON_TRACK, NO_TARGET, DONE }

/** How far ahead of the time used up a list must be, at least, to count as on track rather than behind. */
private const val SLACK = 0.1f

/**
 * Done is done. Otherwise, against the target day: past it is overdue, on it is due today, and before it the
 * share of tasks done is compared with the share of time used since [startDay] (when the list was made).
 * Behind means more than [SLACK] of the time has gone than of the tasks.
 */
fun todoStatus(count: TodoCount, startDay: Long, targetDay: Long?, today: Long): TodoStatus = when {
    count.complete -> TodoStatus.DONE
    targetDay == null -> TodoStatus.NO_TARGET
    today > targetDay -> TodoStatus.OVERDUE
    today == targetDay -> TodoStatus.DUE_TODAY
    else -> {
        val span = (targetDay - startDay).coerceAtLeast(1)
        val used = ((today - startDay).toFloat() / span).coerceIn(0f, 1f)
        if (count.fraction + SLACK >= used) TodoStatus.ON_TRACK else TodoStatus.BEHIND
    }
}

/** One to-do list in the report. */
data class TodoList(val note: NoteEntity, val count: TodoCount, val status: TodoStatus)

/** Every to-do list, most urgent first, with the task totals across them. */
data class TodoReport(val lists: List<TodoList>) {
    val done get() = lists.sumOf { it.count.done }
    val total get() = lists.sumOf { it.count.total }
    val fraction get() = if (total == 0) 0f else done.toFloat() / total
    fun count(status: TodoStatus) = lists.count { it.status == status }
    /** Lists not done yet whose target is in the next 7 days (after today). */
    fun dueThisWeek(today: Long) = lists.count { l ->
        l.status != TodoStatus.DONE && l.note.targetDate?.let { it in today + 1..today + 7 } == true
    }
}

/** The day [millis] falls on in [zone], as an epoch day. */
fun epochDay(millis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

/**
 * The to-do report for [notes]: each live note with checklist lines or a target date. Locked notes are left
 * out (their tasks can't be read without the password), as are deleted ones. Sorted by status, then by the
 * nearest target, then by the latest edit.
 */
fun todoReport(notes: List<NoteEntity>, today: Long, zone: ZoneId): TodoReport = TodoReport(
    notes.asSequence()
        .filter { !it.deleted && it.trashedAt == null && !it.isLocked }
        .mapNotNull { n ->
            val count = todoCount(n.blocks)
            if (count.total == 0 && n.targetDate == null) null
            else TodoList(n, count, todoStatus(count, epochDay(n.createdAt, zone), n.targetDate, today))
        }
        .sortedWith(
            compareBy<TodoList> { it.status.ordinal }
                .thenBy { it.note.targetDate ?: Long.MAX_VALUE }
                .thenByDescending { it.note.lastEdited },
        )
        .toList(),
)
