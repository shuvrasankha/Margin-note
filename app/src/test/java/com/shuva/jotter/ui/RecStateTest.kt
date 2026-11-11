package com.shuva.jotter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RecStateTest {
    @Test fun pausesDontCount() {
        val running = RecState(active = true, banked = 2_000, runStart = 10_000)
        assertEquals(5_000L, running.elapsed(13_000)) // 2 s before + 3 s this run
        assertEquals(2_000L, running.copy(paused = true).elapsed(99_000))
        assertEquals(0L, RecState().elapsed(99_000)) // not recording
    }
}
