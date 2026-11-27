package com.margin.app.data

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import com.margin.app.R

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
    /** Passwords this login used before, newest first, at most [HISTORY_LIMIT]. */
    val history: List<PastPassword> = emptyList(),
    /** What it's for: a [VaultKind] name. Older entries are all logins. */
    val kind: String = VaultKind.LOGIN.name,
    /** Android apps (package names) this login fills, linked when it was saved or picked from one. */
    val apps: List<String> = emptyList(),
    /** The site's or app's icon as a small base64 PNG, captured when it was saved; empty for a monogram. */
    val icon: String = "",
    /** Wi-Fi only: a [WifiSecurity] name. */
    val security: String = "",
    /** Logins only: the authenticator setup key (an otpauth:// link or a base32 key) its 2FA codes come from; empty for none. */
    val otp: String = "",
)

/** What a vault entry holds. Only logins are offered for autofill. */
enum class VaultKind(val label: String, @androidx.annotation.StringRes val labelRes: Int) {
    LOGIN("Login", R.string.vault_kind_login), WIFI("Wi-Fi", R.string.vault_kind_wifi), DEVICE("Device or drive", R.string.vault_kind_device), OTHER("Other", R.string.vault_kind_other);
    companion object { fun of(name: String) = entries.firstOrNull { it.name == name } ?: LOGIN }
}

enum class WifiSecurity(val label: String, @androidx.annotation.StringRes val labelRes: Int) {
    WPA("WPA/WPA2", R.string.wifi_security_wpa), WPA3("WPA3", R.string.wifi_security_wpa3), WEP("WEP", R.string.wifi_security_wep), OPEN("None", R.string.wifi_security_open);
    companion object { fun of(name: String) = entries.firstOrNull { it.name == name } ?: WPA }
}

val Login.vaultKind get() = VaultKind.of(kind)

/** A password a login no longer uses, and when it was replaced. */
@Serializable
data class PastPassword(val password: String, val replacedAt: Long)

const val HISTORY_LIMIT = 5

/**
 * [new] as it should be saved over [old]: a changed password gets a fresh change time, and the one it
 * replaces goes to the front of the history (unless the new one is simply an old one coming back).
 */
fun stampPasswordChange(old: Login?, new: Login, now: Long): Login {
    if (old != null && old.password == new.password) return new.copy(history = old.history)
    val history = if (old == null) emptyList()
    else (listOfNotNull(PastPassword(old.password, now).takeIf { old.password.isNotEmpty() }) + old.history).filter { it.password != new.password }.distinctBy { it.password }.take(HISTORY_LIMIT)
    return new.copy(passwordChangedAt = now, history = history)
}

/** Words a guesser would try for this login: its name, the username before the @, the site's name. */
fun Login.relatedWords(): List<String> =
    (name.split(Regex("[^\\p{L}\\p{N}]+")) + username.substringBefore('@') + siteOf(url).substringBefore('.'))
        .map { it.lowercase() }.filter { it.length >= 3 }.distinct()

const val LOGIN_PREFIX = "login-"
const val LOGIN_TITLE = "Saved password"

fun isLoginId(id: String) = id.startsWith(LOGIN_PREFIX)
fun isLogin(note: NoteEntity) = isLoginId(note.id)

/** Saved passwords may only be backed up encrypted with a backup password (their notes passcode may be 6 digits). */
fun needsBackupPassword(notes: List<NoteEntity>, hasBackupPassword: Boolean) = !hasBackupPassword && notes.any { isLogin(it) && it.cipher != null }

/** A login as the note body to seal: one line holding it as JSON. */
fun loginBody(login: Login): List<Block> = listOf(Block(text = MarginJson.encodeToString(Login.serializer(), login)))

/** The login in an opened body, or null if it isn't one. */
fun loginFrom(body: List<Block>): Login? =
    body.firstOrNull()?.text?.let { runCatching { MarginJson.decodeFromString(Login.serializer(), it) }.getOrNull() }

/** "https://www.github.com/login" → "github.com", for showing and matching sites. */
fun siteOf(url: String): String =
    url.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').removePrefix("www.").lowercase()

/**
 * Whether a page on [visited] may use a login saved for [saved]: the same host, or one under it
 * ("accounts.github.com" for "github.com"). Never a mere substring: "github.com.evil.io" isn't github.com.
 */
fun hostMatches(saved: String, visited: String): Boolean {
    val a = siteOf(saved); val b = siteOf(visited)
    return a.isNotEmpty() && '.' in a && (b == a || b.endsWith(".$a"))
}

/**
 * Whether this login is offered on a form in app [pkg] showing site [domain]. [domain] counts only when the
 * caller trusts it (it came from a browser): any other app could claim to be any site.
 */
fun Login.fills(pkg: String?, domain: String?): Boolean =
    vaultKind == VaultKind.LOGIN && (
        (pkg != null && pkg in apps) || (domain != null && url.isNotBlank() && hostMatches(url, domain))
    )

/** What saving a sign-in seen by autofill does to the vault. */
sealed interface AutofillSave {
    /** A new login. */
    data class Create(val login: Login) : AutofillSave
    /** [id]'s login with the new password (its old one goes to history when stamped) or a newly linked app. */
    data class Update(val id: String, val login: Login) : AutofillSave
    /** Several logins could be the one that changed; the user picks. */
    data class Choose(val ids: List<String>) : AutofillSave
    /** Already saved exactly so. */
    data object Unchanged : AutofillSave
}

