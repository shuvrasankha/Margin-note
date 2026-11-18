package com.margin.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App lock: when it's on, Margin asks for the notes passcode every time it opens or comes back to the front.
 * The passcode is the same one locked notes, the journal and Passwords use; opening the app doesn't unlock them.
 */
@Singleton
class AppLock @Inject constructor(private val prefs: AppPrefs, private val repo: NoteRepository) {
    /** The app lock's sample: non-null while the app lock is on. */
    val state: StateFlow<String?> get() = prefs.appLock
    private val _open = MutableStateFlow(prefs.appLock.value == null)
    /** False while the lock screen is up. */
    val open: StateFlow<Boolean> = _open

    /** A sealed sample the notes passcode opens: a locked note, else the app lock's own. Null if no passcode exists. */
    suspend fun passcodeSample(): String? = repo.lockedNotes().firstOrNull()?.cipher ?: prefs.appLock.value

    /**
     * Opens the app with [code]. Checked against the app lock's sample; if that fails (the passcode was changed
     * on another device), against a locked note, and the sample follows the new passcode. Doesn't clear [code].
     */
    suspend fun unlock(code: CharArray): Boolean {
        val own = prefs.appLock.value ?: return true.also { _open.value = true }
        val ok = opens(code, own) || repo.lockedNotes().firstOrNull()?.cipher?.let { opens(code, it) }?.also { if (it) reseal(code) } == true
        if (ok) _open.value = true
        return ok
    }

    /** Turns the app lock on with [code] (already checked against any locked note). */
    suspend fun turnOn(code: CharArray) { reseal(code); _open.value = true }

    fun turnOff() { prefs.setAppLock(null); _open.value = true }

    /** After a passcode change: the app lock follows it, if it's on. */
    suspend fun passcodeChanged(code: CharArray) { if (prefs.appLock.value != null) reseal(code) }

    /** Shows the lock screen again (the app left the screen), when the app lock is on. */
    fun relock() { if (prefs.appLock.value != null) _open.value = false }

    private suspend fun reseal(code: CharArray) {
        val params = NoteCrypto.KeyParams(NoteCrypto.newSalt(), NoteCrypto.ITERATIONS)
        val key = withContext(Dispatchers.Default) { NoteCrypto.deriveKey(code, params.salt, params.iterations) }
        prefs.setAppLock(NoteCrypto.seal(listOf(Block(text = "margin")), key, params))
    }

    private suspend fun opens(code: CharArray, sealed: String): Boolean = withContext(Dispatchers.Default) {
        val p = NoteCrypto.paramsOf(sealed)
        NoteCrypto.open(sealed, NoteCrypto.deriveKey(code, p.salt, p.iterations)) != null
    }
}
