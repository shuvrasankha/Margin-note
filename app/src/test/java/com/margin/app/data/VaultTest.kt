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
        assertEquals("github.com", siteOf("https://www.GitHub.com:443/login?x=1"))
        assertEquals("example.org", siteOf("example.org/path"))
    }
}
