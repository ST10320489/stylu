package com.iie.st10320489.stylu.repository

import android.content.Context
import android.util.Log
import com.iie.st10320489.stylu.data.models.calendar.CalendarEvent
import com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit
import com.iie.st10320489.stylu.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class CalendarRepository(private val context: Context) {

    private val apiService = ApiService(context)
    private val baseUrl = "https://stylu-api-x69c.onrender.com"

    companion object {
        private const val TAG = "CalendarRepository"
        private const val PREFS_NAME = "stylu_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
    }

    private fun getAccessToken(): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCESS_TOKEN, null)
    }

    suspend fun scheduleOutfit(
        outfitId: Int,
        date: Date,
        eventName: String? = null,
        notes: String? = null
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
                ?: return@withContext Result.failure(Exception("No access token"))

            val url = URL("$baseUrl/api/Calendar/schedule")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val requestBody = JSONObject().apply {
                put("outfitId", outfitId)
                put("eventDate", dateFormat.format(date))
                eventName?.let { put("eventName", it) }
                notes?.let { put("notes", it) }
            }

            Log.d(TAG, "Scheduling outfit - Request: $requestBody")

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

            Log.d(TAG, "Schedule Response Code: $responseCode")
            Log.d(TAG, "Schedule Response: $responseText")

            if (responseCode in 200..201) {
                val jsonResponse = JSONObject(responseText)
                val event = parseCalendarEvent(jsonResponse)
                Result.success(event)
            } else {
                Result.failure(Exception("Failed to schedule outfit: $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling outfit", e)
            Result.failure(e)
        }
    }

    suspend fun getScheduledOutfits(
        startDate: Date,
        endDate: Date
    ): Result<List<ScheduledOutfit>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
                ?: return@withContext Result.failure(Exception("No access token"))

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val startDateStr = dateFormat.format(startDate)
            val endDateStr = dateFormat.format(endDate)

            val url = URL("$baseUrl/api/Calendar/scheduled?startDate=$startDateStr&endDate=$endDateStr")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "Get Scheduled Response: $responseText")

            if (responseCode == 200) {
                val scheduledOutfits = parseScheduledOutfits(responseText)
                Result.success(scheduledOutfits)
            } else {
                Result.failure(Exception("Failed to fetch scheduled outfits"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching scheduled outfits", e)
            Result.failure(e)
        }
    }

    suspend fun deleteScheduledOutfit(scheduleId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
                ?: return@withContext Result.failure(Exception("No access token"))

            val url = URL("$baseUrl/api/Calendar/schedule/$scheduleId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = connection.responseCode

            if (responseCode in 200..204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete schedule"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting schedule", e)
            Result.failure(e)
        }
    }

    private fun parseCalendarEvent(json: JSONObject): CalendarEvent {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return CalendarEvent(
            eventId = json.getInt("scheduleId"),
            userId = json.getString("userId"),
            outfitId = json.optInt("outfitId"),
            eventDate = dateFormat.parse(json.getString("eventDate")) ?: Date(),
            eventName = json.optString("eventName", null),
            notes = json.optString("notes", null)
        )
    }

    private fun parseScheduledOutfits(jsonString: String): List<ScheduledOutfit> {
        val list = mutableListOf<ScheduledOutfit>()
        val jsonArray = JSONArray(jsonString)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            // Parse based on your API response structure
            // This is a placeholder - adjust based on actual API
        }

        return list
    }
}