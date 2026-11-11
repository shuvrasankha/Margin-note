package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTest {
    @Test fun clocks() {
        assertEquals("0:00", clock(999))
        assertEquals("1:05", clock(65_000))
        assertEquals("59:59", clock(3_599_000))
        assertEquals("1:00:07", clock(3_607_000))
        assertEquals("0:00", clock(-5))
    }

    @Test fun `wave bars scale to the loudest and keep silence flat`() {
        assertEquals(listOf(0f, 0.5f, 1f), normalizeBars(floatArrayOf(0f, 0.125f, 0.5f)))
        assertEquals(listOf(0f, 0f), normalizeBars(floatArrayOf(0f, 0f)))
    }
}
