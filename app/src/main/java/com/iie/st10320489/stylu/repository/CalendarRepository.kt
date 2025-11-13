package com.iie.st10320489.stylu.repository

import android.content.Context
import android.util.Log
import com.iie.st10320489.stylu.data.local.StyluDatabase
import com.iie.st10320489.stylu.data.local.dao.CalendarDao
import com.iie.st10320489.stylu.data.local.dao.OutfitDao
import com.iie.st10320489.stylu.data.local.entities.CalendarEntity
import com.iie.st10320489.stylu.data.local.entities.toEntity
import com.iie.st10320489.stylu.data.local.entities.toScheduledOutfit
import com.iie.st10320489.stylu.data.models.calendar.CalendarEvent
import com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit
import com.iie.st10320489.stylu.data.models.calendar.WeatherInfo
import com.iie.st10320489.stylu.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val database = StyluDatabase.getDatabase(context)
    private val calendarDao: CalendarDao = database.calendarDao()
    private val outfitDao: OutfitDao = database.outfitDao()
    private val apiService = ApiService(context)

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

    /**
     * ✅ ONLINE-FIRST: Schedule outfit
     * 1. Try API first (when online)
     * 2. Save API response to Room (cache)
     * 3. If API fails (offline), save to Room only
     */
    suspend fun scheduleOutfit(
        outfitId: Int,
        date: Date,
        eventName: String? = null,
        notes: String? = null
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
            Log.d(TAG, "📡 Attempting to schedule outfit via API...")

            // Try API first
            val apiResult = scheduleOutfitViaApi(token, outfitId, date, eventName, notes)

            if (apiResult.isSuccess) {
                val event = apiResult.getOrThrow()

                // ✅ Save API response to Room (cache)
                val entity = CalendarEntity(
                    scheduleId = event.eventId,
                    userId = event.userId,
                    outfitId = outfitId,
                    scheduledDate = date.time,
                    eventName = eventName,
                    notes = notes,
                    weatherMinTemp = null,
                    weatherMaxTemp = null,
                    weatherCondition = null,
                    weatherPrecipitation = null
                )
                calendarDao.insertScheduledOutfit(entity)
                Log.d(TAG, "✅ API success - saved to cache")

                Result.success(event)
            } else {
                // API failed - might be offline
                throw apiResult.exceptionOrNull() ?: Exception("API request failed")
            }

        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "⚠️ Timeout - trying offline mode", e)
            scheduleOffline(outfitId, date, eventName, notes)
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "⚠️ No connection - saving offline", e)
            scheduleOffline(outfitId, date, eventName, notes)
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "⚠️ No internet - saving offline", e)
            scheduleOffline(outfitId, date, eventName, notes)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling outfit", e)
            Result.failure(Exception("Error: ${e.message}"))
        }
    }

    /**
     * Schedule via API
     */
    private suspend fun scheduleOutfitViaApi(
        token: String,
        outfitId: Int,
        date: Date,
        eventName: String?,
        notes: String?
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        return@withContext try {
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save schedule offline (fallback)
     */
    private suspend fun scheduleOffline(
        outfitId: Int,
        date: Date,
        eventName: String?,
        notes: String?
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Generate temporary negative ID for offline schedules
            val tempId = -(System.currentTimeMillis().toInt())

            val entity = CalendarEntity(
                scheduleId = tempId,
                userId = "offline",
                outfitId = outfitId,
                scheduledDate = date.time,
                eventName = eventName,
                notes = notes,
                weatherMinTemp = null,
                weatherMaxTemp = null,
                weatherCondition = null,
                weatherPrecipitation = null
            )

            calendarDao.insertScheduledOutfit(entity)
            Log.d(TAG, "💾 Saved offline with temp ID: $tempId")

            val event = CalendarEvent(
                eventId = tempId,
                userId = "offline",
                outfitId = outfitId,
                eventDate = date,
                eventName = eventName,
                notes = notes
            )

            Result.success(event)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save offline", e)
            Result.failure(Exception("Failed to save offline: ${e.message}"))
        }
    }

    /**
     * ✅ ONLINE-FIRST: Get scheduled outfits
     * 1. Try API first (when online)
     * 2. Save API response to Room (cache)
     * 3. Return API data
     * 4. If API fails (offline), return cached Room data
     */
    suspend fun getScheduledOutfits(
        startDate: Date,
        endDate: Date
    ): Result<List<ScheduledOutfit>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
            Log.d(TAG, "📡 Fetching schedules from API...")

            // Try API first
            val apiResult = getScheduledOutfitsFromApi(token, startDate, endDate)

            if (apiResult.isSuccess) {
                val scheduledOutfits = apiResult.getOrThrow()

                // ✅ Save API response to Room (cache)
                Log.d(TAG, "💾 Caching ${scheduledOutfits.size} schedules to Room...")
                scheduledOutfits.forEach { scheduledOutfit ->
                    val entity = scheduledOutfit.toEntity(scheduledOutfit.outfit.userId)
                    calendarDao.insertScheduledOutfit(entity)
                }
                Log.d(TAG, "✅ API success - cache updated")

                Result.success(scheduledOutfits)
            } else {
                // API failed - might be offline
                throw apiResult.exceptionOrNull() ?: Exception("API request failed")
            }

        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "⚠️ Timeout - loading from cache", e)
            getScheduledOutfitsFromCache(startDate, endDate)
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "⚠️ No connection - loading from cache", e)
            getScheduledOutfitsFromCache(startDate, endDate)
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "⚠️ No internet - loading from cache", e)
            getScheduledOutfitsFromCache(startDate, endDate)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error, trying cache", e)
            getScheduledOutfitsFromCache(startDate, endDate)
        }
    }

    /**
     * Get schedules from API
     */
    private suspend fun getScheduledOutfitsFromApi(
        token: String,
        startDate: Date,
        endDate: Date
    ): Result<List<ScheduledOutfit>> = withContext(Dispatchers.IO) {
        return@withContext try {
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

            when (responseCode) {
                200 -> {
                    val scheduledOutfits = parseScheduledOutfits(responseText)
                    Log.d(TAG, "✅ Parsed ${scheduledOutfits.size} scheduled outfits from API")
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get schedules from cache (offline fallback)
     */
    private suspend fun getScheduledOutfitsFromCache(
        startDate: Date,
        endDate: Date
    ): Result<List<ScheduledOutfit>> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "💾 Loading schedules from cache...")

            val entities = calendarDao.getScheduledOutfitsByDateRange(
                startDate = startDate.time,
                endDate = endDate.time
            )

            Log.d(TAG, "📦 Found ${entities.size} schedules in cache")

            val scheduledOutfits = entities.mapNotNull { entity ->
                val outfitEntity = outfitDao.getOutfitById(entity.outfitId)
                if (outfitEntity == null) {
                    Log.w(TAG, "⚠️ Outfit ${entity.outfitId} not found for schedule ${entity.scheduleId}")
                    return@mapNotNull null
                }

                val outfitItems = outfitDao.getOutfitLayout(entity.outfitId)
                val items = outfitItems.mapNotNull { outfitItem ->
                    val item = database.itemDao().getItemById(outfitItem.itemId)
                    item?.let {
                        ApiService.OutfitItemDetail(
                            itemId = it.itemId,
                            name = it.name ?: "",
                            imageUrl = it.imageUrl,
                            colour = it.colour,
                            subcategory = it.subcategory,
                            layoutData = ApiService.ItemLayoutData(
                                x = outfitItem.x,
                                y = outfitItem.y,
                                scale = outfitItem.scale,
                                width = outfitItem.width,
                                height = outfitItem.height
                            )
                        )
                    }
                }

                val outfit = ApiService.OutfitDetail(
                    outfitId = outfitEntity.outfitId,
                    userId = outfitEntity.userId,
                    name = outfitEntity.name,
                    category = outfitEntity.category,
                    schedule = null,
                    items = items,
                    createdAt = outfitEntity.createdAt
                )

                entity.toScheduledOutfit(outfit)
            }

            Log.d(TAG, "✅ Returning ${scheduledOutfits.size} cached schedules")
            Result.success(scheduledOutfits)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading from cache", e)
            Result.failure(Exception("Error loading cached data: ${e.message}"))
        }
    }

    /**
     * ✅ ONLINE-FIRST: Delete scheduled outfit
     * 1. Try API first (when online)
     * 2. Delete from Room (cache)
     * 3. If API fails (offline), delete from Room only
     */
    suspend fun deleteScheduledOutfit(scheduleId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
            Log.d(TAG, "📡 Deleting schedule $scheduleId via API...")

            // Try API first
            val apiResult = deleteScheduleViaApi(token, scheduleId)

            if (apiResult.isSuccess) {
                // ✅ Delete from Room (cache)
                calendarDao.deleteScheduledOutfit(scheduleId)
                Log.d(TAG, "✅ API delete success - removed from cache")
                Result.success(Unit)
            } else {
                throw apiResult.exceptionOrNull() ?: Exception("API delete failed")
            }

        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "⚠️ Timeout - deleting from cache only", e)
            deleteOffline(scheduleId)
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "⚠️ No connection - deleting offline", e)
            deleteOffline(scheduleId)
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "⚠️ No internet - deleting offline", e)
            deleteOffline(scheduleId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting schedule", e)
            Result.failure(Exception("Error: ${e.message}"))
        }
    }

    /**
     * Delete via API
     */
    private suspend fun deleteScheduleViaApi(
        token: String,
        scheduleId: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
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
                    Log.d(TAG, "✅ API delete successful")
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete offline (fallback)
     */
    private suspend fun deleteOffline(scheduleId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            calendarDao.deleteScheduledOutfit(scheduleId)
            Log.d(TAG, "💾 Deleted from cache (offline)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete offline", e)
            Result.failure(Exception("Failed to delete: ${e.message}"))
        }
    }

    /**
     * Get scheduled outfits as Flow for real-time updates (from cache)
     */
    fun getScheduledOutfitsFlow(
        startDate: Date,
        endDate: Date
    ): Flow<List<ScheduledOutfit>> {
        return calendarDao.getScheduledOutfitsByDateRangeFlow(
            startDate = startDate.time,
            endDate = endDate.time
        ).map { entities ->
            entities.mapNotNull { entity ->
                val outfitEntity = outfitDao.getOutfitById(entity.outfitId) ?: return@mapNotNull null
                val outfitItems = outfitDao.getOutfitLayout(entity.outfitId)
                val items = outfitItems.mapNotNull { outfitItem ->
                    val item = database.itemDao().getItemById(outfitItem.itemId)
                    item?.let {
                        ApiService.OutfitItemDetail(
                            itemId = it.itemId,
                            name = it.name ?: "",
                            imageUrl = it.imageUrl,
                            colour = it.colour,
                            subcategory = it.subcategory,
                            layoutData = ApiService.ItemLayoutData(
                                x = outfitItem.x,
                                y = outfitItem.y,
                                scale = outfitItem.scale,
                                width = outfitItem.width,
                                height = outfitItem.height
                            )
                        )
                    }
                }

                val outfit = ApiService.OutfitDetail(
                    outfitId = outfitEntity.outfitId,
                    userId = outfitEntity.userId,
                    name = outfitEntity.name,
                    category = outfitEntity.category,
                    schedule = null,
                    items = items,
                    createdAt = outfitEntity.createdAt
                )

                entity.toScheduledOutfit(outfit)
            }
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
            Log.d(TAG, "📥 Raw JSON Response: $jsonString")

            val jsonArray = JSONArray(jsonString)
            Log.d(TAG, "📊 Array length: ${jsonArray.length()}")

            if (jsonArray.length() == 0) {
                Log.w(TAG, "⚠️ API returned empty array")
                return emptyList()
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                Log.d(TAG, "📦 Processing schedule $i: ${obj.toString()}")

                val eventDate = try {
                    dateFormat.parse(obj.getString("eventDate")) ?: Date()
                } catch (e: Exception) {
                    try {
                        simpleDateFormat.parse(obj.getString("eventDate")) ?: Date()
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to parse date: ${obj.getString("eventDate")}", e)
                        Date()
                    }
                }

                if (!obj.has("outfit") || obj.isNull("outfit")) {
                    Log.w(TAG, "⚠️ Schedule $i has no outfit object - skipping")
                    continue
                }

                val outfitObj = obj.getJSONObject("outfit")
                Log.d(TAG, "👕 Outfit object: ${outfitObj.toString()}")

                val outfitId = outfitObj.getInt("outfitId")
                val outfitName = outfitObj.getString("name")
                val category = outfitObj.optString("category", null)

                val itemsArray = outfitObj.getJSONArray("items")
                val items = mutableListOf<ApiService.OutfitItemDetail>()

                Log.d(TAG, "🎨 Processing ${itemsArray.length()} items for outfit $outfitId")

                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)

                    val layoutData = if (itemObj.has("layoutData") && !itemObj.isNull("layoutData")) {
                        val ldObj = itemObj.getJSONObject("layoutData")
                        ApiService.ItemLayoutData(
                            x = ldObj.optDouble("x", 0.0).toFloat(),
                            y = ldObj.optDouble("y", 0.0).toFloat(),
                            scale = ldObj.optDouble("scale", 1.0).toFloat(),
                            width = ldObj.optInt("width", 100),
                            height = ldObj.optInt("height", 100)
                        )
                    } else {
                        Log.d(TAG, "⚠️ Item $j has no layoutData")
                        null
                    }

                    items.add(
                        ApiService.OutfitItemDetail(
                            itemId = itemObj.getInt("itemId"),
                            name = itemObj.optString("name", ""),
                            imageUrl = itemObj.optString("imageUrl", ""),
                            colour = itemObj.optString("colour", null),
                            subcategory = itemObj.optString("subcategory", ""),
                            layoutData = layoutData
                        )
                    )
                }

                val outfit = ApiService.OutfitDetail(
                    outfitId = outfitId,
                    userId = "",
                    name = outfitName,
                    category = category,
                    schedule = null,
                    items = items,
                    createdAt = ""
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
                        eventName = obj.optString("eventName", null),
                        notes = obj.optString("notes", null),
                        weatherForecast = weatherInfo
                    )
                )

                Log.d(TAG, "✅ Successfully parsed schedule $i: $outfitName for $eventDate")
            }

            Log.d(TAG, "✅ Total scheduled outfits parsed: ${list.size}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ FATAL: Error parsing scheduled outfits", e)
            e.printStackTrace()
        }

        return list
    }
}