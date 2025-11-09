package com.iie.st10320489.stylu.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.iie.st10320489.stylu.MainActivity
import com.iie.st10320489.stylu.R

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "weather_notifications"
        private const val CHANNEL_NAME = "Weather Notifications"
        private const val NOTIFICATION_ID = 1001
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
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your weather icon
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