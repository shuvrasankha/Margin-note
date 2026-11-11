package com.shuva.jotter.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatus(
    val email: String? = null,
    val name: String? = null,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
) {
    val signedIn get() = email != null
}

/** Account and last-sync info. No tokens are stored; they're fetched fresh per sync. */
@Singleton
class SyncPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("jotter_sync", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(read())
    val status: StateFlow<SyncStatus> = _status

    val isSignedIn get() = _status.value.signedIn

    fun setAccount(email: String, name: String?) = edit { putString(K_EMAIL, email).putString(K_NAME, name).remove(K_ERROR) }
    fun clearAccount() = edit { clear() }
    fun recordSuccess(at: Long) = edit { putLong(K_LAST, at).remove(K_ERROR) }
    fun recordError(message: String) = edit { putString(K_ERROR, message) }

    private fun edit(block: android.content.SharedPreferences.Editor.() -> android.content.SharedPreferences.Editor) {
        prefs.edit().block().apply()
        _status.value = read()
    }

    private fun read() = SyncStatus(
        email = prefs.getString(K_EMAIL, null),
        name = prefs.getString(K_NAME, null),
        lastSyncAt = prefs.getLong(K_LAST, 0L).takeIf { it > 0 },
        lastError = prefs.getString(K_ERROR, null),
    )

    private companion object {
        const val K_EMAIL = "email"
        const val K_NAME = "name"
        const val K_LAST = "lastSyncAt"
        const val K_ERROR = "lastError"
    }
}
