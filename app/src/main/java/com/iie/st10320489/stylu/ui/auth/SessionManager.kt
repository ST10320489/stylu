package com.iie.st10320489.stylu.auth

import android.content.Context
import android.content.Intent
import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import com.iie.st10320489.stylu.service.MyFirebaseMessagingService
import com.iie.st10320489.stylu.ui.auth.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SessionManager(private val context: Context) {

    private val supabaseAuth = DirectSupabaseAuth(context)

    /**
     * Redirect user to login screen
     */
    fun redirectToLogin() {
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }

    /**
     * Logout user and unregister FCM token
     */
    suspend fun logout() {
        // Unregister FCM token first
        MyFirebaseMessagingService.unregisterToken(context)

        // Then sign out from Supabase (this clears session)
        supabaseAuth.signOut()

        // Redirect to login
        redirectToLogin()
    }

    /**
     * Clear session immediately (non-suspending version for crash handling)
     */
    fun clearSession() {
        // Launch logout in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MyFirebaseMessagingService.unregisterToken(context)
                supabaseAuth.signOut()
            } catch (e: Exception) {
                // Ignore errors during emergency clear
            }
        }

        // Immediately redirect to login (don't wait for logout)
        redirectToLogin()
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated() = supabaseAuth.isLoggedIn()

    /**
     * Get current user
     */
    fun getCurrentUser() = supabaseAuth.getCurrentUser()

    /**
     * Get current access token
     */
    fun getCurrentAccessToken() = supabaseAuth.getCurrentAccessToken()

    /**
     * Get current user email
     */
    fun getCurrentUserEmail() = supabaseAuth.getCurrentUserEmail()

    /**
     * Get current user ID
     */
    fun getCurrentUserId() = supabaseAuth.getCurrentUserId()
}