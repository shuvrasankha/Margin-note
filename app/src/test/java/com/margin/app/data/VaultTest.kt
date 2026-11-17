package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultTest {
    @Test
    fun `a login survives sealing and opening`() {
        val login = Login("GitHub", "me@example.com", "p@ss \"quoted\" ✓", "https://github.com", "2FA on phone", 42)
        val params = NoteCrypto.KeyParams(NoteCrypto.newSalt(), 1_000)
        val key = NoteCrypto.deriveKey("123456".toCharArray(), params.salt, params.iterations)
        val sealed = NoteCrypto.seal(loginBody(login), key, params)
        assertEquals(login, loginFrom(NoteCrypto.open(sealed, key)!!))
        assertTrue("GitHub" !in sealed && "p@ss" !in sealed)
        assertNull(loginFrom(listOf(Block(text = "just a note"))))
    }

    @Test
    fun `generated passwords have the length and every chosen set`() {
        repeat(200) {
            val p = PasswordGen.generate(12, digits = true, symbols = true)
            assertEquals(12, p.length)
            assertTrue(p.any { it.isLowerCase() } && p.any { it.isUpperCase() } && p.any { it.isDigit() } && p.any { !it.isLetterOrDigit() })
            assertTrue(p.none { it in "0O1lI" })
        }
        val letters = PasswordGen.generate(16, digits = false, symbols = false)
        assertTrue(letters.all { it.isLetter() })
        assertEquals(4, PasswordGen.generate(2).length) // never shorter than one of each set
    }

    @Test
    fun `strength and site names`() {
        assertEquals(Strength.WEAK, PasswordGen.strength("password"))
        assertEquals(Strength.WEAK, PasswordGen.strength("aaaaaaaaaaaaaaaaaaaa"))
        assertEquals(Strength.STRONG, PasswordGen.strength(PasswordGen.generate(20)))
        // Dressed-up common passwords and keyboard or number runs.
        for (p in listOf("Password123!", "P@ssw0rd!", "qwerty123456", "abcdef654321", "Summer2024")) assertEquals(p, Strength.WEAK, PasswordGen.strength(p))
        // The site's own name is the first thing a guesser tries.
        assertEquals(Strength.GOOD, PasswordGen.strength("Github!2024x"))
        assertEquals(Strength.FAIR, PasswordGen.strength("Github!2024x", Login(url = "https://github.com").relatedWords()))
        assertEquals(Strength.WEAK, PasswordGen.strength("john1990", Login(username = "john1990@mail.com").relatedWords()))
        assertEquals(Strength.WEAK, PasswordGen.strength("xtrustno1x"))
        PasswordGen.strength("İabcİabc") // a character that lowercases to two must not crash it
        repeat(200) { assertEquals(Strength.STRONG, PasswordGen.strength(PasswordGen.generate(20))) }
        assertEquals("github.com", siteOf("https://www.GitHub.com:443/login?x=1"))
        assertEquals("example.org", siteOf("example.org/path"))
    }

    @Test
    fun `changing a password keeps the old one`() {
        val first = stampPasswordChange(null, Login(name = "Bank", password = "one"), 10)
        assertEquals(10, first.passwordChangedAt)
        assertTrue(first.history.isEmpty())
        val second = stampPasswordChange(first, first.copy(password = "two"), 20)
        assertEquals(listOf(PastPassword("one", 20)), second.history)
        // Editing anything else leaves the history and change time alone.
        val renamed = stampPasswordChange(second, second.copy(name = "My Bank", history = emptyList()), 30)
        assertEquals(20, renamed.passwordChangedAt)
        assertEquals(second.history, renamed.history)
        // Going back to an old password takes it out of the history; the list stays capped.
        assertEquals(listOf(PastPassword("two", 40)), stampPasswordChange(second, second.copy(password = "one"), 40).history)
        var l = second
        repeat(10) { l = stampPasswordChange(l, l.copy(password = "p$it"), 50L + it) }
        assertEquals(HISTORY_LIMIT, l.history.size)
    }
}
