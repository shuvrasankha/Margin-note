package com.margin.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UnlockLimitTest {
    private var now = 1_000_000L

    @Before fun setUp() {
        UnlockLimit.store = object : UnlockLimit.Store { override var failures = 0; override var since = 0L }
        UnlockLimit.clock = { now }
    }

    @After fun tearDown() { UnlockLimit.store = object : UnlockLimit.Store { override var failures = 0; override var since = 0L } }

    @Test fun `first wrong tries are free, then the wait doubles up to an hour`() {
        assertEquals(0, UnlockLimit.delayFor(4))
        assertEquals(30_000, UnlockLimit.delayFor(5))
        assertEquals(60_000, UnlockLimit.delayFor(6))
        assertEquals(3_600_000, UnlockLimit.delayFor(100))
    }

    @Test fun `waiting is enforced, and a right passcode resets the count`() {
        repeat(5) { assertEquals(0, UnlockLimit.waitMs()); UnlockLimit.record(false) }
        assertEquals(30_000, UnlockLimit.waitMs())
        now += 29_000
        assertEquals(1_000, UnlockLimit.waitMs())
        now += 1_000
        assertEquals(0, UnlockLimit.waitMs())
        UnlockLimit.record(false)
        assertEquals(60_000, UnlockLimit.waitMs())
        now += 60_000
        UnlockLimit.record(true)
        assertEquals(0, UnlockLimit.store.failures)
        assertEquals(0, UnlockLimit.waitMs())
    }

    @Test fun `a reboot starts the wait over instead of skipping it`() {
        repeat(5) { UnlockLimit.record(false) }
        now = 5_000 // boot clock reset
        assertEquals(30_000, UnlockLimit.waitMs())
    }

    @Test fun `session refuses even the right passcode while waiting`() = kotlinx.coroutines.runBlocking {
        val salt = NoteCrypto.newSalt()
        val params = NoteCrypto.KeyParams(salt, 1_000)
        val sealed = NoteCrypto.seal(listOf(Block(text = "x")), NoteCrypto.deriveKey("123456".toCharArray(), salt, 1_000), params)
        val session = LockSession()
        repeat(5) { assertEquals(null, session.unlock("000000".toCharArray(), sealed)) }
        assertEquals(null, session.unlock("123456".toCharArray(), sealed))
        now += 30_000
        assertEquals("x", session.unlock("123456".toCharArray(), sealed)!!.single().text)
        assertEquals(0, UnlockLimit.store.failures)
    }
}
