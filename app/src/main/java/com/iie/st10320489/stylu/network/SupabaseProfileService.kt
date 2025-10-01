package com.iie.st10320489.stylu.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class SupabaseUserProfile(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val language: String?,
    val temperatureUnit: String?,
    val defaultReminderTime: String?,
    val weatherSensitivity: String?,
    val notifyWeather: Boolean?,
    val notifyOutfitReminders: Boolean?
)

object SupabaseProfileService {
    private const val TAG = "SupabaseProfileService"
    private const val SUPABASE_URL = DirectSupabaseAuth.SUPABASE_URL
    private const val SUPABASE_ANON_KEY = DirectSupabaseAuth.SUPABASE_ANON_KEY

    /**
     * Fetches the current user's profile from the user_profiles table
     */
    suspend fun getCurrentProfile(accessToken: String): Result<SupabaseUserProfile> = withContext(Dispatchers.IO) {
        try {
            // First, get the user ID from the token
            val userId = extractUserIdFromToken(accessToken)
            if (userId == null) {
                return@withContext Result.failure(Exception("Invalid token: could not extract user ID"))
            }

            Log.d(TAG, "Fetching profile for user ID: $userId")

            // Query the user_profiles table
            val url = URL("$SUPABASE_URL/rest/v1/user_profiles?id=eq.$userId&select=*")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")

            val responseCode = connection.responseCode
            val response = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "Response code: $responseCode")
            Log.d(TAG, "Response: $response")

            if (responseCode == 200) {
                val jsonArray = JSONArray(response)

                if (jsonArray.length() == 0) {
                    // Profile doesn't exist yet, create it with defaults
                    return@withContext createDefaultProfile(accessToken, userId)
                }

                val profileJson = jsonArray.getJSONObject(0)
                val profile = SupabaseUserProfile(
                    id = profileJson.getString("id"),
                    email = profileJson.optString("email"),
                    firstName = profileJson.optStringOrNull("first_name"),
                    lastName = profileJson.optStringOrNull("last_name"),
                    phoneNumber = profileJson.optStringOrNull("phone_number"),
                    language = profileJson.optStringOrNull("language") ?: "en",
                    temperatureUnit = profileJson.optStringOrNull("temperature_unit") ?: "C",
                    defaultReminderTime = profileJson.optStringOrNull("default_reminder_time") ?: "07:00",
                    weatherSensitivity = profileJson.optStringOrNull("weather_sensitivity") ?: "normal",
                    notifyWeather = profileJson.optBooleanOrNull("notify_weather") ?: true,
                    notifyOutfitReminders = profileJson.optBooleanOrNull("notify_outfit_reminders") ?: true
                )

                Result.success(profile)
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(response)
                    errorJson.optString("message", "Failed to fetch profile")
                } catch (e: Exception) {
                    "Failed to fetch profile: HTTP $responseCode"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching profile", e)
            Result.failure(e)
        }
    }

