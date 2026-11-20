package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TrackersTest {
    private fun note(vararg lines: String) = NoteEntity(
        id = TRACKER_PREFIX + "x", folderId = DEFAULT_FOLDER_ID, title = "Movies", searchText = "",
        blocks = listOf(Block(type = BlockType.TITLE, text = "Movies")) + lines.map { Block(text = it) },
        createdAt = 0, updatedAt = 0,
    )

    @Test fun parsesDateTitleAndRating() {
        assertEquals(TrackerEntry(LocalDate.of(2026, 3, 4), "Dune", 4), parseTrackerEntry("2026-03-04 · Dune · ★4"))
        assertEquals(TrackerEntry(LocalDate.of(2026, 3, 4), "Dune", null), parseTrackerEntry("2026-03-04 · Dune"))
        assertEquals(TrackerEntry(null, "Something typed by hand", null), parseTrackerEntry("Something typed by hand"))
        assertNull(parseTrackerEntry("   "))
    }

    @Test fun roundTripsTitlesWithTheSeparator() {
        val e = TrackerEntry(LocalDate.of(2025, 12, 31), "Kill Bill · Vol. 1", 5)
        assertEquals(e, parseTrackerEntry(formatTrackerEntry(e)))
        val plain = TrackerEntry(null, "Book", null)
        assertEquals(plain, parseTrackerEntry(formatTrackerEntry(plain)))
    }

    @Test fun sortsNewestFirstAndCountsByEntryDate() {
        val entries = trackerEntries(note("2025-06-01 · Old", "Undated", "2026-01-02 · New · ★3", "2026-02-10 · Newer"))
        assertEquals(listOf("Newer", "New", "Old", "Undated"), entries.map { it.title })
        assertEquals(2, entries.countInYear(2026))
        assertEquals(1, entries.countInYear(2025))
    }

    @Test fun monthsRatingsAndYears() {
        val entries = trackerEntries(note("2026-01-05 · A · ★4", "2026-01-20 · B · ★2", "2026-03-01 · C", "2025-07-01 · D", "Undated"))
        val months = entries.monthCounts(2026)
        assertEquals(2, months[0]); assertEquals(0, months[1]); assertEquals(1, months[2])
        assertEquals(3.0, entries.averageRating()!!, 0.001)
        assertNull(trackerEntries(note("2026-01-01 · X")).averageRating())
        assertEquals(listOf(2027, 2026, 2025), entries.years(2027))
    }

    @Test fun comparesWithTheSameDayLastYear() {
        val entries = trackerEntries(note("2025-03-01 · A", "2025-11-16 · B", "2025-11-17 · C", "2025-12-31 · D", "2024-11-01 · E"))
        assertEquals(2, entries.countSameTimeLastYear(LocalDate.of(2026, 11, 16)))
    }

    @Test fun replacesByValue() {
        val a = TrackerEntry(LocalDate.of(2026, 1, 1), "A")
        val b = TrackerEntry(LocalDate.of(2026, 2, 1), "B")
        val c = TrackerEntry(null, "C")
        assertEquals(listOf(a, c), listOf(a, b).replacing(b, c))
        assertEquals(listOf(a), listOf(a, b).replacing(b, null))
        assertEquals(listOf(a, b, c), listOf(a, b).replacing(null, c))
        assertEquals(listOf(a, b, c), listOf(a, b).replacing(c, c)) // gone already: added back
    }

    @Test fun picksAKindFromTheName() {
        assertEquals(TrackerKind.MOVIE, trackerKind("Movies watched"))
        assertEquals(TrackerKind.BOOK, trackerKind("Books read in 2026"))
        assertEquals(TrackerKind.TV, trackerKind("TV shows"))
        assertEquals(TrackerKind.PLACE, trackerKind("Places"))
        assertEquals(TrackerKind.ART, trackerKind("Art I've seen"))
        assertNull(trackerKind("Party invites"))
        assertNull(trackerKind("Something else"))
    }

    @Test fun blocksKeepTheTitleLine() {
        val n = note()
        val blocks = trackerBlocks(n, listOf(TrackerEntry(LocalDate.of(2026, 1, 1), "A"), TrackerEntry(LocalDate.of(2026, 5, 1), "B", 2)))
        assertEquals(listOf("Movies", "2026-05-01 · B · ★2", "2026-01-01 · A"), blocks.map { it.text })
    }

    @Test fun photosStayWithTheirEntry() {
        val cover = Attachment("abc.jpg", "Dune.jpg", "image/jpeg")
        val n = note()
        val blocks = trackerBlocks(n, listOf(TrackerEntry(LocalDate.of(2026, 1, 1), "A"), TrackerEntry(LocalDate.of(2026, 5, 1), "Dune", 5, cover)))
        assertEquals(listOf(null, cover, null), blocks.map { it.attachment })
        val back = trackerEntries(n.copy(blocks = blocks))
        assertEquals(TrackerEntry(LocalDate.of(2026, 5, 1), "Dune", 5, cover), back.first())
        assertNull(back.last().photo)
        assertEquals(listOf("Movies", "2026-05-01 · Dune · ★5", "2026-01-01 · A"), blocks.map { it.text })
    }
}
