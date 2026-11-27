package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TotpTest {
    private fun b32(ascii: String): String {
        val bytes = ascii.toByteArray()
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) { bits -= 5; sb.append(alphabet[(buffer shr bits) and 31]) }
        }
        if (bits > 0) sb.append(alphabet[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    private val sha1 = Totp.parse("otpauth://totp/x?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&digits=8")!!

    @Test fun rfc6238Sha1() {
        assertEquals("94287082", Totp.code(sha1, 59_000))
        assertEquals("07081804", Totp.code(sha1, 1_111_111_109_000))
        assertEquals("89005924", Totp.code(sha1, 1_234_567_890_000))
        assertEquals("69279037", Totp.code(sha1, 2_000_000_000_000))
    }

    @Test fun rfc6238Sha256AndSha512() {
        val s256 = Totp.parse("otpauth://totp/x?secret=${b32("12345678901234567890123456789012")}&algorithm=SHA256&digits=8")!!
        assertEquals("46119246", Totp.code(s256, 59_000))
        val s512 = Totp.parse("otpauth://totp/x?secret=${b32("1234567890".repeat(6) + "1234")}&algorithm=SHA512&digits=8")!!
        assertEquals("90693936", Totp.code(s512, 59_000))
    }

    @Test fun bareKeyWithSpacesAndLowerCase() {
        val spec = Totp.parse("gezd gnbv gy3t qojq gezd gnbv gy3t qojq")!!
        assertEquals(6, spec.digits)
        assertEquals(30, spec.period)
        assertEquals("287082", Totp.code(spec, 59_000))
    }

    @Test fun readsIssuerAndAccount() {
        val spec = Totp.parse("otpauth://totp/GitHub:me%40mail.com?secret=GEZDGNBVGY3TQOJQ&period=60")!!
        assertEquals("GitHub", spec.issuer)
        assertEquals("me@mail.com", spec.account)
        assertEquals(60, spec.period)
        val withIssuer = Totp.parse("otpauth://totp/acct?secret=GEZDGNBVGY3TQOJQ&issuer=Acme%20Co")!!
        assertEquals("Acme Co", withIssuer.issuer)
    }

    @Test fun rejectsWhatIsNotAKey() {
        assertNull(Totp.parse(""))
        assertNull(Totp.parse("hello world"))
        assertNull(Totp.parse("ABC1"))
        assertNull(Totp.parse("otpauth://hotp/x?secret=GEZDGNBVGY3TQOJQ&counter=1"))
        assertNull(Totp.parse("otpauth://totp/x?issuer=nobody"))
        assertNull(Totp.parse("otpauth://totp/x?secret=GEZDGNBVGY3TQOJQ&algorithm=MD5"))
    }

    @Test fun countdownAndGrouping() {
        assertEquals(1, Totp.secondsLeft(sha1, 59_000))
        assertEquals(30, Totp.secondsLeft(sha1, 60_000))
        assertEquals("123 456", Totp.grouped("123456"))
        assertEquals("1234 5678", Totp.grouped("12345678"))
    }
}
