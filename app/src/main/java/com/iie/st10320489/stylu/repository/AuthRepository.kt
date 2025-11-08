package com.iie.st10320489.stylu.repository

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import com.iie.st10320489.stylu.auth.SimpleOAuthManager
import com.iie.st10320489.stylu.ui.auth.BiometricAuthManager
import com.iie.st10320489.stylu.ui.auth.BiometricStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(private val context: Context) {
    private val oAuthManager = SimpleOAuthManager(context)
    private val biometricManager = BiometricAuthManager(context)

    private val TAG = "AuthRepository"

    suspend fun signUp(email: String, password: String, firstName: String, lastName: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext oAuthManager.signUpWithEmail(email, password, firstName, lastName)
    }

    suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext oAuthManager.signInWithEmail(email, password)
    }

    suspend fun signInWithGoogle(activity: Activity): Result<String> = withContext(Dispatchers.IO) {
        return@withContext oAuthManager.signInWithGoogle(activity)
    }

    suspend fun handleOAuthCallback(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        return@withContext oAuthManager.handleOAuthCallback(uri)
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        // Disable biometric login on sign out for security
        try {
            biometricManager.disableBiometricLogin()
            Log.d(TAG, "Biometric login disabled on sign out")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling biometric on sign out: ${e.message}")
        }
        return@withContext oAuthManager.signOut()
    }

    // OAuth methods
    fun getCurrentAccessToken() = oAuthManager.getCurrentAccessToken()
    fun isLoggedIn() = oAuthManager.isLoggedIn()
    fun getCurrentUserEmail() = oAuthManager.getCurrentUser()?.email
    fun getCurrentUser() = oAuthManager.getCurrentUser()

    // Biometric methods
    fun getBiometricManager() = biometricManager
    fun isBiometricAvailable(): BiometricStatus = biometricManager.isBiometricAvailable()
    fun isBiometricEnabled(): Boolean = biometricManager.isBiometricEnabled()

    fun enableBiometric(email: String, password: String) {
        biometricManager.enableBiometricLogin(email, password)
        Log.d(TAG, "Biometric enabled for: $email")
    }

    fun disableBiometric() {
        biometricManager.disableBiometricLogin()
        Log.d(TAG, "Biometric disabled")
    }

    fun getBiometricCredentials(): Pair<String, String>? = biometricManager.getSavedCredentials()
}