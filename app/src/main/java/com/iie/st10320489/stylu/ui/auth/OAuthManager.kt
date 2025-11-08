package com.iie.st10320489.stylu.auth

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SimpleOAuthManager(context: Context) {

    private val supabaseAuth = DirectSupabaseAuth(context)

    suspend fun signInWithGoogle(activity: Activity): Result<String> = withContext(Dispatchers.IO) {
        try {
            val redirectUrl = "com.iie.st10320489.stylu://oauth"
            val oAuthUrlResult = supabaseAuth.getOAuthUrl("google", redirectUrl)

            if (oAuthUrlResult.isSuccess) {
                val oAuthUrl = oAuthUrlResult.getOrThrow()
                withContext(Dispatchers.Main) {
                    launchCustomTab(activity, oAuthUrl)
                }
                Result.failure(Exception("OAuth flow initiated - waiting for redirect"))
            } else {
                Result.failure(Exception("Failed to get OAuth URL"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun handleOAuthCallback(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("OAuthCallback", "Full URI: $uri")
            val fragment = uri.fragment ?: return@withContext Result.failure(Exception("No authentication data received"))

            val params = mutableMapOf<String, String>()
            fragment.split("&").forEach { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) params[parts[0]] = parts[1]
            }

            val error = params["error"] ?: params["error_description"]
            if (error != null) return@withContext Result.failure(Exception("OAuth error: $error"))

            val accessToken = params["access_token"] ?: return@withContext Result.failure(Exception("No access token received"))
            val refreshToken = params["refresh_token"]

            supabaseAuth.setSession(accessToken, refreshToken)
            Result.success(accessToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext supabaseAuth.signIn(email, password)
    }

    suspend fun signUpWithEmail(email: String, password: String, firstName: String, lastName: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext supabaseAuth.signUp(email, password, firstName, lastName)
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext supabaseAuth.signOut()
    }

    fun getCurrentAccessToken() = supabaseAuth.getCurrentAccessToken()
    fun isLoggedIn() = supabaseAuth.isLoggedIn()
    fun getCurrentUser() = supabaseAuth.getCurrentUser()

    private fun launchCustomTab(activity: Activity, url: String) {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, Uri.parse(url))
    }
}