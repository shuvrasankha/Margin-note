package com.margin.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AiAnswersTest {
    @Test fun checklistItemsDropListMarks() = assertEquals(
        listOf("Buy milk", "Call Sam", "Book tickets", "Pay rent"),
        checklistItems("- Buy milk\n* Call Sam\n\n2. Book tickets\n- [ ] Pay rent\n"),
    )

    @Test fun tagsAreFoundOrMade() {
        assertEquals(listOf("#work", "#ideas"), tagsIn("#Work #ideas #work"))
        assertEquals(listOf("#travel", "#plans"), tagsIn("travel, plans"))
    }

    @Test fun titleLosesQuotesAndLabel() {
        assertEquals("Trip to Goa", titleIn("Title: \"Trip to Goa\"\n"))
        assertEquals("Weekly plan", titleIn("\n**Weekly plan**"))
    }

    @Test fun madeTagsWorkAsNoteTags() {
        val line = tagsIn("#work #road-trip #idées").joinToString(" ")
        assertEquals(3, com.margin.app.data.findTags(line).size)
    }
}
