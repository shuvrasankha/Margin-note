package com.margin.app.ui

import com.margin.app.data.Block
import com.margin.app.data.BlockType
import com.margin.app.data.NoteEntity
import com.margin.app.data.withBlocks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HabitsTest {
    private val today = LocalDate.of(2026, 11, 13)

    @Test fun streakCountsBackFromTodayOrYesterday() {
        val d = { n: Long -> today.minusDays(n) }
        assertEquals(2, habitStreak(setOf(d(0), d(1), d(3)), today))
        assertEquals(1, habitStreak(setOf(d(1)), today)) // today not ticked yet: yesterday's run still counts
        assertEquals(0, habitStreak(setOf(d(2)), today))
        assertEquals(0, habitStreak(emptySet(), today))
    }

    @Test fun bestIsTheLongestRunEver() {
        val d = { n: Long -> today.minusDays(n) }
        assertEquals(3, habitBest(setOf(d(0), d(5), d(6), d(7), d(9))))
        assertEquals(0, habitBest(emptySet()))
    }

    @Test fun aHabitNoteKeepsItsDaysAsLines() {
        val note = NoteEntity("habit-1", "f", emptyList(), "", "", createdAt = 0, updatedAt = 0)
            .withBlocks(listOf(Block(type = BlockType.TITLE, text = "Run"), Block(text = "2026-11-13"), Block(text = "not a date"), Block(text = " 2026-11-10 ")), 1)
        assertEquals(setOf(today, today.minusDays(3)), habitDays(note))
        assertEquals(true, isHabit(note))
    }

    @Test fun allNotesLeavesOutHabitsAndJournalEntries() {
        val note = { id: String -> NoteEntity(id, "f", emptyList(), "", "", createdAt = 0, updatedAt = 0) }
        assertEquals(listOf("n1"), listOf(note("habit-1"), note("journal-2026-11-13"), note("n1")).filter(::inAllNotes).map { it.id })
    }

    @Test fun reminderRingsLaterTodayOrElseTomorrow() {
        val now = java.time.ZonedDateTime.of(2026, 11, 13, 18, 30, 15, 0, java.time.ZoneOffset.UTC)
        assertEquals(now.withHour(20).withMinute(0).withSecond(0), nextAt(java.time.LocalTime.of(20, 0), now))
        assertEquals(now.plusDays(1).withHour(8).withMinute(0).withSecond(0), nextAt(java.time.LocalTime.of(8, 0), now))
    }

    @Test fun doneTicksTodayOnce() {
        val note = NoteEntity("habit-1", "f", emptyList(), "", "", createdAt = 0, updatedAt = 0)
            .withBlocks(listOf(Block(type = BlockType.TITLE, text = "Run"), Block(text = "2026-11-10")), 1)
        val once = habitTicked(note, today)
        assertEquals(listOf("Run", "2026-11-13", "2026-11-10"), once.map { it.text })
        assertEquals(once.map { it.text }, habitTicked(note.withBlocks(once, 2), today).map { it.text })
    }
}
