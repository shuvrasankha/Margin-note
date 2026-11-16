package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkColorTest {
    @Test fun hsvRoundTrips() {
        listOf(0xFF111111, 0xFFB25E00, 0xFFD12F25, 0xFF0A66D9, 0xFF1F7A4D, 0xFF7440C9, 0xFFFFFFFF, 0xFF000000, 0xFF00FF7F).forEach { c ->
            val (h, s, v) = argbToHsv(c)
            assertEquals(hexOf(c), hexOf(hsvToArgb(h, s, v)))
        }
        assertEquals(0xFFFF0000, hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFFFF0000, hsvToArgb(360f, 1f, 1f))
        assertEquals(240f, argbToHsv(0xFF0000FF)[0], 0.01f)
    }

    @Test fun parsesHex() {
        assertEquals(0xFF1A2B3C, parseHexColor("#1a2b3c"))
        assertEquals(0xFF1A2B3C, parseHexColor(" 1A2B3C "))
        assertEquals(0xFFAABBCC, parseHexColor("#abc"))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("zzzzzz"))
        assertEquals("#1A2B3C", hexOf(0xFF1A2B3C))
    }

    @Test fun darkPageLiftsDarkInksOnly() {
        assertEquals(0xFFFFEE00, inkOnDarkPage(0xFFFFEE00)) // already bright
        val navy = inkOnDarkPage(0xFF001F4D)
        assertTrue(argbToHsv(navy)[2] > 0.9f)
        assertEquals(argbToHsv(0xFF001F4D)[0], argbToHsv(navy)[0], 2f) // still blue
    }

    @Test fun recentInksNewestFirst() {
        assertEquals(listOf(3L, 1L, 2L), listOf(1L, 2L).withRecentInk(3))
        assertEquals(listOf(2L, 1L, 3L), listOf(1L, 2L, 3L).withRecentInk(2))
        assertEquals(listOf(4L, 1L, 2L), listOf(1L, 2L, 3L).withRecentInk(4))
    }
}
