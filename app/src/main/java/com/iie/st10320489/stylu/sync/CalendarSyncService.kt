package com.iie.st10320489.stylu.sync

import android.content.Context
import android.util.Log
import com.iie.st10320489.stylu.data.local.StyluDatabase
import com.iie.st10320489.stylu.repository.CalendarRepository
import com.iie.st10320489.stylu.repository.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * ✅ Background sync service for calendar schedules
 * Call this when:
 * - App starts
 * - Network connectivity is restored
 * - User pulls to refresh
 */
class CalendarSyncService(private val context: Context) {

    private val calendarRepository = CalendarRepository(context)
    private val database = StyluDatabase.getDatabase(context)
    private val tokenManager = TokenManager(context)

    companion object {
        private const val TAG = "CalendarSyncService"
    }

    /**
     * Sync all pending calendar changes with API
     */
    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        try {
            // Check if we have auth token
            val token = tokenManager.getValidAccessToken().getOrNull()
            if (token == null) {
                Log.w(TAG, "⚠️ No auth token - skipping sync")
                return@withContext SyncResult(
                    success = false,
                    message = "Not authenticated",
                    synced = 0,
                    failed = 0
                )
            }

            Log.d(TAG, "🔄 Starting calendar sync...")

            var syncedCount = 0
            var failedCount = 0

            // Get all schedules with negative IDs (pending sync)
            val allSchedules = database.calendarDao().getAllScheduledOutfits()
            val pendingSchedules = allSchedules.filter { it.scheduleId < 0 }

            Log.d(TAG, "📦 Found ${pendingSchedules.size} pending schedules to sync")

            // Sync each pending schedule
            for (schedule in pendingSchedules) {
                try {
                    // Re-schedule with API
                    val result = calendarRepository.scheduleOutfit(
                        outfitId = schedule.outfitId,
                        date = Date(schedule.scheduledDate),
                        eventName = schedule.eventName,
                        notes = schedule.notes
                    )

                    if (result.isSuccess) {
                        syncedCount++
                        Log.d(TAG, "✅ Synced schedule: ${schedule.scheduleId}")
                    } else {
                        failedCount++
                        Log.w(TAG, "❌ Failed to sync schedule: ${schedule.scheduleId}")
                    }
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "❌ Error syncing schedule: ${schedule.scheduleId}", e)
                }
            }

            // Sync down latest schedules from API
            try {
                val now = Date()
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MONTH, -1) // Last month
                val startDate = calendar.time

                calendar.time = now
                calendar.add(Calendar.MONTH, 3) // Next 3 months
                val endDate = calendar.time

                calendarRepository.getScheduledOutfits(startDate, endDate)
                Log.d(TAG, "✅ Synced down schedules from API")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Could not sync down from API", e)
            }

            val message = when {
                failedCount == 0 && syncedCount > 0 -> "✅ Synced $syncedCount schedules"
                failedCount > 0 && syncedCount > 0 -> "⚠️ Synced $syncedCount, failed $failedCount"
                failedCount > 0 -> "❌ Failed to sync $failedCount schedules"
                else -> "✅ Everything is up to date"
            }

            Log.d(TAG, "🏁 Sync complete: $message")

            SyncResult(
                success = failedCount == 0,
                message = message,
                synced = syncedCount,
                failed = failedCount
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync failed", e)
            SyncResult(
                success = false,
                message = "Sync error: ${e.message}",
                synced = 0,
                failed = 0
            )
        }
    }

    /**
     * Check if there are pending syncs
     */
    suspend fun hasPendingSyncs(): Boolean = withContext(Dispatchers.IO) {
        try {
            val allSchedules = database.calendarDao().getAllScheduledOutfits()
            val pendingCount = allSchedules.count { it.scheduleId < 0 }
            Log.d(TAG, "📊 Pending syncs: $pendingCount")
            pendingCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking pending syncs", e)
            false
        }
    }

    data class SyncResult(
        val success: Boolean,
        val message: String,
        val synced: Int,
        val failed: Int
    )
}