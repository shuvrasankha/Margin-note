package com.margin.app.data

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

    @Test fun passphrasesAreLongAndNeverNaggedBackToDigits() {
        assertFalse(isPassphrase("hunter22".toCharArray()))
        assertTrue(isPassphrase("correct horse".toCharArray()))
        val session = LockSession()
        session.startWith("correct horse battery".toCharArray())
        assertTrue(session.passphrase.value)
        assertFalse(session.legacy.value) // a chosen passphrase isn't an old password
        session.startWith("123456".toCharArray())
        assertFalse(session.passphrase.value) // a passcode alone can't save to Passwords (VaultStore.save)
        session.lock()
        assertFalse(session.passphrase.value)
    }

    @Test fun newSealsUseTheStrongerIterationCount() {
        assertTrue(NoteCrypto.ITERATIONS >= 600_000)
    }

    @Test fun savedPasswordsAreOnlyBackedUpWithABackupPassword() {
        val note = NoteEntity("n1", "f", emptyList(), "", "", createdAt = 0, updatedAt = 0)
        val login = note.copy(id = LOGIN_PREFIX + "1", cipher = "j1.x")
        assertFalse(needsBackupPassword(listOf(note), hasBackupPassword = false))
        assertTrue(needsBackupPassword(listOf(note, login), hasBackupPassword = false))
        assertFalse(needsBackupPassword(listOf(note, login), hasBackupPassword = true))
    }
}
