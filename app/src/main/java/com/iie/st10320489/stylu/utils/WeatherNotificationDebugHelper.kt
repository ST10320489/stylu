package com.iie.st10320489.stylu.utils

import android.content.Context
import android.util.Log
import androidx.work.*
import com.iie.st10320489.stylu.workers.WeatherNotificationWorker
import java.util.concurrent.TimeUnit

/**
 * Debug helper class to test weather notifications
 * Use this in development to trigger notifications immediately
 */
object WeatherNotificationDebugHelper {

    private const val TAG = "WeatherDebugHelper"
    private const val TEST_WORK_NAME = "test_weather_notification"

    /**
     * Trigger a weather notification immediately for testing
     * Call this from your UI to test the notification flow
     */
    fun triggerTestNotification(context: Context) {
        Log.d(TAG, "🧪 Triggering test weather notification...")

        // Create constraints
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create one-time work request with minimal delay
        val workRequest = OneTimeWorkRequestBuilder<WeatherNotificationWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS) // 5 second delay
            .setConstraints(constraints)
            .addTag("test_weather")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            TEST_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        Log.d(TAG, "✅ Test notification scheduled for 5 seconds from now")
    }

    /**
     * Check if test work is running
     */
    fun isTestWorkRunning(context: Context): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(TEST_WORK_NAME)
            .get()

        val isRunning = workInfos.any { workInfo ->
            workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING
        }

        Log.d(TAG, "Test work running: $isRunning")
        return isRunning
    }

    /**
     * Cancel test notification
     */
    fun cancelTestNotification(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TEST_WORK_NAME)
        Log.d(TAG, "Test notification cancelled")
    }
}