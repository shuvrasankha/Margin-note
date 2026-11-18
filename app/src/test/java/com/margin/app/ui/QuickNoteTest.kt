package com.margin.app.ui

import com.margin.app.data.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickNoteTest {
    @Test fun emptyIsNotSaved() {
        assertNull(quickNoteBlocks("  ", "\n \n"))
    }

    @Test fun titleThenLines() {
        val b = quickNoteBlocks(" Shopping ", "milk\n\neggs\n")!!
        assertEquals(listOf("Shopping", "milk", "", "eggs"), b.map { it.text })
        assertEquals(BlockType.TITLE, b[0].type)
        assertEquals(BlockType.BODY, b[1].type)
    }

    @Test fun firstLineBecomesTitle() {
        val b = quickNoteBlocks("", "\nCall mum\n\nabout Sunday")!!
        assertEquals(listOf("Call mum", "about Sunday"), b.map { it.text })
    }
}
