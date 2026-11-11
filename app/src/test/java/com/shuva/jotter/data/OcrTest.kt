package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTest {
    @Test fun keepsLinesAndSeparatesBlocks() {
        val blocks = listOf(listOf("  Milk   2.50 ", "Bread 1.20"), listOf(" "), listOf("Total 3.70"))
        assertEquals("Milk 2.50\nBread 1.20\n\nTotal 3.70", ocrToNoteText(blocks))
    }

    @Test fun nothingFound() = assertEquals("", ocrToNoteText(emptyList()))
}
