package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HandwritingTest {
    private fun s(vararg p: Float, erase: Boolean = false) = PenStroke(0xFF000000, 4f, p.toList(), erase)

    @Test fun groupsStrokesIntoLinesTopToBottom() {
        val a1 = s(0f, 100f, 10f, 160f)    // line 2, written first
        val b1 = s(0f, 0f, 10f, 60f)       // line 1
        val b2 = s(20f, 10f, 30f, 55f)     // line 1
        val a2 = s(20f, 110f, 30f, 150f)   // line 2
        assertEquals(listOf(listOf(b1, b2), listOf(a1, a2)), inkLines(listOf(a1, b1, b2, a2)))
    }

    @Test fun keepsMarksAboveLettersOnTheirLine() {
        val letter = s(0f, 40f, 30f, 100f)
        val matra = s(-5f, 30f, 40f, 31f)  // the bar over Bengali letters, just above the letter
        val dot = s(50f, 25f, 51f, 26f)    // a dot a little higher still
        val next = s(0f, 200f, 30f, 260f)
        assertEquals(listOf(listOf(letter, matra, dot), listOf(next)), inkLines(listOf(letter, matra, dot, next)))
    }

    @Test fun leavesOutEraserKeepsDots() {
        val ink = s(0f, 0f, 10f, 50f)
        val dot = s(5f, 5f) // the dot of an i
        assertEquals(listOf(listOf(ink, dot)), inkLines(listOf(ink, s(0f, 0f, 20f, 20f, erase = true), dot)))
        assertEquals(emptyList<List<PenStroke>>(), inkLines(emptyList()))
    }
}
