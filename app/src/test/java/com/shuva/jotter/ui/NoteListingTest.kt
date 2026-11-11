package com.shuva.jotter.ui

import com.shuva.jotter.data.Attachment
import com.shuva.jotter.data.Block
import com.shuva.jotter.data.BlockType
import com.shuva.jotter.data.NoteEntity
import com.shuva.jotter.data.NoteSort
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class NoteListingTest {
    private val zone = ZoneOffset.UTC
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).toInstant(zone).toEpochMilli()
    private val now = at(2026, 11, 9, 20)

    private fun note(id: String, title: String = id, edited: Long = now, created: Long = edited, pinned: Boolean = false, updated: Long = edited) =
        NoteEntity(
            id = id, folderId = "f", blocks = emptyList(), title = title, searchText = "", isPinned = pinned,
            createdAt = created, updatedAt = updated, editedAt = edited,
        )

    private fun List<ListEntry>.labels() = map {
        when (it) {
            is ListEntry.Row -> it.note.id
            is ListEntry.Header -> "[${it.text}]"
            is ListEntry.AttachmentRow -> it.note.id + ":" + it.attachment.name
        }
    }

    @Test
    fun `date groups run from today back to earlier years`() {
        fun g(t: Long) = dateGroup(t, now, zone, Locale.US)
        assertEquals("Today", g(at(2026, 11, 9, 0)))
        assertEquals("Today", g(at(2026, 11, 10))) // another device's clock ahead
        assertEquals("Yesterday", g(at(2026, 11, 8)))
        assertEquals("Previous 7 Days", g(at(2026, 11, 3)))
        assertEquals("Previous 30 Days", g(at(2026, 10, 15)))
        assertEquals("September", g(at(2026, 9, 1)))
        assertEquals("2025", g(at(2025, 12, 31)))
    }

    @Test
    fun `pinning or deleting does not change the edit order`() {
        // b was pinned/unpinned later (newer updatedAt) but its text is older, so it stays below a.
        val notes = listOf(note("b", edited = at(2026, 11, 1), updated = now), note("a", edited = at(2026, 11, 9)))
        assertEquals(listOf("[Today]", "a", "[Previous 30 Days]", "b"),
            buildListing(notes, NoteSort.EDITED, false, isTrash = false, now = now, zone = zone, locale = Locale.US).labels())
    }

    @Test
    fun `title sort is case-insensitive, has no date headers, and keeps pinned notes on top`() {
        val notes = listOf(note("1", "banana"), note("2", "Apple"), note("3", "cherry", pinned = true), note("4", "apricot"))
        assertEquals(listOf("[Pinned]", "3", "[Notes]", "2", "4", "1"),
            buildListing(notes, NoteSort.TITLE, true, isTrash = false, now = now, zone = zone, locale = Locale.US).labels())
        assertEquals(listOf("3", "1", "4", "2"), notes.sortedFor(NoteSort.TITLE, ascending = false).map { it.id })
    }

    @Test
    fun `created sort groups by creation date, oldest first when ascending`() {
        val notes = listOf(
            note("new", created = at(2026, 11, 9), edited = at(2026, 11, 9)),
            note("old", created = at(2025, 6, 1), edited = at(2026, 11, 9)),
        )
        assertEquals(listOf("[2025]", "old", "[Today]", "new"),
            buildListing(notes, NoteSort.CREATED, true, isTrash = false, now = now, zone = zone, locale = Locale.US).labels())
    }

    @Test
    fun `notes written by an older version fall back to updatedAt`() {
        val legacy = note("x", edited = 0, updated = at(2026, 11, 8))
        assertEquals(listOf("[Yesterday]", "x"),
            buildListing(listOf(legacy), NoteSort.EDITED, false, isTrash = false, now = now, zone = zone, locale = Locale.US).labels())
    }

    @Test
    fun `Recently Deleted keeps its given order without headers`() {
        val notes = listOf(note("a", pinned = true), note("b"), note("c"))
        assertEquals(listOf("a", "b", "c"),
            buildListing(notes, NoteSort.EDITED, false, isTrash = true, now = now, zone = zone, locale = Locale.US).labels())
    }

    private fun found(id: String, title: String, vararg body: Block) =
        note(id, title).copy(blocks = listOf(Block(type = BlockType.TITLE, text = title)) + body)

    @Test
    fun `search splits text hits from attachment hits in the given order`() {
        val pdf = Attachment("f1", "ticket-train.pdf", "application/pdf")
        val voice = Attachment("f2", "Voice note (0:42)", "audio/mp4")
        val a = found("a", "Trip", Block(text = "Toy train on Saturday"))
        val b = found("b", "Tickets", Block(text = "\uFFFC", attachment = pdf)) // only the file name matches
        val c = found("c", "Train times", Block(text = "\uFFFC", attachment = voice))
        val r = searchResults(listOf(a, b, c), " TRAIN ")
        assertEquals(listOf("a", "c"), r.notes.map { it.id })
        assertEquals(listOf("b" to pdf), r.attachments.map { (n, at) -> n.id to at })
        assertEquals(listOf("[Notes]", "a", "c", "[In attachments]", "b:ticket-train.pdf"), r.entries(SearchScope.TOP).labels())
        assertEquals(listOf("[Notes]", "a", "c"), r.entries(SearchScope.NOTES).labels())
        assertEquals(listOf("[In attachments]", "b:ticket-train.pdf"), r.entries(SearchScope.ATTACHMENTS).labels())
        assertEquals(listOf("c:Voice note (0:42)"), searchResults(listOf(c), "voice").entries(SearchScope.ATTACHMENTS).drop(1).labels())
    }

    @Test
    fun `search snippet shows the match even at the end of a long line`() {
        val short = found("a", "Trip", Block(text = "First"), Block(text = "Toy train on Saturday"))
        assertEquals(SearchSnippet("Toy train on Saturday", 4 until 9), searchSnippet(short, "Train"))
        val long = found("b", "Notes", Block(text = "x".repeat(40) + "train"))
        val s = searchSnippet(long, "train", lead = 10)
        assertEquals("…" + "x".repeat(10) + "train", s.text)
        assertEquals("train", s.text.substring(s.hit!!))
        // Title-only hit: the normal first line, nothing to highlight.
        assertEquals(SearchSnippet("First", null), searchSnippet(found("c", "Train", Block(text = ""), Block(text = "First")), "train"))
        assertEquals(SearchSnippet("Locked", null), searchSnippet(note("d", "Train").copy(cipher = "x"), "train"))
    }

    @Test
    fun `a collapsed pinned section keeps only its header`() {
        val notes = listOf(note("p", pinned = true), note("a"))
        val entries = buildListing(notes, NoteSort.EDITED, false, isTrash = false, now = now, zone = zone, locale = Locale.US, pinnedCollapsed = true)
        assertEquals(listOf("[Pinned]", "[Today]", "a"), entries.labels())
        assertEquals(true, (entries.first() as ListEntry.Header).collapsed)
    }

    @Test
    fun `without group by date there are no date headers`() {
        val notes = listOf(note("a", edited = now), note("b", edited = now - 3L * 24 * 60 * 60 * 1000), note("p", pinned = true))
        assertEquals(listOf("[Pinned]", "p", "[Notes]", "a", "b"),
            buildListing(notes, NoteSort.EDITED, false, isTrash = false, now = now, zone = zone, locale = Locale.US, groupByDate = false).labels())
    }

    @Test
    fun `the Pinned list has no Pinned section, only dates`() {
        val notes = listOf(note("p", pinned = true), note("q", edited = at(2026, 11, 1), pinned = true))
        assertEquals(listOf("[Today]", "p", "[Previous 30 Days]", "q"),
            buildListing(notes, NoteSort.EDITED, false, isTrash = false, now = now, zone = zone, locale = Locale.US, pinnedSection = false).labels())
    }
}
