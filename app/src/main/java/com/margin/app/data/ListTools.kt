package com.margin.app.data

import java.text.Collator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ---------- Quantities ----------

private val QTY = Regex("""\s+[x×](\d{1,4})\s*$""", RegexOption.IGNORE_CASE)

/** An item's name and how many: "Eggs ×12" is ("Eggs", 12); "Eggs" is ("Eggs", 1). Kept in the text so it syncs and shares as is. */
fun splitQty(text: String): Pair<String, Int> {
    val m = QTY.find(text) ?: return text.trimEnd() to 1
    return text.substring(0, m.range.first).trimEnd() to (m.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1)
}

/** "Eggs ×12", or just "Eggs" for one. */
fun withQty(name: String, n: Int): String = if (n <= 1) name.trimEnd() else "${name.trimEnd()} ×$n"

/** The same item whatever its quantity, case or spacing: for finding it in another list. */
fun itemName(text: String) = splitQty(text).first.trim().lowercase().replace(Regex("\\s+"), " ")

// ---------- Clear ticked ----------

/**
 * [blocks] without ticked tasks. A "Done" heading or aisle heading (from Sort by aisle) left with no tasks under
 * it goes too; other lines stay.
 */
fun withoutTicked(blocks: List<Block>): List<Block> {
    val kept = blocks.filterIndexed { i, b -> i == 0 || !(b.type == BlockType.CHECK && b.checked && b.attachment == null) }
    return kept.filterIndexed { i, b ->
        if (i == 0 || b.type != BlockType.SUBHEADING || b.text.trim().lowercase() !in AISLE_LABELS) return@filterIndexed true
        // Keep the heading if a task follows before the next heading.
        kept.drop(i + 1).takeWhile { it.type != BlockType.SUBHEADING && it.type != BlockType.HEADING }.any { it.type == BlockType.CHECK }
    }
}

fun tickedCount(blocks: List<Block>) = blocks.drop(1).count { it.type == BlockType.CHECK && it.checked && it.attachment == null }

// ---------- Sort ----------

enum class ListSort(val label: String) { AZ("A to Z"), ZA("Z to A"), DUE("By due date"), UNDONE("To do first") }

/**
 * Tasks sorted within each run of task lines, so headings and other lines stay where they are. An indented task
 * moves with the task above it. Ties keep their order.
 */
fun sortedTasks(blocks: List<Block>, sort: ListSort, locale: Locale = Locale.getDefault()): List<Block> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val name = { g: List<Block> -> splitQty(g.first().text).first.trim() }
    val order: Comparator<List<Block>> = when (sort) {
        ListSort.AZ -> Comparator { a, b -> collator.compare(name(a), name(b)) }
        ListSort.ZA -> Comparator { a, b -> collator.compare(name(b), name(a)) }
        ListSort.DUE -> compareBy { it.first().due ?: Long.MAX_VALUE }
        ListSort.UNDONE -> compareBy { it.first().checked }
    }
    val out = mutableListOf<Block>()
    var i = 0
    while (i < blocks.size) {
        val b = blocks[i]
        if (i == 0 || b.type != BlockType.CHECK || b.attachment != null) { out += b; i++; continue }
        // A run of tasks (with their indented sub-tasks), grouped under each top task.
        val base = b.indent
        val groups = mutableListOf<MutableList<Block>>()
        while (i < blocks.size && blocks[i].type == BlockType.CHECK && blocks[i].attachment == null && blocks[i].indent >= base) {
            if (blocks[i].indent == base || groups.isEmpty()) groups += mutableListOf(blocks[i]) else groups.last() += blocks[i]
            i++
        }
        groups.sortedWith(order).forEach { out += it }
    }
    return out
}

// ---------- Suggestions ----------

/** An item from your lists, as you last wrote it, and in how many lists it appears. */
data class PastItem(val text: String, val lists: Int)

/** Items from every list but [exceptNoteId], most used first. Locked, deleted and trashed notes are left out. */
fun pastItems(notes: List<NoteEntity>, exceptNoteId: String): List<PastItem> {
    val seen = linkedMapOf<String, Pair<String, Int>>()
    notes.sortedByDescending { it.updatedAt }.forEach { n ->
        if (n.id == exceptNoteId || n.deleted || n.trashedAt != null || n.isLocked) return@forEach
        n.blocks.filter { it.type == BlockType.CHECK && it.attachment == null }
            .map { splitQty(it.text).first.trim() }.filter { it.length > 1 }
            .distinctBy { it.lowercase() }
            .forEach { t -> val k = itemName(t); seen[k] = (seen[k]?.first ?: t) to ((seen[k]?.second ?: 0) + 1) }
    }
    return seen.values.map { (t, n) -> PastItem(t, n) }.sortedByDescending { it.lists }
}

/**
 * Up to [limit] of [items] that finish [typed]: those starting with it first, then those with a word starting
 * with it. Not items already on the list ([have], by [itemName]) or exactly what's typed.
 */
fun suggestItems(items: List<PastItem>, typed: String, have: Set<String>, limit: Int = 5): List<PastItem> {
    val q = itemName(typed)
    if (q.length < 2) return emptyList()
    val fits = items.filter { val k = itemName(it.text); k != q && k !in have && (k.startsWith(q) || k.split(' ').any { w -> w.startsWith(q) }) }
    return fits.sortedBy { if (itemName(it.text).startsWith(q)) 0 else 1 }.take(limit)
}

// ---------- Due dates ----------

/** "Today", "Tomorrow", "Yesterday", "Fri" within the week ahead, else "21 Nov". */
fun dueLabel(day: Long, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String {
    val d = LocalDate.ofEpochDay(day)
    val diff = day - today.toEpochDay()
    return when {
        diff == 0L -> "Today"
        diff == 1L -> "Tomorrow"
        diff == -1L -> "Yesterday"
        diff in 2..6 -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        d.year == today.year -> d.format(DateTimeFormatter.ofPattern("d MMM", locale))
        else -> d.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
}

fun overdue(b: Block, today: LocalDate = LocalDate.now()) = b.type == BlockType.CHECK && !b.checked && (b.due ?: Long.MAX_VALUE) < today.toEpochDay()
