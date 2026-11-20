package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ListRepeatTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone)
    private fun note(repeat: ListRepeat?, resetAt: ZonedDateTime, cipher: String? = null) = NoteEntity(
        "n", "f", listOf(Block(type = BlockType.CHECK, text = "Milk", checked = true)), "List", "",
        createdAt = 0, updatedAt = 0, resetEvery = repeat?.encode(), resetAt = resetAt.toInstant().toEpochMilli(), cipher = cipher,
    )

    @Test fun encodesAndParses() {
        for (r in listOf(ListRepeat(ListRepeat.Kind.DAILY), ListRepeat(ListRepeat.Kind.WEEKLY, DayOfWeek.FRIDAY), ListRepeat(ListRepeat.Kind.MONTHLY)))
            assertEquals(r.kind, ListRepeat.parse(r.encode())!!.kind)
        assertEquals(DayOfWeek.FRIDAY, ListRepeat.parse("WEEKLY:FRIDAY")!!.day)
        assertNull(ListRepeat.parse(null))
        assertNull(ListRepeat.parse("YEARLY"))
    }

    @Test fun roundStarts() {
        val thu = at(2026, 11, 19, 15) // a Thursday
        assertEquals(at(2026, 11, 19, 0), ListRepeat(ListRepeat.Kind.DAILY).roundStart(thu))
        assertEquals(at(2026, 11, 16, 0), ListRepeat(ListRepeat.Kind.WEEKLY, DayOfWeek.MONDAY).roundStart(thu))
        assertEquals(at(2026, 11, 19, 0), ListRepeat(ListRepeat.Kind.WEEKLY, DayOfWeek.THURSDAY).roundStart(thu))
        assertEquals(at(2026, 11, 1, 0), ListRepeat(ListRepeat.Kind.MONTHLY).roundStart(thu))
        assertEquals(at(2026, 11, 23, 0), ListRepeat(ListRepeat.Kind.WEEKLY, DayOfWeek.MONDAY).nextRound(thu))
        assertEquals(at(2026, 12, 1, 0), ListRepeat(ListRepeat.Kind.MONTHLY).nextRound(thu))
    }

    @Test fun unticksOnlyOnceANewRoundBegins() {
        val daily = ListRepeat(ListRepeat.Kind.DAILY)
        val turnedOn = at(2026, 11, 19, 9)
        assertFalse(roundOver(note(daily, turnedOn), at(2026, 11, 19, 23)))
        assertTrue(roundOver(note(daily, turnedOn), at(2026, 11, 20, 0)))
        val weekly = ListRepeat(ListRepeat.Kind.WEEKLY, DayOfWeek.MONDAY)
        assertFalse(roundOver(note(weekly, turnedOn), at(2026, 11, 22)))
        assertTrue(roundOver(note(weekly, turnedOn), at(2026, 11, 23, 1)))
        // Away for weeks: one untick, not one per missed round.
        assertTrue(roundOver(note(weekly, turnedOn), at(2026, 12, 30)))
        assertFalse(roundOver(note(weekly, at(2026, 12, 30)), at(2026, 12, 31)))
    }

    @Test fun leavesLockedAndPlainListsAlone() {
        val later = at(2026, 12, 25)
        assertFalse(roundOver(note(null, at(2026, 1, 1)), later))
        assertFalse(roundOver(note(ListRepeat(ListRepeat.Kind.DAILY), at(2026, 1, 1), cipher = "x"), later))
    }

    @Test fun untickingKeepsEverythingElse() {
        val blocks = listOf(
            Block(type = BlockType.TITLE, text = "Gym"),
            Block(type = BlockType.CHECK, text = "Stretch", checked = true),
            Block(type = BlockType.BODY, text = "notes"),
            Block(type = BlockType.CHECK, text = "Run"),
        )
        val out = unticked(blocks)
        assertFalse(anyTicked(out))
        assertEquals(blocks.map { it.text }, out.map { it.text })
        assertEquals(blocks.map { it.type }, out.map { it.type })
    }
}

class ListStreakTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone)
    private val daily = ListRepeat(ListRepeat.Kind.DAILY)
    private fun list(done: Boolean, streak: Int, resetAt: ZonedDateTime) = NoteEntity(
        "n", "f", listOf(Block(type = BlockType.CHECK, text = "Stretch", checked = true), Block(type = BlockType.CHECK, text = "Run", checked = done)),
        "Gym", "", createdAt = 0, updatedAt = 0, resetEvery = daily.encode(), resetAt = resetAt.toInstant().toEpochMilli(), streak = streak,
    )

    @Test fun growsWhenEachRoundIsDone() {
        assertEquals(4, nextStreak(list(true, 3, at(2026, 11, 19, 0)), at(2026, 11, 20, 0)))
        // Reset at 6 in the morning still counts as that day's round.
        assertEquals(4, nextStreak(list(true, 3, at(2026, 11, 19, 6)), at(2026, 11, 20, 9)))
    }

    @Test fun startsOverWhenARoundIsMissedOrUnfinished() {
        assertEquals(0, nextStreak(list(false, 3, at(2026, 11, 19)), at(2026, 11, 20, 1)))
        assertEquals(1, nextStreak(list(true, 3, at(2026, 11, 17)), at(2026, 11, 20, 1)))
    }

    @Test fun weeklyAndLabels() {
        val weekly = ListRepeat(ListRepeat.Kind.WEEKLY, DayOfWeek.MONDAY)
        val n = list(true, 1, at(2026, 11, 16, 8)).copy(resetEvery = weekly.encode())
        assertEquals(2, nextStreak(n, at(2026, 11, 23, 1)))
        assertEquals(1, nextStreak(n, at(2026, 11, 30, 1)))
        assertEquals("1 week in a row", streakLabel(1, weekly))
        assertEquals("5 days in a row", streakLabel(5, daily))
        assertEquals(3, shownStreak(2, TodoCount(2, 2)))
        assertEquals(2, shownStreak(2, TodoCount(1, 2)))
    }
}
