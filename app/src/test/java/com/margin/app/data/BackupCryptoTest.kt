package com.margin.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class BackupCryptoTest {
    private val pw = "correct horse".toCharArray()
    private fun seal(data: ByteArray) = ByteArrayOutputStream().also { BackupCrypto.seal(ByteArrayInputStream(data), it, pw, iterations = 10_000) }.toByteArray()
    private fun open(sealed: ByteArray, password: CharArray = pw) =
        ByteArrayOutputStream().also { BackupCrypto.open(ByteArrayInputStream(sealed), it, password) }.toByteArray()

    private fun contains(hay: ByteArray, needle: ByteArray) =
        (0..hay.size - needle.size).any { i -> needle.indices.all { hay[i + it] == needle[it] } }

    @Test
    fun `sealed backups of any size open back exactly`() {
        for (size in listOf(0, 1, 64 * 1024, 64 * 1024 + 1, 200_000)) {
            val data = Random(size).nextBytes(size)
            val sealed = seal(data)
            assertTrue(BackupCrypto.isSealed(sealed))
            if (size >= 32) assertFalse(contains(sealed, data.copyOf(32))) // not in the clear
            assertArrayEquals(data, open(sealed))
        }
    }

    @Test(expected = WrongBackupPasswordException::class)
    fun `a wrong password is refused`() { open(seal(Random(1).nextBytes(1000)), "wrong".toCharArray()) }

    @Test(expected = WrongBackupPasswordException::class)
    fun `a cut-off backup is refused, even at a chunk boundary`() {
        val sealed = seal(Random(2).nextBytes(64 * 1024 * 2 + 10))
        val firstChunkEnd = 8 + 1 + 4 + 16 + 8 + 4 + 64 * 1024 + 16
        open(sealed.copyOf(firstChunkEnd))
    }

    @Test(expected = WrongBackupPasswordException::class)
    fun `a changed byte is refused`() {
        val sealed = seal(Random(3).nextBytes(5000))
        sealed[sealed.size - 20] = (sealed[sealed.size - 20] + 1).toByte()
        open(sealed)
    }

    @Test
    fun `a plain zip isn't taken for a sealed one`() {
        val zip = ByteArrayOutputStream().also { out ->
            writeBackup(out, emptyList(), emptyList(), emptyList(), { null }, now = 1)
        }.toByteArray()
        assertFalse(BackupCrypto.isSealed(zip))
    }
}
