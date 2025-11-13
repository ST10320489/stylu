package com.iie.st10320489.stylu.workers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.iie.st10320489.stylu.data.models.notifications.Notification
import com.iie.st10320489.stylu.repository.NotificationRepository
import com.iie.st10320489.stylu.repository.WeatherRepository
import com.iie.st10320489.stylu.utils.LocationHelper
import com.iie.st10320489.stylu.utils.NotificationHelper
import com.iie.st10320489.stylu.utils.NotificationPreferences
import com.iie.st10320489.stylu.utils.WorkManagerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WeatherNotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WeatherNotificationWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🌤️ WeatherNotificationWorker started")

            val notificationPrefs = NotificationPreferences(context)

            // Check if notifications are enabled
            if (!notificationPrefs.isWeatherNotificationEnabled()) {
                Log.d(TAG, "Weather notifications are disabled, skipping")
                return@withContext Result.success()
            }

            // Check if notification was already sent today
            if (notificationPrefs.wasNotificationSentToday()) {
                Log.d(TAG, "Notification already sent today, skipping")
                // Schedule for tomorrow
                scheduleNextNotification()
                return@withContext Result.success()
            }

            // Get user's location
            val locationHelper = LocationHelper(context)
            val (latitude, longitude) = locationHelper.getCurrentLocation()

            Log.d(TAG, "Got location: lat=$latitude, lon=$longitude")

            // Fetch weather forecast
            val weatherRepository = WeatherRepository()
            val weatherResult = weatherRepository.getWeeklyForecast(
                latitude = latitude,
                longitude = longitude
            )

            weatherResult.onSuccess { forecast ->
                if (forecast.isNotEmpty()) {
                    val todayWeather = forecast[0]

                    Log.d(TAG, "Today's weather: High=${todayWeather.maxTemp}°C, Low=${todayWeather.minTemp}°C, Condition=${todayWeather.condition}")

                    // Show notification
                    val notificationHelper = NotificationHelper(context)
                    notificationHelper.showWeatherNotification(
                        highTemp = todayWeather.maxTemp,
                        lowTemp = todayWeather.minTemp,
                        condition = todayWeather.condition
                    )

                    // ✅ NEW: Save notification to database
                    saveNotificationToDatabase(
                        highTemp = todayWeather.maxTemp,
                        lowTemp = todayWeather.minTemp,
                        condition = todayWeather.condition
                    )

                    // Mark notification as sent today
                    notificationPrefs.markNotificationSentToday()

                    Log.d(TAG, "✅ Weather notification sent and saved successfully")

                    // Broadcast to refresh notifications list
                    val intent = Intent("com.iie.st10320489.stylu.NEW_NOTIFICATION")
                    context.sendBroadcast(intent)

                } else {
                    Log.w(TAG, "No weather data available")
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to fetch weather: ${error.message}", error)
                // Don't retry immediately, wait for next scheduled time
            }

            // Schedule next notification for tomorrow at same time
            scheduleNextNotification()

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error in WeatherNotificationWorker: ${e.message}", e)
            // Schedule next notification even on error
            scheduleNextNotification()
            Result.failure()
        }
    }

    /**
     * Save weather notification to database
     */
    private suspend fun saveNotificationToDatabase(
        highTemp: Int,
        lowTemp: Int,
        condition: String
    ) {
        try {
            val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
            val userId = prefs.getString("user_id", null)  // ✅ UUID string

            if (userId == null) {
                Log.e(TAG, "User ID not found, cannot save notification")
                return
            }

            // Get current timestamp in ISO 8601 format
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

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

            // Create notification object using UUID
            val notification = Notification(
                userId = userId.hashCode(),  // ✅ For model compatibility
                title = "Today's Weather Forecast $emoji",
                message = "High: $highTemp°C, Low: $lowTemp°C - $conditionText conditions expected today",
                type = "weather",
                scheduledAt = timestamp,
                sentAt = timestamp,
                status = "sent"
            )

            // Save using repository (which now uses UUID)
            val notificationRepository = NotificationRepository(context)
            val saved = notificationRepository.saveNotification(notification)

            if (saved) {
                Log.d(TAG, "✅ Weather notification saved to database")
            } else {
                Log.e(TAG, "❌ Failed to save weather notification to database")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving notification to database: ${e.message}", e)
        }
    }

    private fun scheduleNextNotification() {
        try {
            val notificationPrefs = NotificationPreferences(context)
            val reminderTime = notificationPrefs.getReminderTime()

            Log.d(TAG, "Scheduling next notification for tomorrow at $reminderTime")

            val scheduler = WorkManagerScheduler(context)
            scheduler.scheduleWeatherNotification(reminderTime)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule next notification: ${e.message}", e)
        }
    }
}