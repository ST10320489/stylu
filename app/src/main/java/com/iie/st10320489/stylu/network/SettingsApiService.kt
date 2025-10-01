package com.iie.st10320489.stylu.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.apply
import kotlin.io.bufferedReader
import kotlin.io.readText
import kotlin.io.use
import kotlin.let

// Data classes for profile settings
data class UpdateProfileRequest(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val email: String,
    val password: String? = null
)

data class UserSettingsProfile(
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


data class UpdateSystemSettingsRequest(
    val language: String,
    val temperatureUnit: String, // "C" or "F"
    val defaultReminderTime: String, // Format: "HH:mm"
    val weatherSensitivity: String, // "low", "normal", "high"
    val notifyWeather: Boolean,
    val notifyOutfitReminders: Boolean
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?,
    val error: String?
)

class SettingsApiService {

    // Replace with your actual API base URL
    //private val API_BASE_URL = "http://localhost:5038"
    private val API_BASE_URL = "http://10.0.2.2:5038"



    suspend fun getCurrentProfile(accessToken: String): Result<UserSettingsProfile> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$API_BASE_URL/api/Settings/profile")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")

                // 🔑 ADD LOG HERE: token being used
                Log.d("SettingsApiService", "Using token: $accessToken")

                val responseCode = connection.responseCode

                val response = if (responseCode >= 400) {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } else {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }

                // 🔑 ADD LOG HERE: response code + response body
                Log.d("ApiService", "Profile response code: $responseCode")
                Log.d("ApiService", "Profile response: $response")
                Log.d("SettingsApiService", "GET URL: $url")
                Log.d(
                    "SettingsApiService",
                    "Request headers: Authorization=Bearer $accessToken, Content-Type=application/json"
                )
                Log.d("SettingsApiService", "Response code: $responseCode")
                Log.d("SettingsApiService", "Response body: $response")

                if (responseCode == 200) {
                    val responseJson = JSONObject(response)
                    val profile = parseUserProfile(responseJson)
                    Result.success(profile)
                } else {
                    val errorMessage = try {
                        val errorJson = JSONObject(response)
                        errorJson.optString("error", "Failed to fetch profile")
                    } catch (e: Exception) {
                        "Failed to fetch profile"
                    }
                    Result.failure(kotlin.Exception(errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }


    suspend fun updateProfile(accessToken: String, request: UpdateProfileRequest): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$API_BASE_URL/api/Settings/profile")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.doOutput = true

                val requestBody = JSONObject().apply {
                    put("firstName", request.firstName)
                    put("lastName", request.lastName)
                    put("phoneNumber", request.phoneNumber)
                    put("email", request.email)
                    request.password?.let { put("password", it) }
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
                    val message = responseJson.optString("message", "Profile updated successfully")
                    Result.success(message)
                } else {
                    val errorMessage = try {
                        val errorJson = JSONObject(response)
                        errorJson.optString("error", "Failed to update profile")
                    } catch (e: Exception) {
                        "Failed to update profile"
                    }
                    Result.failure(kotlin.Exception(errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getCurrentSystemSettings(accessToken: String): Result<UpdateSystemSettingsRequest> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$API_BASE_URL/api/Settings/system")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")

                val responseCode = connection.responseCode
                val response = if (responseCode >= 400) {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } else {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }

                if (responseCode == 200) {
                    val responseJson = JSONObject(response)
                    val settings = parseSystemSettings(responseJson)
                    Result.success(settings)
                } else {
                    val errorMessage = try {
                        val errorJson = JSONObject(response)
                        errorJson.optString("error", "Failed to fetch system settings")
                    } catch (e: Exception) {
                        "Failed to fetch system settings"
                    }
                    Result.failure(kotlin.Exception(errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun updateSystemSettings(accessToken: String, request: UpdateSystemSettingsRequest): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$API_BASE_URL/api/Settings/system")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.doOutput = true

                val requestBody = JSONObject().apply {
                    put("language", request.language)
                    put("temperatureUnit", request.temperatureUnit)
                    put("defaultReminderTime", request.defaultReminderTime)
                    put("weatherSensitivity", request.weatherSensitivity)
                    put("notifyWeather", request.notifyWeather)
                    put("notifyOutfitReminders", request.notifyOutfitReminders)
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
                    val message =
                        responseJson.optString("message", "System settings updated successfully")
                    Result.success(message)
                } else {
                    val errorMessage = try {
                        val errorJson = JSONObject(response)
                        errorJson.optString("error", "Failed to update system settings")
                    } catch (e: Exception) {
                        "Failed to update system settings"
                    }
                    Result.failure(kotlin.Exception(errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseUserProfile(json: JSONObject): UserSettingsProfile {
        return UserSettingsProfile(
            firstName = json.optString("firstName", null),
            lastName = json.optString("lastName", null),
            phoneNumber = json.optString("phoneNumber", null),
            email = json.optString("email", null),
            language = json.optString("language", null),
            temperatureUnit = json.optString("temperatureUnit", null),
            defaultReminderTime = json.optString("defaultReminderTime", null),
            weatherSensitivity = json.optString("weatherSensitivity", null),
            notifyWeather = json.optBooleanOrNull("notifyWeather"),
            notifyOutfitReminders = json.optBooleanOrNull("notifyOutfitReminders")
        )
    }


    private fun parseSystemSettings(json: JSONObject): UpdateSystemSettingsRequest {
        return UpdateSystemSettingsRequest(
            language = json.optString("language", "en"),
            temperatureUnit = json.optString("temperatureUnit", "C"),
            defaultReminderTime = json.optString("defaultReminderTime", "07:00"),
            weatherSensitivity = json.optString("weatherSensitivity", "normal"),
            notifyWeather = json.optBoolean("notifyWeather", true),
            notifyOutfitReminders = json.optBoolean("notifyOutfitReminders", true)
        )
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