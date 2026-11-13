package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonesTest {
    private fun found(text: String) = findPhones(text).map { it.second }

    @Test fun `finds mobile numbers in common shapes`() {
        assertEquals(listOf("9876543210"), found("Call 9876543210 tomorrow"))
        assertEquals(listOf("+91 98765 43210"), found("Ravi: +91 98765 43210."))
        assertEquals(listOf("(555) 123-4567"), found("office (555) 123-4567"))
        assertEquals(listOf("+44 20 7946 0958", "98765-43210"), found("+44 20 7946 0958 or 98765-43210"))
    }

    @Test fun `leaves out things that aren't phone numbers`() {
        assertEquals(emptyList<String>(), found("Lunch 18.50 + 15% = 21.275"))
        assertEquals(emptyList<String>(), found("1 ÷ 3 + 1 ÷ 4 = 7/12 ≈ 0.583333333333"))
        assertEquals(emptyList<String>(), found("2^40 = 1099511627776.5"))
        assertEquals(emptyList<String>(), found("on 2026-11-10 at 10:30"))
        assertEquals(emptyList<String>(), found("server 192.168.100.200"))
        assertEquals(emptyList<String>(), found("order 12345 and 678"))
        assertEquals(emptyList<String>(), found("code ab9876543210cd"))
        assertEquals(emptyList<String>(), found("12345678901234567890"))
    }

    @Test fun `ranges point at the number`() {
        val text = "Mom 9876543210"
        val (range, _) = findPhones(text).single()
        assertEquals("9876543210", text.substring(range))
    }

    @Test fun `dialable keeps only digits and the plus`() {
        assertEquals("+919876543210", dialable("+91 98765-43210"))
        assertEquals("5551234567", dialable("(555) 123-4567"))
    }
}
