package com.shuva.jotter.ui

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/**
 * Fingerprint unlock for locked notes. The notes password is encrypted with an Android Keystore key that
 * only works right after a strong biometric check, and is invalidated when fingerprints are added or
 * removed. The encrypted password stays on this device.
 */
object Biometric {
    private const val ALIAS = "jotter_notes_password"

    fun available(activity: FragmentActivity) =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    sealed interface Result {
        data class Ok(val bytes: ByteArray, val iv: ByteArray) : Result
        /** The user backed out or chose the password instead. */
        data object Cancelled : Result
        /** Fingerprints changed since setup, or the key is gone: fingerprint unlock must be set up again. */
        data object Invalidated : Result
        data class Failed(val message: String) : Result
    }

    /** Encrypts [secret] after a fingerprint check. Returns its ciphertext and IV. */
    suspend fun encrypt(activity: FragmentActivity, secret: ByteArray): Result {
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, newKey()) }
        } catch (e: Exception) {
            return Result.Failed(e.message ?: "Couldn't set up fingerprint unlock")
        }
        return prompt(activity, cipher, "Turn on fingerprint unlock") { it.doFinal(secret) to it.iv }
    }

    suspend fun decrypt(activity: FragmentActivity, data: ByteArray, iv: ByteArray): Result {
        val key = existingKey() ?: return Result.Invalidated
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            forget()
            return Result.Invalidated
        } catch (e: Exception) {
            return Result.Failed(e.message ?: "Fingerprint unlock failed")
        }
        return prompt(activity, cipher, "Unlock notes") { it.doFinal(data) to iv }
    }

    fun forget() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun existingKey(): SecretKey? =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(ALIAS, null) as? SecretKey

    private fun newKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }

    private suspend fun prompt(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        finish: (Cipher) -> Pair<ByteArray, ByteArray>,
    ): Result = suspendCancellableCoroutine { cont ->
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                val out = runCatching { finish(c!!) }
                if (cont.isActive) {
                    cont.resume(out.fold({ (bytes, iv) -> Result.Ok(bytes, iv) }, { Result.Failed(it.message ?: "Fingerprint unlock failed") }))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!cont.isActive) return
                val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                cont.resume(if (cancelled) Result.Cancelled else Result.Failed(errString.toString()))
            }
            // onAuthenticationFailed (finger not recognized) keeps the prompt open; nothing to do.
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("Use password")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
