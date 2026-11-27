package com.margin.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens and saves vault entries with a [LockSession]: the Passwords screen uses the app's session, autofill a
 * session of its own that ends with its screen. Every entry shares one salt, so the vault derives a single key.
 */
@Singleton
class VaultStore @Inject constructor(private val repo: NoteRepository) {
    /** Every saved entry (not in Recently Deleted) as (id, login), opened with [session]; empty while locked. */
    suspend fun openAll(session: LockSession): List<Pair<String, Login>> = withContext(Dispatchers.Default) {
        repo.liveNotes().first().filter { isLogin(it) && it.cipher != null && it.trashedAt == null && !it.deleted }
            .mapNotNull { n -> session.open(n.cipher!!)?.let(::loginFrom)?.let { n.id to it } }
    }

    /** Whether anything is saved, without opening it: autofill offers nothing on an empty vault. */
    suspend fun isEmpty() = repo.liveNotes().first().none { isLogin(it) && it.cipher != null && it.trashedAt == null && !it.deleted }

    /**
     * Saves [login] as a new entry ([id] null) or over [id]. The stored one is read back first, so a changed
     * password always goes to its history, whoever saves it. Returns the id, or null when [session] is locked
     * or unlocked with a 6-digit passcode (too few to keep saved passwords safe once they sync or are backed up).
     */
    suspend fun save(session: LockSession, id: String?, login: Login, stamp: Boolean = true): String? {
        if (!session.passphrase.value) return null
        val params = repo.liveNotes().first().firstOrNull { isLogin(it) && it.cipher != null }?.cipher?.let(NoteCrypto::paramsOf)
            ?: NoteCrypto.KeyParams(NoteCrypto.newSalt(), NoteCrypto.ITERATIONS)
        val key = session.key(params) ?: return null
        val old = id?.let { repo.getNote(it)?.cipher }?.let { session.open(it) }?.let(::loginFrom)
        val stamped = if (stamp) stampPasswordChange(old, login, System.currentTimeMillis()) else login
        val body = loginBody(stamped.trimmed())
        val cipher = withContext(Dispatchers.Default) { NoteCrypto.seal(body, key, params) }
        val target = id ?: (LOGIN_PREFIX + newId())
        repo.ensureDefaultFolder()
        repo.saveNote(target, DEFAULT_FOLDER_ID, listOf(Block(type = BlockType.TITLE, text = LOGIN_TITLE)) + body, cipher)
        return target
    }

    /** Sets [id]'s icon, leaving everything else (and the password's change time) as stored. */
    suspend fun setIcon(session: LockSession, id: String, icon: String) {
        val stored = repo.getNote(id)?.takeIf { it.trashedAt == null && !it.deleted }?.cipher?.let { session.open(it) }?.let(::loginFrom) ?: return
        if (stored.icon != icon) save(session, id, stored.copy(icon = icon), stamp = false)
    }

    suspend fun delete(id: String) = repo.moveToTrash(id)

    private fun Login.trimmed() = copy(name = name.trim(), username = username.trim(), url = url.trim(), notes = notes.trim())
}
