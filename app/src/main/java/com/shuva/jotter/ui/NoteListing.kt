package com.shuva.jotter.ui

import com.shuva.jotter.data.plainText
import com.shuva.jotter.data.Attachment
import com.shuva.jotter.data.NoteEntity
import com.shuva.jotter.data.NoteSort
import com.shuva.jotter.data.displayTitle
import com.shuva.jotter.data.isLocked
import com.shuva.jotter.data.lastEdited
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One row of the note list: a section header or a note. */
sealed interface ListEntry {
    /** [collapsed] is null for headers that can't be collapsed (only Pinned can). */
    data class Header(val text: String, val collapsed: Boolean? = null) : ListEntry
    data class Row(val note: NoteEntity) : ListEntry
    /** Search: an attachment whose name matches, in [note]. */
    data class AttachmentRow(val note: NoteEntity, val attachment: Attachment) : ListEntry
}

/** The date a note is sorted, grouped and labelled by. */
fun NoteEntity.sortTime(sortBy: NoteSort) = if (sortBy == NoteSort.CREATED) createdAt else lastEdited

/** Pinned notes first, then by the folder's sort key and direction. Ties are broken by id so the order is stable. */
fun List<NoteEntity>.sortedFor(sortBy: NoteSort, ascending: Boolean): List<NoteEntity> {
    val key: Comparator<NoteEntity> = when (sortBy) {
        NoteSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
        else -> compareBy { it.sortTime(sortBy) }
    }
    val directed = if (ascending) key else key.reversed()
    return sortedWith(compareByDescending<NoteEntity> { it.isPinned }.then(directed).thenBy { it.id })
}

/**
 * Date header for a note: Today, Yesterday, Previous 7 Days, Previous 30 Days, then the month name
 * for the rest of this year, then the year. Future times (another device's clock ahead) count as Today.
 */
fun dateGroup(time: Long, now: Long, zone: ZoneId, locale: Locale): String {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> "Previous 7 Days"
        days < 30 -> "Previous 30 Days"
        day.year == today.year -> day.month.getDisplayName(TextStyle.FULL, locale)
        else -> day.year.toString()
    }
}

/**
 * Builds the list for a folder or Recently Deleted (search uses [SearchResults.entries]).
 * - Folder: sorted by its own setting; pinned section on top; date headers when sorted by a date and [groupByDate].
 * - The Pinned section can be collapsed to just its header.
 * - Recently Deleted: kept in the order given (most recently deleted first); no sections.
 */
fun buildListing(
    notes: List<NoteEntity>,
    sortBy: NoteSort,
    ascending: Boolean,
    isTrash: Boolean,
    now: Long,
    zone: ZoneId,
    locale: Locale,
    pinnedCollapsed: Boolean = false,
    groupByDate: Boolean = true,
    pinnedSection: Boolean = true,
): List<ListEntry> {
    if (isTrash) return notes.map { ListEntry.Row(it) }
    val sorted = notes.sortedFor(sortBy, ascending)
    // [pinnedSection] false (the Pinned list, where every note is pinned): no section, just the dates.
    val pinned = if (pinnedSection) sorted.filter { it.isPinned } else emptyList()
    val others = if (pinnedSection) sorted.filterNot { it.isPinned } else sorted
    val result = mutableListOf<ListEntry>()
    if (pinned.isNotEmpty()) {
        result += ListEntry.Header("Pinned", collapsed = pinnedCollapsed)
        if (!pinnedCollapsed) pinned.mapTo(result) { ListEntry.Row(it) }
    }
    if (groupByDate && sortBy != NoteSort.TITLE) {
        var last: String? = null
        others.forEach { note ->
            val label = dateGroup(note.sortTime(sortBy), now, zone, locale)
            if (label != last) result += ListEntry.Header(label)
            last = label
            result += ListEntry.Row(note)
        }
    } else {
        if (pinned.isNotEmpty() && others.isNotEmpty()) result += ListEntry.Header("Notes")
        others.mapTo(result) { ListEntry.Row(it) }
    }
    return result
}

/** Which results search shows, from the pills above them. */
enum class SearchScope(val label: String) { TOP("Top results"), NOTES("Notes"), ATTACHMENTS("Attachments") }

/** Search results: notes whose title or text matches, and attachments whose name matches. A note can be in both. */
data class SearchResults(val notes: List<NoteEntity>, val attachments: List<Pair<NoteEntity, Attachment>>)

/**
 * Splits what the database found (it matches attachment names as part of the text) into note hits and
 * attachment hits, keeping the order given. A note found only through an attachment is only an attachment hit.
 */
fun searchResults(found: List<NoteEntity>, query: String): SearchResults {
    val q = query.trim()
    if (q.isEmpty()) return SearchResults(emptyList(), emptyList())
    val notes = found.filter { n ->
        n.title.contains(q, ignoreCase = true) || n.blocks.drop(1).any { it.attachment == null && it.plainText.contains(q, ignoreCase = true) }
    }
    val attachments = found.flatMap { n ->
        n.blocks.mapNotNull { b -> b.attachment?.takeIf { it.name.contains(q, ignoreCase = true) }?.let { n to it } }
    }
    return SearchResults(notes, attachments)
}

fun SearchResults.entries(scope: SearchScope): List<ListEntry> = buildList {
    if (scope != SearchScope.ATTACHMENTS && notes.isNotEmpty()) {
        add(ListEntry.Header("Notes"))
        notes.mapTo(this) { ListEntry.Row(it) }
    }
    if (scope != SearchScope.NOTES && attachments.isNotEmpty()) {
        add(ListEntry.Header("In attachments"))
        attachments.mapTo(this) { (n, a) -> ListEntry.AttachmentRow(n, a) }
    }
}

/** The text under a search result's title, and where the match is in it (null: only the title matched). */
data class SearchSnippet(val text: String, val hit: IntRange?)

/**
 * The first text line that contains [query], cut to start [lead] characters before the match (with "…") so the
 * match shows even at the end of a long line. With no match in the text, the note's first line, as in the list.
 */
fun searchSnippet(note: NoteEntity, query: String, lead: Int = 24): SearchSnippet {
    if (note.isLocked) return SearchSnippet("Locked", null)
    val q = query.trim()
    val lines = note.blocks.drop(1).filter { it.attachment == null }.flatMap { it.plainText.lines() }
    for (line in lines) {
        val at = if (q.isEmpty()) -1 else line.indexOf(q, ignoreCase = true)
        if (at < 0) continue
        if (at <= lead) return SearchSnippet(line, at until at + q.length)
        val start = at - lead
        return SearchSnippet("…" + line.substring(start), (lead + 1) until (lead + 1 + q.length))
    }
    return SearchSnippet(lines.firstOrNull { it.isNotBlank() } ?: "No additional text", null)
}
