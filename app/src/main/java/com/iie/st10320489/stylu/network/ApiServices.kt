// ApiService.kt
package com.iie.st10320489.stylu.network

import com.iie.st10320489.stylu.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UserProfile(
    val success: Boolean,
    val userId: String?,
    val email: String?,
    val role: String?,
    val userMetadata: String?
)

data class UserData(
    val success: Boolean,
    val userId: String?,
    val email: String?,
    val customData: String?,
    val timestamp: String?
)

class ApiService {
    private val baseUrl = "http://localhost:5038"
    private val authRepository = AuthRepository(
        context = TODO()
    )

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL("$baseUrl/user/test")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Result.success("API connection successful: $response")
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(Exception("API not reachable: $responseCode - $errorResponse"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.message}"))
        }
    }

    suspend fun getUserProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/user/profile")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = JSONObject(responseText)

                val userProfile = UserProfile(
                    success = jsonResponse.optBoolean("success", false),
                    userId = jsonResponse.optString("userId"),
                    email = jsonResponse.optString("email"),
                    role = jsonResponse.optString("role"),
                    userMetadata = jsonResponse.optString("userMetadata")
                )

                Result.success(userProfile)
            } else {
                val errorMessage = when (responseCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        "Authentication failed. Please login again."
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        "Access denied."
                    }
                    else -> {
                        try {
                            val errorJson = JSONObject(responseText)
                            errorJson.optString("message", "API call failed: $responseCode")
                        } catch (e: Exception) {
                            "API call failed: $responseCode - $responseText"
                        }
                    }
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getUserData(): Result<UserData> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/user/data")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = JSONObject(responseText)

                val userData = UserData(
                    success = jsonResponse.optBoolean("success", false),
                    userId = jsonResponse.optString("userId"),
                    email = jsonResponse.optString("email"),
                    customData = jsonResponse.optString("customData"),
                    timestamp = jsonResponse.optString("timestamp")
                )

                Result.success(userData)
            } else {
                val errorMessage = when (responseCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        "Authentication failed. Please login again."
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        "Access denied."
                    }
                    else -> {
                        try {
                            val errorJson = JSONObject(responseText)
                            errorJson.optString("message", "API call failed: $responseCode")
                        } catch (e: Exception) {
                            "API call failed: $responseCode - $responseText"
                        }
                    }
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}