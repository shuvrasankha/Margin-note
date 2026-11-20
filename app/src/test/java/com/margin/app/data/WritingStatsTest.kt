package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WritingStatsTest {
    @Test fun countsWordsNotPunctuation() {
        assertEquals(0, countWords("   "))
        assertEquals(4, countWords("Hello,  world —  it's\nme"))
        assertEquals(2, countWords("• milk - eggs"))
    }

    @Test fun bodyLeavesOutTitleAndFiles() {
        val blocks = listOf(
            Block(type = BlockType.TITLE, text = "A long title here"),
            Block(text = "One two three"),
            Block(text = "", attachment = Attachment("f.jpg", "photo of a cat.jpg", "image/jpeg")),
            Block(type = BlockType.CHECK, text = "Buy milk"),
        )
        assertEquals(5, noteWords(blocks))
        assertEquals(2, paragraphs(blocks))
    }

    @Test fun readingTime() {
        assertEquals("Under a minute", readingLabel(0))
        assertEquals("Under a minute", readingLabel(230))
        assertEquals("2 min read", readingLabel(231))
        assertEquals(5, readingMinutes(1100))
    }

    @Test fun streakEndsTodayOrYesterday() {
        val today = LocalDate.of(2026, 11, 20)
        val days = mapOf(today.minusDays(3) to 600, today.minusDays(2) to 500, today.minusDays(1) to 520, today to 100)
        assertEquals(3, goalStreak(days, 500, today)) // today isn't done yet; the streak still stands
        assertEquals(4, goalStreak(days + (today to 500), 500, today))
        assertEquals(0, goalStreak(days, 0, today))
        assertEquals(0, goalStreak(mapOf(today.minusDays(2) to 900), 500, today))
    }
}
