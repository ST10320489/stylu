package com.iie.st10320489.stylu.repository

import android.content.Context
import android.util.Log
import com.iie.st10320489.stylu.data.models.calendar.CalendarEvent
import com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit
import com.iie.st10320489.stylu.data.models.calendar.WeatherInfo
import com.iie.st10320489.stylu.data.models.calendar.OutfitInfo
import com.iie.st10320489.stylu.data.models.calendar.OutfitItemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarRepository(private val context: Context) {

    private val baseUrl = "https://stylu-api-x69c.onrender.com"
    private val tokenManager = TokenManager(context)

    companion object {
        private const val TAG = "CalendarRepository"
        private const val CONNECT_TIMEOUT = 60000
        private const val READ_TIMEOUT = 30000
    }

    private suspend fun getAccessToken(): String {
        val result = tokenManager.getValidAccessToken()
        return result.getOrElse {
            throw Exception("Authentication failed: ${it.message}")
        }
    }

    suspend fun scheduleOutfit(
        outfitId: Int,
        date: Date,
        eventName: String? = null,
        notes: String? = null
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
            Log.d(TAG, "Scheduling outfit with token: ${token.take(20)}...")

            val url = URL("$baseUrl/api/Calendar/schedule")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doOutput = true

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val requestBody = JSONObject().apply {
                put("outfitId", outfitId)
                put("eventDate", dateFormat.format(date))
                if (!eventName.isNullOrEmpty()) put("eventName", eventName)
                if (!notes.isNullOrEmpty()) put("notes", notes)
            }

            Log.d(TAG, "POST $url")
            Log.d(TAG, "Request Body: $requestBody")

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "Response Code: $responseCode")
            Log.d(TAG, "Response: $responseText")

            when (responseCode) {
                200, 201 -> {
                    val jsonResponse = JSONObject(responseText)
                    val event = parseCalendarEvent(jsonResponse)
                    Result.success(event)
                }
                401 -> Result.failure(Exception("Authentication failed. Please login again."))
                404 -> Result.failure(Exception("API endpoint not found. Check your backend URL."))
                409 -> Result.failure(Exception("This outfit is already scheduled for this date."))
                else -> {
                    val errorMessage = try {
                        val errorJson = JSONObject(responseText)
                        errorJson.optString("error", "Failed: $responseCode")
                    } catch (e: Exception) {
                        "Failed: $responseCode - $responseText"
                    }
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout", e)
            Result.failure(Exception("Request timed out. Server may be starting up. Please try again in 30 seconds."))
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Connection failed", e)
            Result.failure(Exception("Cannot connect to server. Please check your internet connection."))
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling outfit", e)
            Result.failure(Exception("Error: ${e.message}"))
        }
    }

    suspend fun getScheduledOutfits(
        startDate: Date,
        endDate: Date
    ): Result<List<ScheduledOutfit>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
            Log.d(TAG, "Fetching scheduled outfits with token: ${token.take(20)}...")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val startDateStr = dateFormat.format(startDate)
            val endDateStr = dateFormat.format(endDate)

            val url = URL("$baseUrl/api/Calendar/scheduled?startDate=$startDateStr&endDate=$endDateStr")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            Log.d(TAG, "GET $url")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "Response Code: $responseCode")
            Log.d(TAG, "Response: ${responseText.take(200)}...")

            when (responseCode) {
                200 -> {
                    val scheduledOutfits = parseScheduledOutfits(responseText)
                    Log.d(TAG, "Successfully parsed ${scheduledOutfits.size} scheduled outfits")
                    Result.success(scheduledOutfits)
                }
                401 -> Result.failure(Exception("Authentication failed. Please login again."))
                404 -> Result.failure(Exception("API endpoint not found. Check your backend URL."))
                else -> {
                    val errorMessage = try {
                        val errorJson = JSONObject(responseText)
                        errorJson.optString("error", "Failed: $responseCode")
                    } catch (e: Exception) {
                        "Failed: $responseCode"
                    }
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout", e)
            Result.failure(Exception("Request timed out. Server may be starting up. Please try again in 30 seconds."))
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Connection failed", e)
            Result.failure(Exception("Cannot connect to server. Please check your internet connection."))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching scheduled outfits", e)
            Result.failure(Exception("Error: ${e.message}"))
        }
    }

    suspend fun deleteScheduledOutfit(scheduleId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
            Log.d(TAG, "Deleting schedule $scheduleId with token: ${token.take(20)}...")

            val url = URL("$baseUrl/api/Calendar/schedule/$scheduleId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            Log.d(TAG, "DELETE $url")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            Log.d(TAG, "Delete Response Code: $responseCode")

            when (responseCode) {
                200, 204 -> {
                    Log.d(TAG, "Schedule deleted successfully")
                    Result.success(Unit)
                }
                401 -> Result.failure(Exception("Authentication failed. Please login again."))
                404 -> Result.failure(Exception("Schedule not found or already deleted."))
                else -> {
                    val errorMessage = try {
                        val errorJson = JSONObject(responseText)
                        errorJson.optString("error", "Failed to delete: $responseCode")
                    } catch (e: Exception) {
                        "Failed to delete: $responseCode"
                    }
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout", e)
            Result.failure(Exception("Request timed out. Please try again."))
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Connection failed", e)
            Result.failure(Exception("Cannot connect to server. Please check your internet connection."))
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting schedule", e)
            Result.failure(Exception("Error: ${e.message}"))
        }
    }

    private fun parseCalendarEvent(json: JSONObject): CalendarEvent {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        return CalendarEvent(
            eventId = json.getInt("scheduleId"),
            userId = json.getString("userId"),
            outfitId = json.optInt("outfitId", 0),
            eventDate = try {
                dateFormat.parse(json.getString("eventDate")) ?: Date()
            } catch (e: Exception) {
                try {
                    simpleDateFormat.parse(json.getString("eventDate")) ?: Date()
                } catch (e: Exception) {
                    Date()
                }
            },
            eventName = if (json.has("eventName") && !json.isNull("eventName"))
                json.getString("eventName") else null,
            notes = if (json.has("notes") && !json.isNull("notes"))
                json.getString("notes") else null
        )
    }

    private fun parseScheduledOutfits(jsonString: String): List<ScheduledOutfit> {
        val list = mutableListOf<ScheduledOutfit>()

        try {
            val jsonArray = JSONArray(jsonString)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                val eventDate = try {
                    dateFormat.parse(obj.getString("eventDate")) ?: Date()
                } catch (e: Exception) {
                    try {
                        simpleDateFormat.parse(obj.getString("eventDate")) ?: Date()
                    } catch (e: Exception) {
                        Date()
                    }
                }

                val outfitObj = obj.getJSONObject("outfit")
                val outfitId = outfitObj.getInt("outfitId")
                val outfitName = outfitObj.getString("name")

                val itemsArray = outfitObj.getJSONArray("items")
                val items = mutableListOf<OutfitItemInfo>()

                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    items.add(
                        OutfitItemInfo(
                            itemId = itemObj.getInt("itemId"),
                            name = itemObj.optString("name", ""),
                            imageUrl = itemObj.optString("imageUrl", ""),
                            category = itemObj.optString("category", ""),
                            subcategory = itemObj.optString("subcategory", "")
                        )
                    )
                }

                val outfit = OutfitInfo(
                    outfitId = outfitId,
                    name = outfitName,
                    category = outfitObj.optString("category", ""),
                    items = items
                )

                val weatherInfo = if (obj.has("weather") && !obj.isNull("weather")) {
                    val weatherObj = obj.getJSONObject("weather")
                    WeatherInfo(
                        minTemp = weatherObj.optInt("minTemp", 0),
                        maxTemp = weatherObj.optInt("maxTemp", 0),
                        condition = weatherObj.optString("condition", ""),
                        precipitation = weatherObj.optInt("precipitation", 0)
                    )
                } else null

                list.add(
                    ScheduledOutfit(
                        scheduleId = obj.getInt("scheduleId"),
                        date = eventDate,
                        outfit = outfit,
                        weatherForecast = weatherInfo
                    )
                )
            }

            Log.d(TAG, "Successfully parsed ${list.size} scheduled outfits")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing scheduled outfits", e)
        }

        return list
    }
}