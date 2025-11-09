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
    val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZrbWhtdGlvZWhva3J1a3F3YW5vIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mjg1NjkwNTEsImV4cCI6MjA0NDE0NTA1MX0.z6XKVvEHKL2Edhp_Wy-yjV8PZvcEtCLCv9tA3uuDwGY"

    /**
     * Save a notification to the database
     */
    suspend fun saveNotification(notification: Notification): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                val accessToken = prefs.getString("access_token", null)

                if (accessToken == null) {
                    Log.e(TAG, "Access token not found, cannot save notification")
                    return@withContext false
                }

                val json = JSONObject().apply {
                    put("user_id", notification.userId)
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

    /**
     * Get all notifications for the current user
     */
    suspend fun getUserNotifications(): List<Notification> {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                val accessToken = prefs.getString("access_token", null) ?: return@withContext emptyList()
                val userId = prefs.getString("user_id", null) ?: return@withContext emptyList()

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/notifications?user_id=eq.$userId&order=sent_at.desc,scheduled_at.desc")
                    .get()
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val jsonArray = org.json.JSONArray(body ?: "[]")
                    val notifications = mutableListOf<Notification>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        notifications.add(
                            Notification(
                                id = obj.optInt("notifications_id", 0),
                                userId = obj.getInt("user_id"),
                                title = obj.getString("title"),
                                message = obj.getString("message"),
                                type = obj.optString("type", "general"),
                                scheduledAt = obj.getString("scheduled_at"),
                                sentAt = obj.optString("sent_at", null),
                                status = obj.optString("status", "queued")
                            )
                        )
                    }

                    Log.d(TAG, "Loaded ${notifications.size} notifications")
                    notifications
                } else {
                    Log.e(TAG, "Failed to fetch notifications: ${response.code}")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching notifications: ${e.message}", e)
                emptyList()
            }
        }
    }
}