package com.iie.st10320489.stylu.sync

import android.content.Context
import android.util.Log
import com.iie.st10320489.stylu.data.local.StyluDatabase
import com.iie.st10320489.stylu.repository.CalendarRepository
import com.iie.st10320489.stylu.repository.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*


class CalendarSyncService(
    private val context: Context,
    private val calendarRepository: CalendarRepository? = null,
    private val tokenManager: TokenManager? = null,
    private val database: StyluDatabase? = null
) {

    // Use provided dependencies if available, otherwise create defaults
    private val repo: CalendarRepository = calendarRepository ?: CalendarRepository(context)
    private val db: StyluDatabase = database ?: StyluDatabase.getDatabase(context)
    private val token: TokenManager = tokenManager ?: TokenManager(context)

    companion object {
        private const val TAG = "CalendarSyncService"
    }

    /**
     * Sync all pending calendar changes with API
     */
    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val tokenValue = token.getValidAccessToken().getOrNull()
            if (tokenValue == null) {
                Log.w(TAG, "⚠️ No auth token - skipping sync")
                return@withContext SyncResult(false, "Not authenticated", 0, 0)
            }

            var syncedCount = 0
            var failedCount = 0

            val allSchedules = db.calendarDao().getAllScheduledOutfits()
            val pendingSchedules = allSchedules.filter { it.scheduleId < 0 }

            for (schedule in pendingSchedules) {
                try {
                    val result = repo.scheduleOutfit(
                        outfitId = schedule.outfitId,
                        date = Date(schedule.scheduledDate),
                        eventName = schedule.eventName,
                        notes = schedule.notes
                    )
                    if (result.isSuccess) syncedCount++ else failedCount++
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "Error syncing schedule: ${schedule.scheduleId}", e)
                }
            }

            try {
                val now = Date()
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MONTH, -1)
                val startDate = calendar.time
                calendar.time = now
                calendar.add(Calendar.MONTH, 3)
                val endDate = calendar.time
                repo.getScheduledOutfits(startDate, endDate)
            } catch (e: Exception) {
                Log.w(TAG, "Could not sync down from API", e)
            }

            val message = when {
                failedCount == 0 && syncedCount > 0 -> "Synced $syncedCount schedules"
                failedCount > 0 && syncedCount > 0 -> "Synced $syncedCount, failed $failedCount"
                failedCount > 0 -> "Failed to sync $failedCount schedules"
                else -> "Everything is up to date"
            }

            SyncResult(failedCount == 0, message, syncedCount, failedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            SyncResult(false, "Sync error: ${e.message}", 0, 0)
        }
    }

    /**
     * Check if there are pending syncs
     */
    suspend fun hasPendingSyncs(): Boolean = withContext(Dispatchers.IO) {
        try {
            val allSchedules = db.calendarDao().getAllScheduledOutfits()
            allSchedules.count { it.scheduleId < 0 } > 0
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
