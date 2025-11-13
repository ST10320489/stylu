package com.iie.st10320489.stylu.utils

import android.content.Context
import android.util.Log
import androidx.work.*
import com.iie.st10320489.stylu.workers.WeatherNotificationWorker
import java.util.*
import java.util.concurrent.TimeUnit

class WorkManagerScheduler(private val context: Context) {

    companion object {
        private const val TAG = "WorkManagerScheduler"
        private const val WEATHER_WORK_NAME = "weather_notification_work"
    }

    /**
     * Schedule weather notification at specified time (format: "HH:mm")
     */
    fun scheduleWeatherNotification(time: String) {
        try {
            Log.d(TAG, "Scheduling weather notification for $time")

            // Calculate delay until next occurrence of specified time
            val delayMillis = calculateDelayUntilTime(time)
            val delayMinutes = TimeUnit.MILLISECONDS.toMinutes(delayMillis)

            Log.d(TAG, "Delay calculated: $delayMinutes minutes ($delayMillis ms)")

            // Create constraints
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Create one-time work request
            val workRequest = OneTimeWorkRequestBuilder<WeatherNotificationWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(WEATHER_WORK_NAME)
                .build()

            // Schedule work (replace existing work with same name)
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WEATHER_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            Log.d(TAG, "Weather notification scheduled successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule weather notification: ${e.message}", e)
        }
    }

    /**
     * Cancel scheduled weather notification
     */
    fun cancelWeatherNotification() {
        try {
            Log.d(TAG, "Cancelling weather notification")
            WorkManager.getInstance(context).cancelUniqueWork(WEATHER_WORK_NAME)
            Log.d(TAG, "Weather notification cancelled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel weather notification: ${e.message}", e)
        }
    }


    fun rescheduleWeatherNotification(newTime: String) {
        Log.d(TAG, "Rescheduling weather notification to $newTime")
        cancelWeatherNotification()
        scheduleWeatherNotification(newTime)
    }

    /**
     * Calculate delay in milliseconds until next occurrence of specified time
     * Format: "HH:mm" (24-hour format)
     */
    private fun calculateDelayUntilTime(time: String): Long {
        val parts = time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        val now = Calendar.getInstance()
        val scheduledTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If scheduled time has already passed today, schedule for tomorrow
        if (scheduledTime.timeInMillis <= now.timeInMillis) {
            scheduledTime.add(Calendar.DAY_OF_MONTH, 1)
            Log.d(TAG, "Scheduled time has passed today, scheduling for tomorrow")
        }

        val delay = scheduledTime.timeInMillis - now.timeInMillis

        Log.d(TAG, "Current time: ${now.time}")
        Log.d(TAG, "Scheduled time: ${scheduledTime.time}")
        Log.d(TAG, "Delay: $delay ms")

        return delay
    }

    /**
     * Check if weather notification work is scheduled
     */
    fun isWorkScheduled(): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WEATHER_WORK_NAME)
            .get()

        val isScheduled = workInfos.any { workInfo ->
            workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING
        }

        Log.d(TAG, "Work scheduled: $isScheduled")
        return isScheduled
    }
}