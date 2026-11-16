package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSearchTest {
    /** Words on lines: each letter 10 wide, lines 20 apart, starting at x = 0. */
    private fun page(vararg lines: String): PageText {
        val b = PageTextBuilder()
        lines.forEachIndexed { row, line ->
            line.split(' ').forEachIndexed { w, word ->
                if (w > 0) b.gap()
                val start = line.split(' ').take(w).sumOf { it.length + 1 }
                word.forEachIndexed { i, ch -> val x = (start + i) * 10f; b.letters(ch.toString(), x, row * 20f, x + 10, row * 20f + 12) }
            }
            b.gap()
        }
        return b.build()
    }

    @Test fun builderKeepsBoxesInStep() {
        val p = page("Hello world", "again")
        assertEquals("Hello world again", p.text)
        assertEquals(p.text.length * 4, p.boxes.size)
        assertTrue(p.boxes[5 * 4].isNaN()) // the space after Hello
    }

    @Test fun ligaturesSplitTheirBox() {
        val b = PageTextBuilder(); b.letters("fi", 0f, 0f, 20f, 10f)
        val p = b.build()
        assertEquals("fi", p.text)
        assertEquals(10f, p.boxes[2]); assertEquals(10f, p.boxes[4])
    }

    @Test fun findsIgnoringCaseAndAcrossLines() {
        val pages = listOf(page("The cat sat", "on the mat").text, page("No cats here? A CAT!").text)
        val m = findInPages(pages, "cat")
        assertEquals(listOf(PdfMatch(0, 4, 7), PdfMatch(1, 3, 6), PdfMatch(1, 16, 19)), m)
        // "sat on" spans the line break, which became one space.
        assertEquals(listOf(PdfMatch(0, 8, 14)), findInPages(pages, "sat   on"))
        assertEquals(emptyList<PdfMatch>(), findInPages(pages, "  "))
        assertEquals(1, findInPages(pages, "?").size) // not a regex
        assertEquals(2, findInPages(pages, "cat", limit = 2).size)
    }

    @Test fun boxesOnePerLine() {
        val p = page("The cat sat", "on the mat")
        val one = matchBoxes(p, findInPages(listOf(p.text), "cat sat").single())
        assertEquals(1, one.size)
        assertEquals(listOf(40f, 0f, 110f, 12f), one.single().toList())
        val two = matchBoxes(p, findInPages(listOf(p.text), "sat on").single())
        assertEquals(2, two.size)
        assertEquals(listOf(80f, 0f, 110f, 12f), two[0].toList())
        assertEquals(listOf(0f, 20f, 20f, 32f), two[1].toList())
    }
}
