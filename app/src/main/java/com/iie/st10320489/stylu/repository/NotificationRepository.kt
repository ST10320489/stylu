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
import org.json.JSONObject

class NotificationRepository(private val context: Context) {

    private val TAG = "NotificationRepo"

    private val SUPABASE_URL = "https://fkmhmtioehokrukqwano.supabase.co"
    private val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZrbWhtdGlvZWhva3J1a3F3YW5vIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgyMDAzNDIsImV4cCI6MjA3Mzc3NjM0Mn0.wg5fNm5_M8CRN3uzHnqvaxovIUDLCUWDcSiFJ14WqNE"

    /**
     * Save a notification to the database
     */
    suspend fun getUserNotifications(): List<Notification> {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                val accessToken = prefs.getString("access_token", null)
                val userId = prefs.getString("user_id", null)  // This is a UUID string

                if (accessToken == null) {
                    Log.e(TAG, "No access token found")
                    return@withContext emptyList()
                }

                if (userId == null) {
                    Log.e(TAG, "No user ID found")
                    return@withContext emptyList()
                }

                // ✅ USE UUID DIRECTLY - Don't convert to int!
                Log.d(TAG, "Fetching notifications for user: $userId")

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
                    Log.e(TAG, "Failed to fetch notifications: ${response.code}")
                    Log.e(TAG, "Response body: ${response.body?.string()}")
                    return@withContext emptyList()
                }

                val body = response.body?.string()
                val jsonArray = org.json.JSONArray(body ?: "[]")
                val notifications = mutableListOf<Notification>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)

                    // ✅ Parse user_id as string (UUID) but convert to int for model
                    val userIdFromDb = obj.getString("user_id")

                    notifications.add(
                        Notification(
                            id = obj.optInt("notifications_id", 0),
                            userId = userIdFromDb.hashCode(),  // Convert to int for model
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
                Log.e(TAG, "❌ Error fetching notifications: ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun saveNotification(notification: Notification): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                val accessToken = prefs.getString("access_token", null)
                val userId = prefs.getString("user_id", null)  // UUID string

                if (accessToken == null) {
                    Log.e(TAG, "Access token not found, cannot save notification")
                    return@withContext false
                }

                if (userId == null) {
                    Log.e(TAG, "User ID not found, cannot save notification")
                    return@withContext false
                }

                // ✅ USE UUID DIRECTLY
                val json = JSONObject().apply {
                    put("user_id", userId)  // Send UUID string, not int
                    put("title", notification.title)
                    put("message", notification.message)
                    put("type", notification.type)
                    put("scheduled_at", notification.scheduledAt)
                    put("sent_at", notification.sentAt)
                    put("status", notification.status)
                }

                Log.d(TAG, "Saving notification: ${json.toString()}")

                val client = OkHttpClient()
                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/notifications")
                    .post(body)
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Notification saved successfully!")
                    Log.d(TAG, "Response: $responseBody")
                    true
                } else {
                    Log.e(TAG, "❌ Failed to save notification: ${response.code}")
                    Log.e(TAG, "Response body: $responseBody")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving notification: ${e.message}", e)
                false
            }
        }
    }

}