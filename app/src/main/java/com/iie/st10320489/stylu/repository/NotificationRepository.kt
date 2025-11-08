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
    val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

    suspend fun saveNotification(notification: Notification) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                val accessToken = prefs.getString("access_token", null) ?: return@withContext

                val json = JSONObject().apply {
                    put("user_id", notification.userId)
                    put("title", notification.title)
                    put("message", notification.message)
                    put("type", notification.type)
                    put("scheduled_at", notification.scheduledAt)
                    put("sent_at", notification.sentAt)
                    put("status", notification.status)
                }

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
                if (response.isSuccessful) {
                    Log.d(TAG, "Notification saved successfully!")
                } else {
                    Log.e(TAG, "Failed to save notification: ${response.code}")
                }
                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving notification", e)
            }
        }
    }
}
