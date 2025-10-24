package com.iie.st10320489.stylu.ui.auth

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class BiometricAuthManager(private val context: Context) {

    private val TAG = "BiometricAuthManager"

    private val sharedPreferences: SharedPreferences by lazy {
        createEncryptedPreferences()
    }

    companion object {
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SAVED_EMAIL = "saved_email"
        private const val KEY_SAVED_PASSWORD = "saved_password"
        private const val PREFS_NAME = "biometric_prefs"
    }

    private fun createEncryptedPreferences(): SharedPreferences {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } else {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating EncryptedSharedPreferences, falling back to regular: ${e.message}")

            // If EncryptedSharedPreferences fails, delete the corrupted file and retry
            try {
                context.deleteSharedPreferences(PREFS_NAME)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    EncryptedSharedPreferences.create(
                        context,
                        PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } else {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
            } catch (retryException: Exception) {
                Log.e(TAG, "Retry failed, using regular SharedPreferences: ${retryException.message}")
                // Last resort: use regular SharedPreferences
                context.getSharedPreferences("biometric_prefs_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    fun isBiometricAvailable(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            else -> BiometricStatus.UNSUPPORTED
        }
    }

    fun enableBiometricLogin(email: String, password: String) {
        try {
            sharedPreferences.edit().apply {
                putBoolean(KEY_BIOMETRIC_ENABLED, true)
                putString(KEY_SAVED_EMAIL, email)
                putString(KEY_SAVED_PASSWORD, password)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving biometric credentials: ${e.message}")
        }
    }

    fun disableBiometricLogin() {
        try {
            sharedPreferences.edit().apply {
                putBoolean(KEY_BIOMETRIC_ENABLED, false)
                remove(KEY_SAVED_EMAIL)
                remove(KEY_SAVED_PASSWORD)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling biometric: ${e.message}")
        }
    }

    fun isBiometricEnabled(): Boolean {
        return try {
            sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking biometric status: ${e.message}")
            false
        }
    }

    fun getSavedCredentials(): Pair<String, String>? {
        return try {
            if (!isBiometricEnabled()) return null

            val email = sharedPreferences.getString(KEY_SAVED_EMAIL, null)
            val password = sharedPreferences.getString(KEY_SAVED_PASSWORD, null)

            if (email != null && password != null) {
                Pair(email, password)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving credentials: ${e.message}")
            // Clear corrupted data
            disableBiometricLogin()
            null
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Biometric Authentication",
        subtitle: String = "Log in using your biometric credential",
        negativeButtonText: String = "Use password",
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (Int, CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

enum class BiometricStatus {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED,
    UNSUPPORTED
}