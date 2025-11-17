package com.iie.st10320489.stylu.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.iie.st10320489.stylu.MainActivity
import com.iie.st10320489.stylu.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM_Service"


    private val SUPABASE_URL = "https://fkmhmtioehokrukqwano.supabase.co"
    private val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZrbWhtdGlvZWhva3J1a3F3YW5vIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgyMDAzNDIsImV4cCI6MjA3Mzc3NjM0Mn0.wg5fNm5_M8CRN3uzHnqvaxovIUDLCUWDcSiFJ14WqNE"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "========================================")
        Log.d(TAG, "New FCM Token: $token")
        Log.d(TAG, "========================================")

        // Save token locally
        saveTokenLocally(token)

        FirebaseMessaging.getInstance().subscribeToTopic("fashion")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Subscribed to topic: fashion")
                } else {
                    Log.e(TAG, "Failed to subscribe to topic", task.exception)
                }
            }


        sendTokenToSupabase(token)
    }

    private fun saveTokenLocally(token: String) {
        val prefs = getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
        Log.d(TAG, "Token saved locally")
    }

    private fun sendTokenToSupabase(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                val accessToken = prefs.getString("access_token", null)
                val userId = prefs.getString("user_id", null)

                if (accessToken == null || userId == null) {
                    Log.w(TAG, "No auth token or user ID, will send FCM token after login")
                    return@launch
                }

                // SEND DIRECTLY TO SUPABASE (no API middleman)
                val json = JSONObject().apply {
                    put("user_id", userId)
                    put("fcm_token", token)
                    put("platform", "android")
                    put("is_active", true)
                }

                val client = OkHttpClient()
                val body = json.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/device_tokens")
                    .post(body)
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    Log.d(TAG, "Token sent to Supabase successfully")
                } else {
                    Log.e(TAG, "Failed to send token: ${response.code}")
                }

                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending token to Supabase", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Handle notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Notification Title: ${it.title}")
            Log.d(TAG, "Notification Body: ${it.body}")

            // Save to database and show notification
            saveNotificationToDatabase(
                title = it.title ?: "Stylu",
                message = it.body ?: "",
                type = remoteMessage.data["type"] ?: "general"
            )

            showNotification(it.title, it.body)
        }

        // Handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data: ${remoteMessage.data}")
            handleDataPayload(remoteMessage.data)
        }
    }

    private fun handleDataPayload(data: Map<String, String>) {
        val type = data["type"]
        val message = data["message"]
        val title = data["title"]

        when (type) {
            "new_drop" -> {
                saveNotificationToDatabase(
                    title = "New Drop Available! ",
                    message = message ?: "Check out the latest styles",
                    type = "new_drop"
                )
                showNotification(
                    title = "New Drop Available! ",
                    body = message ?: "Check out the latest styles",
                    channelId = "stylu_drops"
                )
            }
            "outfit_liked" -> {
                saveNotificationToDatabase(
                    title = "Someone liked your outfit! ",
                    message = message ?: "Your style is inspiring others",
                    type = "outfit_liked"
                )
                showNotification(
                    title = "Someone liked your outfit! ",
                    body = message ?: "Your style is inspiring others",
                    channelId = "stylu_social"
                )
            }
            "reminder" -> {
                saveNotificationToDatabase(
                    title = title ?: "Outfit Reminder",
                    message = message ?: "Time to plan your outfit!",
                    type = "reminder"
                )
                showNotification(
                    title = title ?: "Outfit Reminder",
                    body = message ?: "Time to plan your outfit!",
                    channelId = "stylu_reminders"
                )
            }
            else -> {
                saveNotificationToDatabase(
                    title = title ?: "Stylu",
                    message = message ?: "New notification",
                    type = type ?: "general"
                )
                showNotification(
                    title = title ?: "Stylu",
                    body = message ?: "New notification",
                    channelId = "stylu_general"
                )
            }
        }
    }


    private fun saveNotificationToDatabase(title: String, message: String, type: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                val accessToken = prefs.getString("access_token", null)
                val userId = prefs.getString("user_id", null)

                if (accessToken == null || userId == null) {
                    Log.w(TAG, "No auth token or user ID, cannot save notification")
                    return@launch
                }

                // Get current timestamp in ISO 8601 format
                val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())

                val json = JSONObject().apply {
                    put("user_id", userId)
                    put("title", title)
                    put("message", message)
                    put("type", type)
                    put("scheduled_at", timestamp)
                    put("sent_at", timestamp)
                    put("status", "sent")
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
                    Log.d(TAG, "Notification saved to database")
                    sendBroadcast(Intent("com.iie.st10320489.stylu.NEW_NOTIFICATION"))
                } else {
                    Log.e(TAG, "Failed to save notification: ${response.code} - ${response.body?.string()}")
                }

                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving notification to database", e)
            }
        }
    }
    private fun showNotification(
        title: String?,
        body: String?,
        channelId: String = "stylu_general"
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels(notificationManager)
        }

        // Create intent to open app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title ?: "Stylu")
            .setContentText(body ?: "New notification")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        // Show notification
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannels(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    "stylu_general",
                    "General Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "General app notifications"
                    enableLights(true)
                    enableVibration(true)
                },
                NotificationChannel(
                    "stylu_drops",
                    "New Drops",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new fashion drops"
                    enableLights(true)
                    enableVibration(true)
                },
                NotificationChannel(
                    "stylu_social",
                    "Social Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Likes, comments, and social interactions"
                    enableLights(true)
                },
                NotificationChannel(
                    "stylu_reminders",
                    "Outfit Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily outfit reminders"
                }
            )

            channels.forEach { notificationManager.createNotificationChannel(it) }
        }
    }

    companion object {
        /**
         * Call this method when user logs out to unregister token
         */
        fun unregisterToken(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                    val authToken = prefs.getString("access_token", null) ?: return@launch

                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url("https://stylu-api-x69c.onrender.com/api/PushNotification/unregister")
                        .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
                        .addHeader("Authorization", "Bearer $authToken")
                        .build()

                    val response = client.newCall(request).execute()
                    response.close()

                    Log.d("FCM_Service", "Token unregistered on logout")
                } catch (e: Exception) {
                    Log.e("FCM_Service", "Error unregistering token", e)
                }
            }
        }

        /**
         * Call this method after successful login to register token
         */
        fun registerTokenAfterLogin(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                    val fcmToken = prefs.getString("fcm_token", null)
                    val authToken = prefs.getString("access_token", null)

                    if (fcmToken == null || authToken == null) {
                        Log.w("FCM_Service", "Missing FCM or auth token")
                        return@launch
                    }

                    val json = JSONObject().apply {
                        put("fcmToken", fcmToken)
                        put("platform", "android")
                    }

                    val body = json.toString()
                        .toRequestBody("application/json".toMediaTypeOrNull())

                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url("https://stylu-api-x69c.onrender.com/api/PushNotification/register")
                        .post(body)
                        .addHeader("Authorization", "Bearer $authToken")
                        .addHeader("Content-Type", "application/json")
                        .build()

                    val response = client.newCall(request).execute()
                    response.close()

                    Log.d("FCM_Service", "Token registered after login")
                } catch (e: Exception) {
                    Log.e("FCM_Service", "Error registering token after login", e)
                }
            }
        }
    }
}