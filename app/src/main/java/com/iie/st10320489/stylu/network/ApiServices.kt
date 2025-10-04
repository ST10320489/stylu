// ApiService.kt
package com.iie.st10320489.stylu.network

import android.content.Context
import android.util.Log
import com.iie.st10320489.stylu.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// Data classes
data class UserProfile(
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val email: String?,
    val language: String?,
    val temperatureUnit: String?,
    val defaultReminderTime: String?,
    val weatherSensitivity: String?,
    val notifyWeather: Boolean?,
    val notifyOutfitReminders: Boolean?
)

data class UpdateProfileRequest(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?,
    val email: String,
    val password: String? = null
)

data class SystemSettings(
    val language: String,
    val temperatureUnit: String,
    val defaultReminderTime: String,
    val weatherSensitivity: String,
    val notifyWeather: Boolean,
    val notifyOutfitReminders: Boolean
)

class ApiService(context: Context) {

    // Use 10.0.2.2 for Android emulator to access localhost
    private val baseUrl = "http://10.0.2.2:5038"
    private val authRepository = AuthRepository(context)

    companion object {
        private const val TAG = "ApiService"
    }

    // ===================== CONNECTION TEST =====================
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL("$baseUrl/api/test")
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

    // ===================== PROFILE ENDPOINTS =====================

    /**
     * Get current user profile
     */
    suspend fun getCurrentProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Settings/profile")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            Log.d(TAG, "GET Profile - URL: $url")
            Log.d(TAG, "GET Profile - Token: Bearer ${token.take(20)}...")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "GET Profile - Response Code: $responseCode")
            Log.d(TAG, "GET Profile - Response: $responseText")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = JSONObject(responseText)

                val profile = UserProfile(
                    firstName = jsonResponse.optString("firstName", null),
                    lastName = jsonResponse.optString("lastName", null),
                    phoneNumber = jsonResponse.optString("phoneNumber", null),
                    email = jsonResponse.optString("email", null),
                    language = jsonResponse.optString("language", null),
                    temperatureUnit = jsonResponse.optString("temperatureUnit", null),
                    defaultReminderTime = jsonResponse.optString("defaultReminderTime", null),
                    weatherSensitivity = jsonResponse.optString("weatherSensitivity", null),
                    notifyWeather = jsonResponse.optBooleanOrNull("notifyWeather"),
                    notifyOutfitReminders = jsonResponse.optBooleanOrNull("notifyOutfitReminders")
                )

                Result.success(profile)
            } else {
                val errorMessage = when (responseCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> "Authentication failed. Please login again."
                    HttpURLConnection.HTTP_FORBIDDEN -> "Access denied."
                    else -> {
                        try {
                            val errorJson = JSONObject(responseText)
                            errorJson.optString("error", "Failed to fetch profile")
                        } catch (e: Exception) {
                            "Failed to fetch profile: $responseCode"
                        }
                    }
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching profile", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Update user profile
     */
    suspend fun updateProfile(request: UpdateProfileRequest): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Settings/profile")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("firstName", request.firstName)
                put("lastName", request.lastName)
                put("phoneNumber", request.phoneNumber ?: "")
                put("email", request.email)
                request.password?.let { put("password", it) }
            }

            Log.d(TAG, "PUT Profile - URL: $url")
            Log.d(TAG, "PUT Profile - Request: $requestBody")

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "PUT Profile - Response Code: $responseCode")
            Log.d(TAG, "PUT Profile - Response: $responseText")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseJson = JSONObject(responseText)
                val message = responseJson.optString("message", "Profile updated successfully")
                Result.success(message)
            } else {
                val errorMessage = when (responseCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> "Authentication failed. Please login again."
                    HttpURLConnection.HTTP_FORBIDDEN -> "Access denied."
                    else -> {
                        try {
                            val errorJson = JSONObject(responseText)
                            errorJson.optString("error", "Failed to update profile")
                        } catch (e: Exception) {
                            "Failed to update profile: $responseCode"
                        }
                    }
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    // ===================== SYSTEM SETTINGS ENDPOINTS =====================

    /**
     * Get current system settings
     */
    suspend fun getCurrentSystemSettings(): Result<SystemSettings> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Settings/system")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            Log.d(TAG, "GET System Settings - URL: $url")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "GET System Settings - Response Code: $responseCode")
            Log.d(TAG, "GET System Settings - Response: $responseText")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = JSONObject(responseText)

                val settings = SystemSettings(
                    language = jsonResponse.optString("language", "en"),
                    temperatureUnit = jsonResponse.optString("temperatureUnit", "C"),
                    defaultReminderTime = jsonResponse.optString("defaultReminderTime", "07:00"),
                    weatherSensitivity = jsonResponse.optString("weatherSensitivity", "normal"),
                    notifyWeather = jsonResponse.optBoolean("notifyWeather", true),
                    notifyOutfitReminders = jsonResponse.optBoolean("notifyOutfitReminders", true)
                )

                Result.success(settings)
            } else {
                val errorMessage = when (responseCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> "Authentication failed. Please login again."
                    HttpURLConnection.HTTP_FORBIDDEN -> "Access denied."
                    else -> {
                        try {
                            val errorJson = JSONObject(responseText)
                            errorJson.optString("error", "Failed to fetch system settings")
                        } catch (e: Exception) {
                            "Failed to fetch system settings: $responseCode"
                        }
                    }
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching system settings", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Update system settings
     */
    suspend fun updateSystemSettings(settings: SystemSettings): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Settings/system")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("language", settings.language)
                put("temperatureUnit", settings.temperatureUnit)
                put("defaultReminderTime", settings.defaultReminderTime)
                put("weatherSensitivity", settings.weatherSensitivity)
                put("notifyWeather", settings.notifyWeather)
                put("notifyOutfitReminders", settings.notifyOutfitReminders)
            }

            Log.d(TAG, "PUT System Settings - URL: $url")
            Log.d(TAG, "PUT System Settings - Request: $requestBody")

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "PUT System Settings - Response Code: $responseCode")
            Log.d(TAG, "PUT System Settings - Response: $responseText")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseJson = JSONObject(responseText)
                val message = responseJson.optString("message", "System settings updated successfully")
                Result.success(message)
            } else {
                val errorMessage = when (responseCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> "Authentication failed. Please login again."
                    HttpURLConnection.HTTP_FORBIDDEN -> "Access denied."
                    else -> {
                        try {
                            val errorJson = JSONObject(responseText)
                            errorJson.optString("error", "Failed to update system settings")
                        } catch (e: Exception) {
                            "Failed to update system settings: $responseCode"
                        }
                    }
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating system settings", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}

// Extension function to handle nullable boolean from JSONObject
private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
    return if (has(key) && !isNull(key)) {
        optBoolean(key)
    } else {
        null
    }
}