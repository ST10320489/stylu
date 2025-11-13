package com.iie.st10320489.stylu.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.iie.st10320489.stylu.MainActivity
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NotificationHelper(private val context: Context) {

    companion object {
        private const val TAG = "NotificationHelper"
        private const val CHANNEL_ID = "weather_notifications"
        private const val CHANNEL_NAME = "Weather Notifications"
        private const val NOTIFICATION_ID = 1001
    }

    private val repository = NotificationRepository(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily weather forecast notifications"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * ✅ FIXED: Now saves to Supabase before showing notification
     */
    fun showWeatherNotification(highTemp: Int, lowTemp: Int, condition: String) {
        // Map condition to emoji
        val emoji = when (condition) {
            "sun" -> "☀️"
            "cloudy" -> "☁️"
            "rain" -> "🌧️"
            "thunder" -> "⛈️"
            else -> "🌤️"
        }

        val conditionText = when (condition) {
            "sun" -> "Sunny"
            "cloudy" -> "Cloudy"
            "rain" -> "Rainy"
            "thunder" -> "Thunderstorm"
            else -> "Partly Cloudy"
        }

        val title = "Today's Weather Forecast $emoji"
        val message = "High: $highTemp°C, Low: $lowTemp°C - $conditionText"

        // ✅ SAVE TO SUPABASE FIRST
        saveWeatherNotificationToDatabase(title, message, highTemp, lowTemp, condition)

        // Then show the notification
        displayNotification(title, message)
    }

    /**
     * ✅ NEW: Save weather notification to Supabase
     */
    private fun saveWeatherNotificationToDatabase(
        title: String,
        message: String,
        highTemp: Int,
        lowTemp: Int,
        condition: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                val userId = prefs.getString("user_id", null)

                if (userId == null) {
                    Log.e(TAG, "❌ Cannot save weather notification: User ID not found")
                    return@launch
                }

                val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                // Create notification data with weather details
                val notificationData = mapOf(
                    "highTemp" to highTemp,
                    "lowTemp" to lowTemp,
                    "condition" to condition
                )

                val success = repository.saveNotificationDirect(
                    userId = userId,
                    title = title,
                    message = message,
                    type = "weather",
                    data = notificationData,
                    timestamp = timestamp
                )

                if (success) {
                    Log.d(TAG, "✅ Weather notification saved to database")
                    // Notify UI to refresh
                    context.sendBroadcast(Intent("com.iie.st10320489.stylu.NEW_NOTIFICATION"))
                } else {
                    Log.e(TAG, "❌ Failed to save weather notification")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving weather notification: ${e.message}", e)
            }
        }
    }


    private fun displayNotification(title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}