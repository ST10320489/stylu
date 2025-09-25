package com.iie.st10320489.stylu.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
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
    val emailConfirmedAt: String?
)

object DirectSupabaseAuth {
    private const val SUPABASE_URL = "https://fkmhmtioehokrukqwano.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZrbWhtdGlvZWhva3J1a3F3YW5vIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgyMDAzNDIsImV4cCI6MjA3Mzc3NjM0Mn0.wg5fNm5_M8CRN3uzHnqvaxovIUDLCUWDcSiFJ14WqNE"

    private var currentAccessToken: String? = null
    private var currentRefreshToken: String? = null
    private var currentUser: User? = null

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
                    currentUser = User(
                        id = userJson.getString("id"),
                        email = userJson.getString("email"),
                        emailConfirmedAt = userJson.optString("email_confirmed_at", null)
                    )
                }

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

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = currentAccessToken ?: return@withContext Result.success(Unit)

            val url = URL("$SUPABASE_URL/auth/v1/logout")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")

            currentAccessToken = null
            currentRefreshToken = null
            currentUser = null

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentAccessToken(): String? = currentAccessToken

    fun isLoggedIn(): Boolean = currentAccessToken != null && currentUser != null

    fun getCurrentUserEmail(): String? = currentUser?.email
}