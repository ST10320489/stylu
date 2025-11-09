// ApiService.kt - UPDATED VERSION
// Changes made:
// 1. Increased all connectTimeout to 60000ms (60 seconds) for Render cold starts
// 2. Increased all readTimeout to 30000ms (30 seconds)
// 3. Added specific error handling for SocketTimeoutException and ConnectException
// 4. Added helpful error messages for users

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

    private val baseUrl = "https://stylu-api-x69c.onrender.com"

    private val authRepository = AuthRepository(context)

    companion object {
        private const val TAG = "ApiService"
        // Increased timeouts for Render free tier cold starts
        private const val CONNECT_TIMEOUT = 60000  // 60 seconds
        private const val READ_TIMEOUT = 30000     // 30 seconds
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL("$baseUrl/api/test")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Result.success("API connection successful: $response")
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(Exception("API not reachable: $responseCode - $errorResponse"))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout connecting to API", e)
            Result.failure(Exception("Server is starting up (Render free tier). This can take up to 60 seconds on first request. Please try again."))
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Cannot connect to API", e)
            Result.failure(Exception("Cannot connect to server. Please check your internet connection."))
        } catch (e: Exception) {
            Log.e(TAG, "API connection error", e)
            Result.failure(Exception("Connection failed: ${e.message}"))
        }
    }

    suspend fun getCurrentProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Settings/profile")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

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
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching profile", e)
            Result.failure(Exception("Request timed out. Server may be starting up. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching profile", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun updateProfile(request: UpdateProfileRequest): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Settings/profile")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
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
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout updating profile", e)
            Result.failure(Exception("Request timed out. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getCurrentSystemSettings(): Result<SystemSettings> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Settings/system")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

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
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching system settings", e)
            Result.failure(Exception("Request timed out. Server may be starting up. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching system settings", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun updateSystemSettings(settings: SystemSettings): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Settings/system")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
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
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout updating system settings", e)
            Result.failure(Exception("Request timed out. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating system settings", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    data class OutfitDetail(
        val outfitId: Int,
        val userId: String,
        val name: String,
        val category: String?,
        val schedule: String?,
        val items: List<OutfitItemDetail>,
        val createdAt: String
    )

    data class OutfitItemDetail(
        val itemId: Int,
        val name: String?,
        val imageUrl: String,
        val colour: String?,
        val subcategory: String,
        val layoutData: ItemLayoutData? = null
    )

    data class ItemLayoutData(
        val x: Float,
        val y: Float,
        val scale: Float,
        val width: Int,
        val height: Int
    )

    data class CreateOutfitRequest(
        val name: String,
        val category: String?,
        val itemIds: List<Int>
    )

    data class CreateOutfitWithLayoutRequest(
        val name: String,
        val category: String?,
        val items: List<String>
    )

    data class OutfitResponse(
        val outfitId: Int,
        val name: String,
        val category: String?,
        val createdAt: String
    )

    suspend fun createOutfit(request: CreateOutfitRequest): Result<OutfitResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Outfit")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("name", request.name)
                put("category", request.category ?: "")
                put("itemIds", org.json.JSONArray(request.itemIds))
            }

            Log.d(TAG, "POST Create Outfit - URL: $url")
            Log.d(TAG, "POST Create Outfit - Request: $requestBody")

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

            Log.d(TAG, "POST Create Outfit - Response Code: $responseCode")
            Log.d(TAG, "POST Create Outfit - Response: $responseText")

            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = JSONObject(responseText)
                val dataObject = jsonResponse.getJSONObject("data")

                val outfit = OutfitResponse(
                    outfitId = jsonResponse.getInt("outfitId"),
                    name = dataObject.getString("outfit_name"),
                    category = request.category,
                    createdAt = ""
                )

                Result.success(outfit)
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(responseText)
                    errorJson.optString("error", "Failed to create outfit")
                } catch (e: Exception) {
                    "Failed to create outfit: $responseCode"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout creating outfit", e)
            Result.failure(Exception("Request timed out. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error creating outfit", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun createOutfitWithLayout(request: CreateOutfitWithLayoutRequest): Result<OutfitResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Outfit")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doOutput = true

            val itemsArray = org.json.JSONArray()
            request.items.forEach { layoutJson ->
                itemsArray.put(JSONObject(layoutJson))
            }

            val requestBody = JSONObject().apply {
                put("name", request.name)
                put("category", request.category ?: "")
                put("items", itemsArray)
            }

            Log.d(TAG, "POST Create Outfit With Layout - URL: $url")
            Log.d(TAG, "POST Create Outfit With Layout - Request: $requestBody")

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

            Log.d(TAG, "POST Create Outfit With Layout - Response Code: $responseCode")
            Log.d(TAG, "POST Create Outfit With Layout - Response: $responseText")

            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = JSONObject(responseText)
                val dataObject = jsonResponse.getJSONObject("data")

                val outfit = OutfitResponse(
                    outfitId = jsonResponse.getInt("outfitId"),
                    name = dataObject.getString("outfit_name"),
                    category = request.category,
                    createdAt = ""
                )

                Result.success(outfit)
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(responseText)
                    errorJson.optString("error", "Failed to create outfit")
                } catch (e: Exception) {
                    "Failed to create outfit: $responseCode"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout creating outfit with layout", e)
            Result.failure(Exception("Request timed out. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error creating outfit with layout", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getUserOutfits(): Result<List<OutfitDetail>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Outfit")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            Log.d(TAG, "GET User Outfits - URL: $url")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "GET User Outfits - Response Code: $responseCode")
            Log.d(TAG, "GET User Outfits - Response: $responseText")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val outfits = parseOutfitsResponse(responseText)
                Result.success(outfits)
            } else {
                val errorMessage = when (responseCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> "Authentication failed. Please login again."
                    HttpURLConnection.HTTP_FORBIDDEN -> "Access denied."
                    else -> {
                        try {
                            val errorJson = JSONObject(responseText)
                            errorJson.optString("error", "Failed to fetch outfits")
                        } catch (e: Exception) {
                            "Failed to fetch outfits: $responseCode"
                        }
                    }
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching outfits", e)
            Result.failure(Exception("Request timed out. Server may be starting up. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching outfits", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getOutfitsByCategory(category: String): Result<List<OutfitDetail>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Outfit/category/$category")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            Log.d(TAG, "GET Outfits by Category - URL: $url")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "GET Outfits by Category - Response Code: $responseCode")
            Log.d(TAG, "GET Outfits by Category - Response: $responseText")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val outfits = parseOutfitsResponse(responseText)
                Result.success(outfits)
            } else {
                Result.failure(Exception("Failed to fetch outfits: $responseCode"))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching outfits by category", e)
            Result.failure(Exception("Request timed out. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching outfits by category", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun deleteOutfit(outfitId: Int): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authRepository.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available. Please login again."))

            val url = URL("$baseUrl/api/Outfit/$outfitId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            Log.d(TAG, "DELETE Outfit - URL: $url")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() } ?: "Outfit deleted successfully"
            }

            Log.d(TAG, "DELETE Outfit - Response Code: $responseCode")
            Log.d(TAG, "DELETE Outfit - Response: $responseText")

            if (responseCode in 200..299) {
                Result.success("Outfit deleted successfully")
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(responseText)
                    errorJson.optString("error", "Failed to delete outfit")
                } catch (e: Exception) {
                    "Failed to delete outfit: $responseCode"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout deleting outfit", e)
            Result.failure(Exception("Request timed out. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting outfit", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    private fun parseOutfitsResponse(jsonString: String): List<OutfitDetail> {
        val outfits = mutableListOf<OutfitDetail>()

        try {
            val jsonArray = org.json.JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val outfitJson = jsonArray.getJSONObject(i)

                val items = mutableListOf<OutfitItemDetail>()

                if (outfitJson.has("outfit_item")) {
                    val outfitItemArray = outfitJson.getJSONArray("outfit_item")

                    for (j in 0 until outfitItemArray.length()) {
                        val outfitItemJson = outfitItemArray.getJSONObject(j)
                        val itemJson = outfitItemJson.getJSONObject("item")

                        val layoutData = if (outfitItemJson.has("layout_data") && !outfitItemJson.isNull("layout_data")) {
                            val layoutJson = outfitItemJson.getJSONObject("layout_data")
                            ItemLayoutData(
                                x = layoutJson.getDouble("x").toFloat(),
                                y = layoutJson.getDouble("y").toFloat(),
                                scale = layoutJson.getDouble("scale").toFloat(),
                                width = layoutJson.getInt("width"),
                                height = layoutJson.getInt("height")
                            )
                        } else null

                        items.add(
                            OutfitItemDetail(
                                itemId = itemJson.getInt("item_id"),
                                name = itemJson.optString("name", null),
                                imageUrl = itemJson.getString("image_url"),
                                colour = itemJson.optString("colour", null),
                                subcategory = itemJson.optJSONObject("sub_category")
                                    ?.optString("name", "Unknown") ?: "Unknown",
                                layoutData = layoutData
                            )
                        )
                    }
                }

                outfits.add(
                    OutfitDetail(
                        outfitId = outfitJson.getInt("outfit_id"),
                        userId = outfitJson.getString("user_id"),
                        name = outfitJson.getString("outfit_name"),
                        category = outfitJson.optString("schedule", null),
                        schedule = outfitJson.optString("schedule", null),
                        items = items,
                        createdAt = ""
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing outfits response", e)
        }

        return outfits
    }
}

private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
    return if (has(key) && !isNull(key)) {
        optBoolean(key)
    } else {
        null
    }
}