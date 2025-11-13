package com.iie.st10320489.stylu.workers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.iie.st10320489.stylu.repository.WeatherRepository
import com.iie.st10320489.stylu.utils.LocationHelper
import com.iie.st10320489.stylu.utils.NotificationHelper
import com.iie.st10320489.stylu.utils.NotificationPreferences
import com.iie.st10320489.stylu.utils.WorkManagerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

            // ✅ FIXED: Check if already sent for CURRENT scheduled time
            if (notificationPrefs.wasNotificationSentForCurrentTime()) {
                Log.d(TAG, "Already sent notification for current scheduled time")
                // Schedule for tomorrow at same time
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

                    // ✅ Show notification (NotificationHelper now saves to Supabase automatically)
                    val notificationHelper = NotificationHelper(context)
                    notificationHelper.showWeatherNotification(
                        highTemp = todayWeather.maxTemp,
                        lowTemp = todayWeather.minTemp,
                        condition = todayWeather.condition
                    )

                    // ✅ FIXED: Mark notification as sent for CURRENT time
                    notificationPrefs.markNotificationSentNow()

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