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
import com.iie.st10320489.stylu.data.models.notifications.Notification
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

        /**
         * Save a local notification (not from push)
         */
        fun saveLocalNotification(
            context: Context,
            title: String,
            message: String,
            type: String = "general"
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                    val userId = prefs.getString("user_id", null)

                    if (userId == null) {
                        Log.e(TAG, "Cannot save notification: User ID not found")
                        return@launch
                    }

                    val timestamp = SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss",
                        Locale.US
                    ).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date())

                    val notification = Notification(
                        userId = userId.hashCode(),
                        title = title,
                        message = message,
                        type = type,
                        scheduledAt = timestamp,
                        sentAt = timestamp,
                        status = "sent"
                    )

                    val repository = NotificationRepository(context)
                    val success = repository.saveNotification(notification)

                    if (success) {
                        Log.d(TAG, "Local notification saved")
                        val intent = Intent("com.iie.st10320489.stylu.NEW_NOTIFICATION")
                        context.sendBroadcast(intent)
                    } else {
                        Log.e(TAG, "Failed to save local notification")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error saving local notification: ${e.message}", e)
                }
            }
        }
    }

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

    fun showWeatherNotification(highTemp: Int, lowTemp: Int, condition: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Today's Weather Forecast $emoji")
            .setContentText("High: $highTemp°C, Low: $lowTemp°C - $conditionText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("High: $highTemp°C, Low: $lowTemp°C\n$conditionText conditions expected today")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}