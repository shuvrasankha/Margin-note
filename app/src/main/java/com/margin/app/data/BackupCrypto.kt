package com.margin.app.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A backup sealed with a backup password, so the cloud it's kept in can't read it.
 *
 *   "MARGINBK"  magic, 8 bytes
 *   1           format, 1 byte
 *   iterations  4 bytes, PBKDF2-SHA256
 *   salt        16 bytes
 *   nonce       8 bytes, the start of each chunk's 12-byte IV
 *   chunks      [length: 4 bytes][AES-GCM ciphertext], each up to [CHUNK] bytes of the zip
 *
 * The zip goes in chunks so neither side has to hold a whole backup (photos and all) in memory. Each chunk's IV
 * is the nonce and its number, and its tag also covers whether it's the last one, so chunks can't be reordered,
 * dropped or cut off without the restore noticing.
 */
object BackupCrypto {
    private val MAGIC = "MARGINBK".toByteArray(Charsets.US_ASCII)
    private const val FORMAT: Byte = 1
    private const val CHUNK = 64 * 1024
    private const val TAG_BITS = 128
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    /** Whether a backup file starting with [head] (its first 8 bytes or more) is sealed. */
    fun isSealed(head: ByteArray) = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    fun isSealed(input: InputStream): Boolean {
        val head = ByteArray(MAGIC.size)
        val n = input.readNBytesCompat(head)
        return n == head.size && isSealed(head)
    }

    fun seal(input: InputStream, out: OutputStream, password: CharArray, iterations: Int = NoteCrypto.ITERATIONS) {
        val salt = NoteCrypto.newSalt()
        val nonce = ByteArray(8).also(random::nextBytes)
        val key = NoteCrypto.deriveKey(password, salt, iterations)
        val data = DataOutputStream(out)
        data.write(MAGIC); data.writeByte(FORMAT.toInt()); data.writeInt(iterations); data.write(salt); data.write(nonce)
        val gcm = Cipher.getInstance(TRANSFORM)
        val sealed = ByteArray(CHUNK + TAG_BITS / 8)
        var buf = ByteArray(CHUNK)
        var next = ByteArray(CHUNK)
        var have = input.readNBytesCompat(buf)
        var index = 0
        while (true) {
            // Read ahead one chunk to know whether this one is the last.
            val more = if (have == CHUNK) input.readNBytesCompat(next) else 0
            val last = more == 0
            val n = gcm.start(Cipher.ENCRYPT_MODE, key, nonce, index, last).doFinal(buf, 0, have, sealed, 0)
            data.writeInt(n); data.write(sealed, 0, n)
            if (last) break
            buf = next.also { next = buf }; have = more; index++
        }
        data.flush()
    }

    /** Opens a sealed backup into [out]. Throws [WrongBackupPasswordException] for a wrong password or a damaged file. */
    fun open(input: InputStream, out: OutputStream, password: CharArray) {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size).also { data.readFully(it) }
        if (!isSealed(magic)) throw NotABackupException()
        if (data.readByte() > FORMAT) throw NewerBackupException()
        val iterations = data.readInt()
        if (iterations !in 10_000..10_000_000) throw WrongBackupPasswordException()
        val salt = ByteArray(16).also { data.readFully(it) }
        val nonce = ByteArray(8).also { data.readFully(it) }
        val key = NoteCrypto.deriveKey(password, salt, iterations)
        val gcm = Cipher.getInstance(TRANSFORM)
        var index = 0
        while (true) {
            val size = try { data.readInt() } catch (_: EOFException) { throw WrongBackupPasswordException() } // cut off
            if (size !in TAG_BITS / 8..CHUNK + TAG_BITS / 8) throw WrongBackupPasswordException()
            val sealed = ByteArray(size).also { data.readFully(it) }
            // Not knowing yet whether it's the last chunk, try it as a middle one first.
            val plain = runCatching { gcm.start(Cipher.DECRYPT_MODE, key, nonce, index, last = false).doFinal(sealed) }.getOrNull()
            if (plain != null) { out.write(plain); index++; continue }
            try {
                out.write(gcm.start(Cipher.DECRYPT_MODE, key, nonce, index, last = true).doFinal(sealed))
            } catch (_: AEADBadTagException) {
                throw WrongBackupPasswordException()
            }
            if (data.read() != -1) throw WrongBackupPasswordException() // something after the last chunk
            break
        }
        out.flush()
    }

    /** Re-inits [this] for chunk [index]: one Cipher serves a whole backup, each chunk with its own IV. */
    private fun Cipher.start(mode: Int, key: SecretKey, nonce: ByteArray, index: Int, last: Boolean): Cipher = apply {
        init(mode, key, GCMParameterSpec(TAG_BITS, ByteBuffer.allocate(12).put(nonce).putInt(index).array()))
        updateAAD(byteArrayOf(if (last) 1 else 0))
    }

    /** Reads until [buf] is full or the stream ends; how many bytes came. */
    private fun InputStream.readNBytesCompat(buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }
}

class WrongBackupPasswordException : Exception("That password doesn't open this backup")

/** The backup is sealed and no password was given (or the saved one is a different one). */
class BackupPasswordNeededException : Exception("This backup is protected with a password")
