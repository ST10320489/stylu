package com.iie.st10320489.stylu.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SimpleOAuthManager(private val context: Context) {

    private val supabaseAuth = DirectSupabaseAuth

    suspend fun signInWithGoogle(activity: Activity): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Get OAuth URL from Supabase
            val redirectUrl = "com.iie.st10320489.stylu://oauth"
            val oAuthUrlResult = supabaseAuth.getOAuthUrl("google", redirectUrl)

            if (oAuthUrlResult.isSuccess) {
                val oAuthUrl = oAuthUrlResult.getOrThrow()

                // Launch Custom Tab with OAuth URL
                withContext(Dispatchers.Main) {
                    launchCustomTab(activity, oAuthUrl)
                }

                // Return a pending result - actual result will be handled in the redirect
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
            android.util.Log.d("OAuthCallback", "Fragment: ${uri.fragment}")

            // Supabase returns tokens in the fragment, not query params
            val fragment = uri.fragment

            if (fragment == null) {
                android.util.Log.e("OAuthCallback", "No fragment found in URI")
                return@withContext Result.failure(Exception("No authentication data received"))
            }

            // Parse fragment into key-value pairs
            val params = mutableMapOf<String, String>()
            fragment.split("&").forEach { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    params[parts[0]] = parts[1]
                }
            }

            android.util.Log.d("OAuthCallback", "Parsed params: $params")

            // Check for error
            val error = params["error"] ?: params["error_description"]
            if (error != null) {
                android.util.Log.e("OAuthCallback", "OAuth error: $error")
                return@withContext Result.failure(Exception("OAuth error: $error"))
            }

            // Get tokens directly from fragment (no code exchange needed)
            val accessToken = params["access_token"]
            val refreshToken = params["refresh_token"]

            if (accessToken == null) {
                android.util.Log.e("OAuthCallback", "No access token in fragment")
                return@withContext Result.failure(Exception("No access token received"))
            }

            android.util.Log.d("OAuthCallback", "Successfully extracted access token")

            // Store the tokens in DirectSupabaseAuth
            // We need to add a method to set these tokens
            supabaseAuth.setSession(accessToken, refreshToken)

            Result.success(accessToken)
        } catch (e: Exception) {
            android.util.Log.e("OAuthCallback", "Exception in handleOAuthCallback", e)
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
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        intent.launchUrl(activity, Uri.parse(url))
    }
}