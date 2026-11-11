package com.shuva.jotter.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasscodeTest {
    @Test fun onlySixDigitsArePasscodes() {
        assertTrue(isPasscode("042917".toCharArray()))
        assertFalse(isPasscode("04291".toCharArray()))
        assertFalse(isPasscode("0429171".toCharArray()))
        assertFalse(isPasscode("04a917".toCharArray()))
        assertFalse(isPasscode("hunter22".toCharArray()))
    }

    @Test fun oldPasswordUnlockAsksForTheSwitch() = runBlocking {
        val params = NoteCrypto.KeyParams(NoteCrypto.newSalt(), 1_000)
        val body = listOf(Block(text = "secret"))
        val oldSealed = NoteCrypto.seal(body, NoteCrypto.deriveKey("hunter22".toCharArray(), params.salt, params.iterations), params)
        val session = LockSession()
        assertNotNull(session.unlock("hunter22".toCharArray(), oldSealed))
        assertTrue(session.legacy.value)
        session.startWith("123456".toCharArray()) // after the notes are re-sealed
        assertFalse(session.legacy.value)
        session.lock()
        assertFalse(session.legacy.value)
    }
}
