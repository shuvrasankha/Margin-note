package com.margin.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteCardTest {
    // Lines 10 px tall: line i spans i*10 until i*10+10.
    private fun pages(count: Int, firstRoom: Int, room: Int) = cardPages(count, { it * 10 }, { it * 10 + 10 }, firstRoom, room)

    @Test fun fitsOnOneCard() = assertEquals(listOf(0 until 5), pages(5, 50, 100))

    @Test fun runsOnWithLessRoomOnTheFirst() = assertEquals(listOf(0 until 3, 3 until 8, 8 until 12), pages(12, 30, 50))

    @Test fun aLineTallerThanTheCardStillMovesOn() = assertEquals(listOf(0 until 1, 1 until 2), pages(2, 5, 5))
}
