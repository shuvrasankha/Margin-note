package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarksTest {
    private val bold = MarkSpan(4, 8, Mark.BOLD) // "bold" in "the bold word"

    @Test fun typingMovesAndGrowsSpans() {
        assertEquals(MarkSpan(6, 10, Mark.BOLD), bold.shifted(0, 0, 2)) // typed before
        assertEquals(MarkSpan(4, 10, Mark.BOLD), bold.shifted(8, 8, 2)) // typed at the end joins
        assertEquals(MarkSpan(6, 10, Mark.BOLD), bold.shifted(4, 4, 2)) // typed at the start doesn't
        assertEquals(bold, bold.shifted(9, 13, 0)) // deleted after
        assertEquals(MarkSpan(4, 6, Mark.BOLD), bold.shifted(6, 10, 0)) // deleted across the end
        assertNull(bold.shifted(3, 9, 0)) // deleted entirely
        assertEquals(MarkSpan(4, 7, Mark.BOLD), bold.shifted(4, 8, 3)) // replaced: new text keeps the style
    }

    @Test fun renamingATagKeepsStylesOnTheRightText() {
        val b = Block(text = "#a then end", marks = listOf(MarkSpan(8, 11, Mark.ITALIC)))
        val renamed = replaceTag(b, "a", "longer")
        assertEquals("#longer then end", renamed.text)
        assertEquals("end", renamed.text.substring(renamed.marks[0].start, renamed.marks[0].end))
    }

    @Test fun badSpansFromSyncAreClamped() {
        assertEquals(listOf(MarkSpan(2, 5, Mark.BOLD)), listOf(MarkSpan(2, 99, Mark.BOLD), MarkSpan(7, 3, Mark.ITALIC)).clampedTo(5))
    }
}