    /**
     * Updates the user's profile in the user_profiles table AND auth metadata
     */
    suspend fun updateProfile(
        accessToken: String,
        firstName: String?,
        lastName: String?,
        phoneNumber: String?,
        email: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userId = extractUserIdFromToken(accessToken)
            if (userId == null) {
                return@withContext Result.failure(Exception("Invalid token"))
            }

            // 1. Update user_profiles table
            val url = URL("$SUPABASE_URL/rest/v1/user_profiles?id=eq.$userId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PATCH"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=minimal")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                firstName?.let { put("first_name", it) }
                lastName?.let { put("last_name", it) }
                phoneNumber?.let { put("phone_number", it) }
                email?.let { put("email", it) }
                put("updated_at", "now()")
            }

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Update profile response code: $responseCode")

            if (responseCode in 200..299) {
                // 2. Update auth.users metadata
                if (firstName != null || lastName != null) {
                    updateAuthMetadata(accessToken, firstName, lastName)
                }
                Result.success("Profile updated successfully")
            } else {
                val response = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Update failed: $response")
                Result.failure(Exception("Failed to update profile: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile", e)
            Result.failure(e)
        }
    }

    /**
     * Updates user metadata in auth.users (for first_name and last_name)
     */
    private suspend fun updateAuthMetadata(
        accessToken: String,
        firstName: String?,
        lastName: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$SUPABASE_URL/auth/v1/user")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("data", JSONObject().apply {
                    firstName?.let { put("first_name", it) }
                    lastName?.let { put("last_name", it) }
                })
            }

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Update auth metadata response code: $responseCode")

            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                val response = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Auth metadata update failed: $response")
                Result.failure(Exception("Failed to update auth metadata"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating auth metadata", e)
            Result.failure(e)
        }
    }

    /**
     * Updates user password
     */
    suspend fun updatePassword(
        accessToken: String,
        newPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (newPassword.length < 6) {
                return@withContext Result.failure(Exception("Password must be at least 6 characters"))
            }

            val url = URL("$SUPABASE_URL/auth/v1/user")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("password", newPassword)
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

            Log.d(TAG, "Update password response code: $responseCode")

            if (responseCode in 200..299) {
                Result.success("Password updated successfully")
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(response)
                    errorJson.optString("error_description", "Failed to update password")
                } catch (e: Exception) {
                    "Failed to update password: HTTP $responseCode"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating password", e)
            Result.failure(e)
        }
    }

    /**
     * Updates system settings in the user_profiles table
     */
    suspend fun updateSystemSettings(
        accessToken: String,
        language: String,
        temperatureUnit: String,
        defaultReminderTime: String,
        weatherSensitivity: String,
        notifyWeather: Boolean,
        notifyOutfitReminders: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userId = extractUserIdFromToken(accessToken)
            if (userId == null) {
                return@withContext Result.failure(Exception("Invalid token"))
            }

            val url = URL("$SUPABASE_URL/rest/v1/user_profiles?id=eq.$userId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PATCH"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=minimal")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("language", language)
                put("temperature_unit", temperatureUnit)
                put("default_reminder_time", defaultReminderTime)
                put("weather_sensitivity", weatherSensitivity)
                put("notify_weather", notifyWeather)
                put("notify_outfit_reminders", notifyOutfitReminders)
                put("updated_at", "now()")
            }

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Update settings response code: $responseCode")

            if (responseCode in 200..299) {
                Result.success("Settings updated successfully")
            } else {
                val response = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Settings update failed: $response")
                Result.failure(Exception("Failed to update settings: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating settings", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a default profile for a new user
     */
    private suspend fun createDefaultProfile(accessToken: String, userId: String): Result<SupabaseUserProfile> {
        try {
            // Get user email from auth
            val userEmail = DirectSupabaseAuth.getCurrentUser()?.email ?: ""
            val userMetadata = DirectSupabaseAuth.getCurrentUser()?.userMetadata

            val url = URL("$SUPABASE_URL/rest/v1/user_profiles")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=representation")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("id", userId)
                put("email", userEmail)
                put("first_name", userMetadata?.firstName ?: "")
                put("last_name", userMetadata?.lastName ?: "")
                put("language", "en")
                put("temperature_unit", "C")
                put("default_reminder_time", "07:00")
                put("weather_sensitivity", "normal")
                put("notify_weather", true)
                put("notify_outfit_reminders", true)
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

            Log.d(TAG, "Create profile response code: $responseCode")
            Log.d(TAG, "Create profile response: $response")

            return if (responseCode in 200..299) {  // ← ADD return HERE
                val jsonArray = JSONArray(response)
                val profileJson = jsonArray.getJSONObject(0)

                val profile = SupabaseUserProfile(
                    id = profileJson.getString("id"),
                    email = profileJson.getString("email"),
                    firstName = profileJson.optStringOrNull("first_name"),
                    lastName = profileJson.optStringOrNull("last_name"),
                    phoneNumber = null,
                    language = "en",
                    temperatureUnit = "C",
                    defaultReminderTime = "07:00",
                    weatherSensitivity = "normal",
                    notifyWeather = true,
                    notifyOutfitReminders = true
                )

                Result.success(profile)
            } else {
                Result.failure(Exception("Failed to create profile: HTTP $responseCode - $response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating default profile", e)
            return Result.failure(e)  // ← ADD return HERE too
        }
    }
    /**
     * Extracts the user ID from a JWT token
     */
    private fun extractUserIdFromToken(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val payload = String(
                android.util.Base64.decode(
                    parts[1],
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
                )
            )
            val json = JSONObject(payload)
            json.optString("sub")
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting user ID from token", e)
            null
        }
    }
}

// Extension functions - must be outside the object
private fun JSONObject.optStringOrNull(key: String): String? {
    return if (has(key) && !isNull(key)) {
        val value = optString(key)
        if (value.isEmpty()) null else value
    } else {
        null
    }
}

private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
    return if (has(key) && !isNull(key)) {
        optBoolean(key)
    } else {
        null
    }
}