package com.iie.st10320489.stylu.repository

import android.content.Context
import android.util.Log
import com.iie.st10320489.stylu.data.models.notifications.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NotificationRepository(private val context: Context) {

    private val TAG = "NotificationRepo"

    // ✅ NEW: Use your API instead of Supabase directly
    private val API_BASE_URL = "https://stylu-api-x69c.onrender.com"

    // ⚠️ Keep these for fetching notifications (reading doesn't require Service Role)
    private val SUPABASE_URL = "https://fkmhmtioehokrukqwano.supabase.co"
    private val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZrbWhtdGlvZWhva3J1a3F3YW5vIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgyMDAzNDIsImV4cCI6MjA3Mzc3NjM0Mn0.wg5fNm5_M8CRN3uzHnqvaxovIUDLCUWDcSiFJ14WqNE"

    /**
     * Get all notifications for current user
     * ✅ This still uses Supabase directly since it requires user auth
     */
    suspend fun getUserNotifications(): List<Notification> {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                val accessToken = prefs.getString("access_token", null)
                val userId = prefs.getString("user_id", null)

                if (accessToken == null || userId == null) {
                    Log.e(TAG, "❌ Missing credentials")
                    return@withContext emptyList()
                }

                Log.d(TAG, "📥 Fetching notifications for user: $userId")

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/notifications?user_id=eq.$userId&order=sent_at.desc,scheduled_at.desc")
                    .get()
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e(TAG, "❌ Failed: ${response.code}")
                    Log.e(TAG, "Response: ${response.body?.string()}")
                    return@withContext emptyList()
                }

                val body = response.body?.string()
                val jsonArray = JSONArray(body ?: "[]")
                val notifications = mutableListOf<Notification>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val userIdFromDb = obj.getString("user_id")

                    notifications.add(
                        Notification(
                            id = obj.optInt("notifications_id", 0),
                            userId = userIdFromDb.hashCode(),
                            title = obj.getString("title"),
                            message = obj.getString("message"),
                            type = obj.optString("type", "general"),
                            scheduledAt = obj.getString("scheduled_at"),
                            sentAt = obj.optString("sent_at", null),
                            status = obj.optString("status", "queued")
                        )
                    )
                }

                Log.d(TAG, "✅ Loaded ${notifications.size} notifications")
                notifications

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * ✅ UPDATED: Save notification via API (works even when logged out)
     */
    suspend fun saveNotification(notification: Notification): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                val userId = prefs.getString("user_id", null)

                if (userId == null) {
                    Log.e(TAG, "❌ Missing user ID")
                    return@withContext false
                }

                val json = JSONObject().apply {
                    put("userId", userId)
                    put("title", notification.title)
                    put("message", notification.message)
                    put("type", notification.type)
                    put("timestamp", notification.scheduledAt)
                    put("status", notification.status)
                }

                Log.d(TAG, "💾 Saving via API: ${json.toString()}")

                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url("$API_BASE_URL/api/Notification/save")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Saved successfully via API")
                    true
                } else {
                    Log.e(TAG, "❌ API Failed: ${response.code}")
                    Log.e(TAG, "Response: $responseBody")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error: ${e.message}", e)
                false
            }
        }
    }

    /**
     * ✅ UPDATED: Direct save method using API (better for services and workers)
     * Now works even when user is logged out!
     */
    suspend fun saveNotificationDirect(
        userId: String,
        title: String,
        message: String,
        type: String,
        data: Map<String, Any>? = null,
        timestamp: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("userId", userId)
                    put("title", title)
                    put("message", message)
                    put("type", type)
                    put("timestamp", timestamp)
                    put("status", "sent")

                    // Store additional data if provided
                    if (data != null) {
                        put("data", JSONObject(data))
                    }
                }

                Log.d(TAG, "💾 Saving via API: ${json.toString()}")

                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url("$API_BASE_URL/api/Notification/save")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Saved successfully via API")
                    true
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "❌ API Failed: ${response.code} - $errorBody")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error: ${e.message}", e)
                false
            }
        }
    }

    /**
     * ✅ NEW: Sync queued notifications via batch API
     */
    suspend fun syncQueuedNotifications(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val queuePrefs = context.getSharedPreferences("stylu_notifications_queue", Context.MODE_PRIVATE)
                val allQueued = queuePrefs.all

                if (allQueued.isEmpty()) {
                    Log.d(TAG, "No queued notifications to sync")
                    return@withContext true
                }

                Log.d(TAG, "🔄 Syncing ${allQueued.size} queued notifications...")

                val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                val userId = prefs.getString("user_id", null)

                if (userId == null) {
                    Log.w(TAG, "Cannot sync: no user ID")
                    return@withContext false
                }

                // Build batch request
                val notificationsArray = JSONArray()
                allQueued.forEach { (_, value) ->
                    try {
                        val notifJson = JSONObject(value as String)
                        val batchItem = JSONObject().apply {
                            put("userId", userId)
                            put("title", notifJson.getString("title"))
                            put("message", notifJson.getString("message"))
                            put("type", notifJson.getString("type"))
                            put("timestamp", notifJson.optString("timestamp"))
                            put("status", "sent")
                        }
                        notificationsArray.put(batchItem)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing queued notification", e)
                    }
                }

                val batchRequest = JSONObject().apply {
                    put("notifications", notificationsArray)
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val body = batchRequest.toString().toRequestBody("application/json".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url("$API_BASE_URL/api/Notification/save-batch")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Batch sync successful")
                    Log.d(TAG, "Response: $responseBody")

                    // Clear the queue on success
                    queuePrefs.edit().clear().apply()

                    // Notify UI to refresh
                    context.sendBroadcast(android.content.Intent("com.iie.st10320489.stylu.NEW_NOTIFICATION"))

                    true
                } else {
                    Log.e(TAG, "❌ Batch sync failed: ${response.code}")
                    Log.e(TAG, "Response: $responseBody")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error syncing: ${e.message}", e)
                false
            }
        }
    }
}