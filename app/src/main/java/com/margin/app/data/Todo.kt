package com.margin.app.data

import java.time.Instant
import java.time.ZoneId

/** A to-do list's tasks (its checklist lines): how many are done of how many. */
data class TodoCount(val done: Int, val total: Int) {
    val fraction get() = if (total == 0) 0f else done.toFloat() / total
    val complete get() = total > 0 && done == total
}

/**
 * [blocks] with a new unchecked task [text]: after the last checklist line, or at the end (in place of an
 * empty last line) when there's none yet.
 */
fun withTask(blocks: List<Block>, text: String): List<Block> {
    val task = Block(type = BlockType.CHECK, text = text)
    val last = blocks.indexOfLast { it.type == BlockType.CHECK }
    if (last >= 0) return blocks.toMutableList().apply { add(last + 1, task) }
    val trimmed = if (blocks.size > 1 && blocks.last().text.isBlank() && blocks.last().attachment == null && blocks.last().type == BlockType.BODY) blocks.dropLast(1) else blocks
    return trimmed + task
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

/**
 * [blocks] with each task ticked as it is in [stored], matching tasks by their text (the nth "Milk" to the
 * nth "Milk"). Everything else in [blocks], edits not yet saved included, stays as it is.
 */
fun withTicksFrom(blocks: List<Block>, stored: List<Block>): List<Block> {
    val ticks = stored.filter { it.type == BlockType.CHECK }.groupBy({ it.text.trim() }, { it.checked })
    val seen = mutableMapOf<String, Int>()
    return blocks.map { b ->
        if (b.type != BlockType.CHECK) return@map b
        val key = b.text.trim()
        val n = seen.merge(key, 1, Int::plus)!! - 1
        ticks[key]?.getOrNull(n)?.let { if (it != b.checked) b.copy(checked = it) else b } ?: b
    }
}

private fun taskKey(text: String) = itemName(text)

/** [tasks] added to another list's [blocks], and how many it didn't have yet. */
data class TasksAdded(val blocks: List<Block>, val added: Int)

/**
 * Brings [tasks] into a list: after its last task, or at the end when it has none. A task it already has still
 * to do isn't added twice; one it has ticked off comes back as to do (when the task brought in is to do).
 */
fun withTasksAdded(blocks: List<Block>, tasks: List<Block>): TasksAdded {
    val out = blocks.toMutableList()
    var added = 0
    for (t in tasks) {
        val k = taskKey(t.text)
        val same = out.indexOfFirst { it.type == BlockType.CHECK && taskKey(it.text) == k }
        if (same >= 0) {
            if (out[same].checked && !t.checked) { out[same] = out[same].copy(checked = false); added++ }
            continue
        }
        val task = t.copy(id = newId(), type = BlockType.CHECK, indent = 0)
        val last = out.indexOfLast { it.type == BlockType.CHECK }
        if (last >= 0) out.add(last + 1, task) else {
            if (out.size > 1 && out.last().type == BlockType.BODY && out.last().text.isBlank() && !out.last().isObject) out.removeAt(out.lastIndex)
            out.add(task)
        }
        added++
    }
    return TasksAdded(out, added)
}

/** A new list holding [tasks], called [title]. */
fun newListBlocks(title: String, tasks: List<Block>): List<Block> =
    listOf(Block(type = BlockType.TITLE, text = title)) + tasks.map { it.copy(id = newId(), indent = 0) }

/** [blocks] without the lines at [indices] (moved away); the title always stays. */
fun withoutLines(blocks: List<Block>, indices: Set<Int>): List<Block> =
    blocks.filterIndexed { i, _ -> i == 0 || i !in indices }.ifEmpty { blocks.take(1) }
