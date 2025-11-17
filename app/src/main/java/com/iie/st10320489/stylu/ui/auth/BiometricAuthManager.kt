package com.iie.st10320489.stylu.ui.auth

import android.content.Context
import android.content.SharedPreferences
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
        getReliableSharedPreferences()
    }

    companion object {
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SAVED_EMAIL = "saved_email"
        private const val KEY_SAVED_PASSWORD = "saved_password"
        private const val PREFS_NAME = "stylu_biometric_v2"
    }


    private fun getReliableSharedPreferences(): SharedPreferences {
        Log.d(TAG, "Initializing SharedPreferences...")

        return try {
            // Try encrypted first
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encrypted = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            Log.d(TAG, "Using EncryptedSharedPreferences")
            encrypted

        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences failed: ${e.message}")
            Log.d(TAG, "Falling back to regular SharedPreferences")


            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isBiometricAvailable(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val result = when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            else -> BiometricStatus.UNSUPPORTED
        }
        Log.d(TAG, "isBiometricAvailable: $result")
        return result
    }

    fun enableBiometricLogin(email: String, password: String) {
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "ENABLING BIOMETRIC LOGIN")
        Log.d(TAG, "   - Email: $email")
        Log.d(TAG, "   - Password length: ${password.length}")

        try {
            // Save to SharedPreferences
            val editor = sharedPreferences.edit()
            editor.putBoolean(KEY_BIOMETRIC_ENABLED, true)
            editor.putString(KEY_SAVED_EMAIL, email)
            editor.putString(KEY_SAVED_PASSWORD, password)

            // Use commit() instead of apply() to ensure immediate write
            val success = editor.commit()

            Log.d(TAG, "   - Commit result: $success")

            // Immediately verify
            val verifyEnabled = sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
            val verifyEmail = sharedPreferences.getString(KEY_SAVED_EMAIL, null)
            val verifyPassword = sharedPreferences.getString(KEY_SAVED_PASSWORD, null)

            Log.d(TAG, "   - Verification:")
            Log.d(TAG, "     * Enabled: $verifyEnabled")
            Log.d(TAG, "     * Email: $verifyEmail")
            Log.d(TAG, "     * Password saved: ${verifyPassword != null}")
            Log.d(TAG, "     * Password length: ${verifyPassword?.length ?: 0}")

            if (!verifyEnabled || verifyEmail == null || verifyPassword == null) {
                Log.e(TAG, "VERIFICATION FAILED! Data not saved correctly!")
            } else {
                Log.d(TAG, "Biometric credentials saved and verified successfully")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving biometric credentials: ${e.message}", e)
            e.printStackTrace()
        }

        Log.d(TAG, "═══════════════════════════════════════")
    }

    fun disableBiometricLogin() {
        Log.d(TAG, "DISABLING BIOMETRIC LOGIN")

        try {
            val editor = sharedPreferences.edit()
            editor.putBoolean(KEY_BIOMETRIC_ENABLED, false)
            editor.remove(KEY_SAVED_EMAIL)
            editor.remove(KEY_SAVED_PASSWORD)

            val success = editor.commit()
            Log.d(TAG, " - Commit result: $success")
            Log.d(TAG, "Biometric login disabled")

        } catch (e: Exception) {
            Log.e(TAG, "Error disabling biometric: ${e.message}")
        }
    }

    fun isBiometricEnabled(): Boolean {
        return try {
            val enabled = sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
            Log.d(TAG, "isBiometricEnabled: $enabled")


            if (enabled) {
                val email = sharedPreferences.getString(KEY_SAVED_EMAIL, null)
                val hasPassword = sharedPreferences.contains(KEY_SAVED_PASSWORD)
                Log.d(TAG, "   - Email: $email")
                Log.d(TAG, "   - Has password: $hasPassword")
            }

            enabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking biometric status: ${e.message}")
            false
        }
    }

    fun getSavedCredentials(): Pair<String, String>? {
        Log.d(TAG, "getSavedCredentials called")

        return try {
            val enabled = isBiometricEnabled()

            if (!enabled) {
                Log.d(TAG, "   - Biometric NOT enabled, returning null")
                return null
            }

            val email = sharedPreferences.getString(KEY_SAVED_EMAIL, null)
            val password = sharedPreferences.getString(KEY_SAVED_PASSWORD, null)

            Log.d(TAG, "   - Email: $email")
            Log.d(TAG, "   - Password exists: ${password != null}")
            Log.d(TAG, "   - Password length: ${password?.length ?: 0}")

            if (email != null && password != null) {
                Log.d(TAG, "Credentials retrieved successfully")
                Pair(email, password)
            } else {
                Log.e(TAG, "Biometric enabled but credentials missing!")
                Log.e(TAG, "   - Email is null: ${email == null}")
                Log.e(TAG, "   - Password is null: ${password == null}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving credentials: ${e.message}", e)
            e.printStackTrace()
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
        Log.d(TAG, "👆 showBiometricPrompt called")

        val executor = ContextCompat.getMainExecutor(context)

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Biometric authentication SUCCEEDED")
                    onSuccess(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e(TAG, "Biometric authentication ERROR: $errorCode - $errString")
                    onError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Biometric authentication FAILED")
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


    fun debugAllPreferences() {
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "DEBUG ALL PREFERENCES")

        // Check our main preferences
        Log.d(TAG, "Main ($PREFS_NAME):")
        sharedPreferences.all.forEach { (key, value) ->
            Log.d(TAG, "   - $key: ${if (key.contains("password")) "***" else value}")
        }

        // Check legacy preferences
        val legacy1 = context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
        Log.d(TAG, " Legacy (biometric_prefs):")
        legacy1.all.forEach { (key, value) ->
            Log.d(TAG, "   - $key: ${if (key.contains("password")) "***" else value}")
        }

        val legacy2 = context.getSharedPreferences("biometric_prefs_fallback", Context.MODE_PRIVATE)
        Log.d(TAG, "Legacy Fallback:")
        legacy2.all.forEach { (key, value) ->
            Log.d(TAG, "   - $key: ${if (key.contains("password")) "***" else value}")
        }

        Log.d(TAG, "═══════════════════════════════════════")
    }
}

enum class BiometricStatus {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED,
    UNSUPPORTED
}