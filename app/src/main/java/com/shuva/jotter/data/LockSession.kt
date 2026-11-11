package com.shuva.jotter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/** Locked notes use a passcode of this many digits. */
const val PASSCODE_LENGTH = 6

/** A 6-digit passcode, as opposed to a password from before passcodes. */
fun isPasscode(p: CharArray) = p.size == PASSCODE_LENGTH && p.all { it in '0'..'9' }

/**
 * Holds the notes password in memory while locked notes are unlocked. The app locks again when it goes
 * to the background ([lock]). Derived keys are cached per note salt, so saving an open locked note never
 * waits on key derivation.
 */
@Singleton
class LockSession @Inject constructor() {
    private var password: CharArray? = null
    private val keys = mutableMapOf<NoteCrypto.KeyParams, SecretKey>()
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked
    private val _legacy = MutableStateFlow(false)
    /** Unlocked with an old-style password: the app asks to switch to a passcode. */
    val legacy: StateFlow<Boolean> = _legacy

    /** Opens [sealed] with [candidate]; on success the session is unlocked with that password. */
    suspend fun unlock(candidate: CharArray, sealed: String): List<Block>? {
        val params = NoteCrypto.paramsOf(sealed)
        val key = withContext(Dispatchers.Default) { NoteCrypto.deriveKey(candidate, params.salt, params.iterations) }
        val body = NoteCrypto.open(sealed, key) ?: return null
        set(candidate)
        keys[params] = key
        return body
    }

    /** Unlocks with a new password when no locked note exists yet to check it against. */
    fun startWith(newPassword: CharArray) = set(newPassword)

    private fun set(newPassword: CharArray) {
        password?.fill('\u0000')
        password = newPassword.copyOf()
        keys.clear()
        _unlocked.value = true
        _legacy.value = !isPasscode(newPassword)
    }

    fun lock() {
        password?.fill('\u0000')
        password = null
        keys.clear()
        _unlocked.value = false
        _legacy.value = false
    }

    /** A copy of the password, for fingerprint setup. Null when locked. */
    fun passwordCopy(): CharArray? = password?.copyOf()

    /** Key for these params, deriving it with the session password if needed. Null when locked. */
    suspend fun key(params: NoteCrypto.KeyParams): SecretKey? {
        keys[params]?.let { return it }
        val pw = password ?: return null
        val key = withContext(Dispatchers.Default) { NoteCrypto.deriveKey(pw, params.salt, params.iterations) }
        keys[params] = key
        return key
    }

    /** Only returns an already-derived key: safe to call on the main thread. */
    fun cachedKey(params: NoteCrypto.KeyParams): SecretKey? = if (password == null) null else keys[params]

    suspend fun open(sealed: String): List<Block>? = key(NoteCrypto.paramsOf(sealed))?.let { NoteCrypto.open(sealed, it) }
}
