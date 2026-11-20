package com.margin.app.data

import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** How a site's authenticator codes are made: the secret and the RFC 6238 settings from its setup key or QR code. */
data class OtpSpec(
    val secret: ByteArray,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
    val issuer: String = "",
    val account: String = "",
) {
    override fun equals(other: Any?) = other is OtpSpec && secret.contentEquals(other.secret) && algorithm == other.algorithm &&
        digits == other.digits && period == other.period && issuer == other.issuer && account == other.account
    override fun hashCode() = secret.contentHashCode() * 31 + algorithm.hashCode()
}

/** Time-based one-time codes (RFC 6238), the six digits an authenticator app shows. */
object Totp {
    /**
     * Reads what the user pasted or scanned: an `otpauth://totp/…` link, or a bare base32 setup key
     * (spaces, dashes and lower case allowed). Null if it isn't one.
     */
    fun parse(input: String): OtpSpec? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (!text.startsWith("otpauth://", ignoreCase = true)) return base32(text)?.let { OtpSpec(it) }
        val rest = text.substring("otpauth://".length)
        val type = rest.substringBefore('/')
        if (!type.equals("totp", ignoreCase = true)) return null
        val label = decode(rest.substringAfter('/', "").substringBefore('?'))
        val query = rest.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=').lowercase() to decode(it.substringAfter('=')) }
        val secret = query["secret"]?.let(::base32) ?: return null
        val algorithm = (query["algorithm"] ?: "SHA1").uppercase().replace("-", "")
        if (algorithm !in setOf("SHA1", "SHA256", "SHA512")) return null
        val digits = query["digits"]?.toIntOrNull() ?: 6
        val period = query["period"]?.toIntOrNull() ?: 30
        if (digits !in 6..8 || period !in 1..300) return null
        val issuer = query["issuer"] ?: label.substringBefore(':', "").trim()
        val account = if (':' in label) label.substringAfter(':').trim() else label.trim()
        return OtpSpec(secret, algorithm, digits, period, issuer, account)
    }

    /** The code for [spec] at [millis], zero-padded to its digits. */
    fun code(spec: OtpSpec, millis: Long): String {
        val counter = Math.floorDiv(millis / 1000, spec.period.toLong())
        val mac = Mac.getInstance("Hmac${spec.algorithm}")
        mac.init(SecretKeySpec(spec.secret, "RAW"))
        val hash = mac.doFinal(ByteArray(8) { i -> (counter ushr (56 - 8 * i)).toByte() })
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or (hash[offset + 3].toInt() and 0xff)
        var mod = 1L
        repeat(spec.digits) { mod *= 10 }
        return (binary % mod).toString().padStart(spec.digits, '0')
    }

    /** Seconds until the code at [millis] changes. */
    fun secondsLeft(spec: OtpSpec, millis: Long): Int = spec.period - ((millis / 1000) % spec.period).toInt()

    /** "123 456" for reading; eight digits as "1234 5678". */
    fun grouped(code: String): String = if (code.length < 6) code else code.substring(0, code.length / 2) + " " + code.substring(code.length / 2)

    /** Base32 (RFC 4648) to bytes, ignoring spaces, dashes, padding and case. Null if a character isn't base32 or it's too short. */
    fun base32(text: String): ByteArray? {
        val clean = text.filterNot { it.isWhitespace() || it == '-' || it == '=' }.uppercase()
        if (clean.length < 16) return null // 80 bits; anything shorter is a typo, not a setup key
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xff)
            }
        }
        return out.toByteArray()
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun decode(s: String) = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
}
