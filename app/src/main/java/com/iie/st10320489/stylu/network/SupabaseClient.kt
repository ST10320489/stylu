package com.iie.st10320489.stylu.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AuthResponse(
    val accessToken: String?,
    val refreshToken: String?,
    val user: User?
)

data class User(
    val id: String,
    val email: String,
    val emailConfirmedAt: String?,
    val userMetadata: UserMetadata?
)

data class UserMetadata(
    val firstName: String?,
    val lastName: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val provider: String?
)

class DirectSupabaseAuth(private val context: Context) {

    companion object {
        const val SUPABASE_URL = "https://fkmhmtioehokrukqwano.supabase.co"
        const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZrbWhtdGlvZWhva3J1a3F3YW5vIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgyMDAzNDIsImV4cCI6MjA3Mzc3NjM0Mn0.wg5fNm5_M8CRN3uzHnqvaxovIUDLCUWDcSiFJ14WqNE"

        private const val PREFS_NAME = "stylu_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME = "last_name"
        private const val TAG = "DirectSupabaseAuth"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var currentAccessToken: String? = null
    private var currentRefreshToken: String? = null
    private var currentUser: User? = null

    init {
        // Load saved session on initialization
        loadSavedSession()
    }

    /**
     * Load saved session from SharedPreferences
     */
    private fun loadSavedSession() {
        currentAccessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        currentRefreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)

        val userId = prefs.getString(KEY_USER_ID, null)
        val userEmail = prefs.getString(KEY_USER_EMAIL, null)
        val firstName = prefs.getString(KEY_FIRST_NAME, null)
        val lastName = prefs.getString(KEY_LAST_NAME, null)

        if (userId != null && userEmail != null) {
            currentUser = User(
                id = userId,
                email = userEmail,
                emailConfirmedAt = null,
                userMetadata = UserMetadata(
                    firstName = firstName,
                    lastName = lastName,
                    fullName = "$firstName $lastName",
                    avatarUrl = null,
                    provider = null
                )
            )
            android.util.Log.d(TAG, "Session restored for user: $userEmail")
        }
    }

