package com.iie.st10320489.stylu.repository

import android.content.Context
import com.iie.st10320489.stylu.auth.SessionManager
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.network.SystemSettings
import com.iie.st10320489.stylu.network.UpdateProfileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileRepository(private val context: Context) {

    private val sessionManager = SessionManager(context)
    private val apiService = ApiService(context)

    /**
     * Get access token helper
     */
    private fun getAccessToken(): String? {
        return sessionManager.getCurrentAccessToken()
    }

    /**
     * Get current user profile
     */
    suspend fun getCurrentProfile() = withContext(Dispatchers.IO) {
        apiService.getCurrentProfile()
    }

    /**
     * Update user profile
     */
    suspend fun updateProfile(
        firstName: String,
        lastName: String,
        phoneNumber: String?,
        email: String,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        val request = UpdateProfileRequest(
            firstName = firstName,
            lastName = lastName,
            phoneNumber = phoneNumber,
            email = email,
            password = password
        )
        apiService.updateProfile(request)
    }

    /**
     * Get system settings
     */
    suspend fun getSystemSettings() = withContext(Dispatchers.IO) {
        apiService.getCurrentSystemSettings()
    }

    /**
     * Update system settings
     */
    suspend fun updateSystemSettings(settings: SystemSettings) = withContext(Dispatchers.IO) {
        apiService.updateSystemSettings(settings)
    }

}