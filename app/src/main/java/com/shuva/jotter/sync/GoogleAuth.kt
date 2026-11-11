package com.shuva.jotter.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.shuva.jotter.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

/** Drive access needs a one-time user approval; the UI launches [pendingIntent]. */
class NeedsUserConsentException(val pendingIntent: PendingIntent) : Exception("Google Drive access needs your approval")

data class GoogleAccount(val email: String, val displayName: String?)

/** Step 1: "Sign in with Google" through Credential Manager. Needs an Activity. */
suspend fun signInWithGoogle(activity: Activity): GoogleAccount {
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(activity).getCredential(activity, request).credential
    require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "Unexpected credential type"
    }
    val google = GoogleIdTokenCredential.createFrom(credential.data)
    return GoogleAccount(google.id, google.displayName)
}

suspend fun signOutOfGoogle(context: Context) {
    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
}

/** Step 2: AuthorizationClient for the drive.appdata scope only. Tokens are never stored. */
@Singleton
class DriveAuthorizer @Inject constructor(@ApplicationContext private val context: Context) {
    private val client get() = Identity.getAuthorizationClient(context)

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        .build()

    suspend fun authorize(): AuthorizationResult = client.authorize(request()).await()

    /** Returns a token, or throws [NeedsUserConsentException] if the user must approve first. */
    suspend fun accessToken(): String {
        val result = authorize()
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) throw NeedsUserConsentException(pending)
        return result.accessToken ?: error("Google returned no access token")
    }

    fun resultFromIntent(data: Intent?): AuthorizationResult = client.getAuthorizationResultFromIntent(data)

    suspend fun clearToken(token: String) = withContext(Dispatchers.IO) { GoogleAuthUtil.clearToken(context, token) }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
