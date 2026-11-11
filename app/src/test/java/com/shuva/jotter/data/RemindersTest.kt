package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RemindersTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone) // Nov 10, 2026 is a Tuesday

    @Test fun morningOffersAllFour() {
        val c = reminderChoices(at(10, 10, 7)).toMap()
        assertEquals(listOf("In 1 hour", "This evening", "Tomorrow morning", "Next week"), reminderChoices(at(10, 10, 7)).map { it.first })
        assertEquals(at(10, 11, 10), c["In 1 hour"]) // rounded up to 5 minutes
        assertEquals(at(10, 18, 0), c["This evening"])
        assertEquals(at(11, 9, 0), c["Tomorrow morning"])
        assertEquals(at(16, 9, 0), c["Next week"]) // the next Monday
    }

    @Test fun noEveningAfterFive() {
        val c = reminderChoices(at(10, 17, 30))
        assertEquals(listOf("In 1 hour", "Tomorrow morning", "Next week"), c.map { it.first })
        assertEquals(at(10, 18, 30), c[0].second) // already on a 5-minute mark
    }
}