/**
 * Decides what a sign-in [username]/[password] typed into [pkg] (on [domain], when trusted) does: updates
 * the matching login when its password changed, links the app to it, or makes a new login named [label].
 */
fun planAutofillSave(
    entries: List<Pair<String, Login>>, pkg: String?, domain: String?, username: String, password: String, label: String,
): AutofillSave {
    val user = username.trim()
    val candidates = entries.filter { (_, l) -> l.fills(pkg, domain) }
    val same = if (user.isEmpty()) candidates else candidates.filter { (_, l) -> l.username.trim().equals(user, ignoreCase = true) }
    val target = when {
        same.size == 1 -> same.single()
        same.size > 1 -> same.firstOrNull { it.second.password == password } ?: return AutofillSave.Choose(same.map { it.first })
        else -> null
    }
    if (target == null) {
        val site = domain?.let(::siteOf).orEmpty()
        return AutofillSave.Create(
            Login(
                name = label.ifBlank { site }, username = user, password = password, url = site,
                apps = listOfNotNull(pkg.takeIf { site.isEmpty() }),
            ),
        )
    }
    val (id, old) = target
    val linked = if (pkg != null && domain == null && pkg !in old.apps) old.apps + pkg else old.apps
    val updated = old.copy(password = password, username = old.username.ifBlank { user }, apps = linked)
    return if (updated == old) AutofillSave.Unchanged else AutofillSave.Update(id, updated)
}

enum class Strength(val label: String, @androidx.annotation.StringRes val labelRes: Int) { WEAK("Weak", R.string.strength_weak), FAIR("Fair", R.string.strength_fair), GOOD("Good", R.string.strength_good), STRONG("Strong", R.string.strength_strong) }

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

    // The passwords tried first; a word from here, however dressed up, is weak.
    private val COMMON = setOf(
        "password", "passwort", "123456", "12345678", "123456789", "qwerty", "qwertyuiop", "letmein", "welcome", "admin",
        "administrator", "iloveyou", "monkey", "dragon", "football", "baseball", "soccer", "sunshine", "princess", "master",
        "shadow", "superman", "batman", "trustno1", "abc123", "login", "starwars", "whatever", "freedom", "hello", "secret",
        "michael", "charlie", "access", "computer", "summer", "winter", "spring", "autumn", "flower", "cheese", "hunter",
        "ninja", "mustang", "jordan", "pokemon", "google", "default", "changeme", "guest", "root", "test", "lovely",
        "love", "family", "pass", "user", "qazwsx", "asdfgh", "zxcvbn", "111111", "000000", "121212", "654321",
    )
    private val ROWS = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm", "!@#$%^&*()")
    private val LEET = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's', '!' to 'i')
    private fun unleet(s: String) = s.map { LEET[it] ?: it }.joinToString("")

    /**
     * A rough guess from length and character variety. Common passwords (also with leet swaps or digits tacked
     * on), runs like "abc", "321" or "qwerty", repeats, and [related] words such as the site or username count
     * against it.
     */
    fun strength(password: String, related: List<String> = emptyList()): Strength {
        if (password.isEmpty()) return Strength.WEAK
        // Per character, so indices still line up with [password] ("İ".lowercase() is two characters).
        val lower = password.map { it.lowercaseChar() }.joinToString("")
        val core = unleet(lower.trim { !it.isLetter() })
        if (lower in COMMON || unleet(lower) in COMMON || core in COMMON) return Strength.WEAK
        var pool = 0
        if (password.any { it.isLowerCase() }) pool += 26
        if (password.any { it.isUpperCase() }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 20
        // Characters a guesser gets almost for free.
        val guessable = BooleanArray(password.length)
        fun mark(from: Int, until: Int) { for (k in from until until) guessable[k] = true }
        // Runs of three or more: repeats, alphabet or digit order either way, or along a keyboard row.
        val rowPos = buildMap { ROWS.forEachIndexed { r, row -> row.forEachIndexed { i, c -> put(c, r to i) } } }
        fun runs(next: (Char, Char) -> Boolean) {
            var start = 0
            for (k in 1..lower.length) {
                if (k < lower.length && next(lower[k - 1], lower[k])) continue
                if (k - start >= 3) mark(start, k)
                start = k
            }
        }
        for (step in -1..1) {
            runs { a, b -> b - a == step }
            if (step != 0) runs { a, b -> val pa = rowPos[a]; val pb = rowPos[b]; pa != null && pb != null && pa.first == pb.first && pb.second - pa.second == step }
        }
        // Common words and the login's own words, wherever they sit.
        val plain = unleet(lower)
        for (word in (COMMON.filter { it.length >= 4 && it.any(Char::isLetter) } + related.map { it.lowercase() }.filter { it.length >= 3 }).map(::unleet).distinct()) {
            var at = plain.indexOf(word)
            while (at >= 0) { mark(at, at + word.length); at = plain.indexOf(word, at + 1) }
        }
        val free = password.indices.filterNot { guessable[it] }
        // Each unguessable character adds log2(pool) bits, counted once per distinct character beyond the first
        // few; guessable ones add a quarter of that.
        val distinct = free.map { password[it] }.toSet().size
        val effective = minOf(free.size.toDouble(), distinct + 3.0) + (password.length - free.size) / 4.0
        val bits = effective * (Math.log(pool.toDouble()) / Math.log(2.0))
        return when {
            bits < 40 -> Strength.WEAK
            bits < 60 -> Strength.FAIR
            bits < 80 -> Strength.GOOD
            else -> Strength.STRONG
        }
    }
}
