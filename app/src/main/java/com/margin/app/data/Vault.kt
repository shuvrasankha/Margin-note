package com.margin.app.data

import kotlinx.serialization.Serializable
import java.security.SecureRandom

/**
 * The password vault. Each saved login is a locked note with an id starting [LOGIN_PREFIX]: it is encrypted
 * with the notes passcode, syncs, and goes to Recently Deleted like any other note. Its readable title is
 * only [LOGIN_TITLE]; the site name sits inside the encrypted body with everything else, so nothing about
 * the login can be read without the passcode. Every login shares one salt, so the list derives a single key.
 */
@Serializable
data class Login(
    val name: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    /** When the password itself last changed. */
    val passwordChangedAt: Long = 0,
)

const val LOGIN_PREFIX = "login-"
const val LOGIN_TITLE = "Saved password"

fun isLoginId(id: String) = id.startsWith(LOGIN_PREFIX)
fun isLogin(note: NoteEntity) = isLoginId(note.id)

/** A login as the note body to seal: one line holding it as JSON. */
fun loginBody(login: Login): List<Block> = listOf(Block(text = MarginJson.encodeToString(Login.serializer(), login)))

/** The login in an opened body, or null if it isn't one. */
fun loginFrom(body: List<Block>): Login? =
    body.firstOrNull()?.text?.let { runCatching { MarginJson.decodeFromString(Login.serializer(), it) }.getOrNull() }

/** "https://www.github.com/login" → "github.com", for showing and matching sites. */
fun siteOf(url: String): String =
    url.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').removePrefix("www.").lowercase()

enum class Strength(val label: String) { WEAK("Weak"), FAIR("Fair"), GOOD("Good"), STRONG("Strong") }

object PasswordGen {
    private val random = SecureRandom()
    // No look-alikes (0/O, 1/l/I), so a generated password can be read off a screen and typed.
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%^&*-_=+?"

    /** A random password of [length] with at least one character from each chosen set. */
    fun generate(length: Int = 20, digits: Boolean = true, symbols: Boolean = true): String {
        val sets = listOfNotNull(LOWER, UPPER, DIGITS.takeIf { digits }, SYMBOLS.takeIf { symbols })
        val all = sets.joinToString("")
        val n = length.coerceAtLeast(sets.size)
        val chars = (sets.map { it[random.nextInt(it.length)] } + List(n - sets.size) { all[random.nextInt(all.length)] }).toMutableList()
        for (i in chars.indices.reversed()) { val j = random.nextInt(i + 1); chars[i] = chars[j].also { chars[j] = chars[i] } }
        return chars.joinToString("")
    }

    /** A rough guess from length and character variety; repeats and short passwords count against it. */
    fun strength(password: String): Strength {
        if (password.isEmpty()) return Strength.WEAK
        var pool = 0
        if (password.any { it.isLowerCase() }) pool += 26
        if (password.any { it.isUpperCase() }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 20
        val bits = password.toSet().size.coerceAtMost(password.length).let { distinct ->
            // Each character adds log2(pool) bits, counted only once per distinct character beyond the first few.
            (minOf(password.length, distinct + 3)) * (Math.log(pool.toDouble()) / Math.log(2.0))
        }
        return when {
            bits < 40 -> Strength.WEAK
            bits < 60 -> Strength.FAIR
            bits < 80 -> Strength.GOOD
            else -> Strength.STRONG
        }
    }
}
