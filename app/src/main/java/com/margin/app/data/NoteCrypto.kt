package com.margin.app.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption for locked notes. Each note has its own salt; the key is PBKDF2-HMAC-SHA256 of the notes
 * password, and the body is sealed with AES-256-GCM. A sealed body is text, so it syncs like any field:
 * "j1.<iterations>.<salt>.<iv>.<ciphertext>" (base64url). Only the password can open it, on any device.
 */
object NoteCrypto {
    const val ITERATIONS = 120_000
    private const val VERSION = "j1"
    private val random = SecureRandom()
    private val b64e = Base64.getUrlEncoder().withoutPadding()
    private val b64d = Base64.getUrlDecoder()

    fun newSalt(): ByteArray = ByteArray(16).also(random::nextBytes)

    /** Slow on purpose (about a tenth of a second or more); call it off the main thread. */
    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** What's needed to derive the key for [sealed]: its salt and iteration count. */
    data class KeyParams(val salt: ByteArray, val iterations: Int) {
        val id get() = b64e.encodeToString(salt) + "." + iterations
        override fun equals(other: Any?) = other is KeyParams && id == other.id
        override fun hashCode() = id.hashCode()
    }

    fun paramsOf(sealed: String): KeyParams {
        val parts = sealed.split('.')
        require(parts.size == 5 && parts[0] == VERSION) { "Unknown locked-note format" }
        return KeyParams(b64d.decode(parts[2]), parts[1].toInt())
    }

    fun seal(body: List<Block>, key: SecretKey, params: KeyParams): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(MarginJson.encodeToString(body).toByteArray(Charsets.UTF_8))
        return listOf(VERSION, params.iterations.toString(), b64e.encodeToString(params.salt), b64e.encodeToString(iv), b64e.encodeToString(ct))
            .joinToString(".")
    }

    private val FILE_MAGIC = "MLK1".toByteArray()

    /** A new random key for one attachment file, as stored in [Attachment.key]. */
    fun newFileKey(): String = b64e.encodeToString(ByteArray(32).also(random::nextBytes))

    /** [plain] encrypted with a file key: "MLK1", the 12-byte IV, then AES-256-GCM ciphertext. */
    fun sealBytes(plain: ByteArray, fileKey: String): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(b64d.decode(fileKey), "AES"), GCMParameterSpec(128, iv))
        return FILE_MAGIC + iv + cipher.doFinal(plain)
    }

    /** The file's bytes, or null when [sealed] isn't one or [fileKey] doesn't open it. */
    fun openBytes(sealed: ByteArray, fileKey: String): ByteArray? {
        if (sealed.size < FILE_MAGIC.size + 12 + 16 || !sealed.copyOfRange(0, 4).contentEquals(FILE_MAGIC)) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(b64d.decode(fileKey), "AES"), GCMParameterSpec(128, sealed, 4, 12))
        return try { cipher.doFinal(sealed, 16, sealed.size - 16) } catch (_: AEADBadTagException) { null }
    }

    /** The body blocks, or null if [key] is wrong (wrong password). */
    fun open(sealed: String, key: SecretKey): List<Block>? {
        val parts = sealed.split('.')
        require(parts.size == 5 && parts[0] == VERSION) { "Unknown locked-note format" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b64d.decode(parts[3])))
        return try {
            MarginJson.decodeFromString<List<Block>>(String(cipher.doFinal(b64d.decode(parts[4])), Charsets.UTF_8))
        } catch (_: AEADBadTagException) {
            null
        }
    }
}