    /**
     * Save session to SharedPreferences
     */
    private fun saveSession(accessToken: String, refreshToken: String?, user: User?) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            if (user != null) {
                putString(KEY_USER_ID, user.id)
                putString(KEY_USER_EMAIL, user.email)
                putString(KEY_FIRST_NAME, user.userMetadata?.firstName)
                putString(KEY_LAST_NAME, user.userMetadata?.lastName)
            }
            apply()
        }
        android.util.Log.d(TAG, "Session saved for user: ${user?.email}")
    }

    /**
     * Clear session from SharedPreferences
     */
    private fun clearSession() {
        prefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USER_EMAIL)
            remove(KEY_FIRST_NAME)
            remove(KEY_LAST_NAME)
            apply()
        }
        currentAccessToken = null
        currentRefreshToken = null
        currentUser = null
        android.util.Log.d(TAG, "Session cleared")
    }

    suspend fun signUp(email: String, password: String, firstName: String, lastName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$SUPABASE_URL/auth/v1/signup")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("data", JSONObject().apply {
                    put("first_name", firstName)
                    put("last_name", lastName)
                })
            }

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            val response = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            if (responseCode == 200) {
                Result.success("Sign up successful! Check your email for verification.")
            } else {
                val errorJson = JSONObject(response)
                val errorMessage = errorJson.optString("error_description", "Sign up failed")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$SUPABASE_URL/auth/v1/token?grant_type=password")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            val response = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            if (responseCode == 200) {
                val responseJson = JSONObject(response)
                currentAccessToken = responseJson.optString("access_token")
                currentRefreshToken = responseJson.optString("refresh_token")

                val userJson = responseJson.optJSONObject("user")
                if (userJson != null) {
                    val userMetadataJson = userJson.optJSONObject("user_metadata")
                    val userMetadata = if (userMetadataJson != null) {
                        UserMetadata(
                            firstName = userMetadataJson.optString("first_name"),
                            lastName = userMetadataJson.optString("last_name"),
                            fullName = userMetadataJson.optString("full_name"),
                            avatarUrl = userMetadataJson.optString("avatar_url"),
                            provider = userMetadataJson.optString("provider")
                        )
                    } else null

                    currentUser = User(
                        id = userJson.getString("id"),
                        email = userJson.getString("email"),
                        emailConfirmedAt = userJson.optString("email_confirmed_at", null),
                        userMetadata = userMetadata
                    )
                }

                // Save session persistently
                saveSession(currentAccessToken!!, currentRefreshToken, currentUser)

                android.util.Log.d(TAG, "User $email logged in successfully")

                Result.success(currentAccessToken ?: "")
            } else {
                val errorJson = JSONObject(response)
                val errorMessage = errorJson.optString("error_description", "Login failed")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setSession(accessToken: String, refreshToken: String?) {
        currentAccessToken = accessToken
        currentRefreshToken = refreshToken

        try {
            val parts = accessToken.split(".")
            if (parts.size == 3) {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
                val json = JSONObject(payload)
                val email = json.optString("email")
                val userId = json.optString("sub")
                val userMetadataJson = json.optJSONObject("user_metadata")
                val userMetadata = if (userMetadataJson != null) {
                    UserMetadata(
                        firstName = userMetadataJson.optString("first_name"),
                        lastName = userMetadataJson.optString("last_name"),
                        fullName = userMetadataJson.optString("full_name"),
                        avatarUrl = userMetadataJson.optString("avatar_url"),
                        provider = userMetadataJson.optString("provider")
                    )
                } else null

                currentUser = User(
                    id = userId,
                    email = email,
                    emailConfirmedAt = json.optString("email_confirmed_at", null),
                    userMetadata = userMetadata
                )

                // Save session persistently
                saveSession(accessToken, refreshToken, currentUser)

                android.util.Log.d(TAG, "OAuth session set for user: $email")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to parse JWT", e)
        }
    }

    suspend fun getOAuthUrl(provider: String, redirectUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encodedRedirect = java.net.URLEncoder.encode(redirectUrl, "UTF-8")
            val url = "$SUPABASE_URL/auth/v1/authorize?provider=$provider&redirect_to=$encodedRedirect"
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exchangeCodeForSession(code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$SUPABASE_URL/auth/v1/token?grant_type=authorization_code")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("code", code)
            }

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            val response = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            if (responseCode == 200) {
                val responseJson = JSONObject(response)
                currentAccessToken = responseJson.optString("access_token")
                currentRefreshToken = responseJson.optString("refresh_token")

                val userJson = responseJson.optJSONObject("user")
                if (userJson != null) {
                    val userMetadataJson = userJson.optJSONObject("user_metadata")
                    val userMetadata = if (userMetadataJson != null) {
                        UserMetadata(
                            firstName = userMetadataJson.optString("first_name"),
                            lastName = userMetadataJson.optString("last_name"),
                            fullName = userMetadataJson.optString("full_name"),
                            avatarUrl = userMetadataJson.optString("avatar_url"),
                            provider = userMetadataJson.optString("provider")
                        )
                    } else null

                    currentUser = User(
                        id = userJson.getString("id"),
                        email = userJson.getString("email"),
                        emailConfirmedAt = userJson.optString("email_confirmed_at", null),
                        userMetadata = userMetadata
                    )
                }

                // Save session persistently
                saveSession(currentAccessToken!!, currentRefreshToken, currentUser)

                Result.success(currentAccessToken ?: "")
            } else {
                val errorJson = JSONObject(response)
                val errorMessage = errorJson.optString("error_description", "OAuth exchange failed")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = currentAccessToken ?: return@withContext Result.success(Unit)

            val url = URL("$SUPABASE_URL/auth/v1/logout")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")

            // Clear session
            clearSession()

            Result.success(Unit)
        } catch (e: Exception) {
            // Even if API call fails, clear local session
            clearSession()
            Result.failure(e)
        }
    }

    fun getCurrentAccessToken(): String? = currentAccessToken

    fun isLoggedIn(): Boolean = currentAccessToken != null && currentUser != null

    fun getCurrentUserEmail(): String? = currentUser?.email

    fun getCurrentUser(): User? = currentUser

    fun getCurrentUserId(): String? = currentUser?.id
}