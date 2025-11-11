package com.iie.st10320489.stylu.data.local.dao

import androidx.room.*
import com.iie.st10320489.stylu.data.local.entities.CalendarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    // Get all scheduled outfits as Flow for real-time updates
    @Query("SELECT * FROM scheduled_outfits ORDER BY scheduledDate ASC")
    fun getAllScheduledOutfitsFlow(): Flow<List<CalendarEntity>>

    // Get all scheduled outfits
    @Query("SELECT * FROM scheduled_outfits ORDER BY scheduledDate ASC")
    suspend fun getAllScheduledOutfits(): List<CalendarEntity>

    // Get scheduled outfits for a specific date range
    @Query("SELECT * FROM scheduled_outfits WHERE scheduledDate BETWEEN :startDate AND :endDate ORDER BY scheduledDate ASC")
    suspend fun getScheduledOutfitsByDateRange(startDate: Long, endDate: Long): List<CalendarEntity>

    // Get scheduled outfits for a specific date range as Flow
    @Query("SELECT * FROM scheduled_outfits WHERE scheduledDate BETWEEN :startDate AND :endDate ORDER BY scheduledDate ASC")
    fun getScheduledOutfitsByDateRangeFlow(startDate: Long, endDate: Long): Flow<List<CalendarEntity>>

    // Get a single scheduled outfit by ID
    @Query("SELECT * FROM scheduled_outfits WHERE scheduleId = :scheduleId LIMIT 1")
    suspend fun getScheduledOutfitById(scheduleId: Int): CalendarEntity?

    // Get all dates that have scheduled outfits
    @Query("SELECT DISTINCT scheduledDate FROM scheduled_outfits ORDER BY scheduledDate ASC")
    suspend fun getAllScheduledDates(): List<Long>

    // Get all dates that have scheduled outfits as Flow
    @Query("SELECT DISTINCT scheduledDate FROM scheduled_outfits ORDER BY scheduledDate ASC")
    fun getAllScheduledDatesFlow(): Flow<List<Long>>

    // Insert a scheduled outfit
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledOutfit(scheduledOutfit: CalendarEntity)

    // Insert multiple scheduled outfits
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledOutfits(scheduledOutfits: List<CalendarEntity>)

    // Update a scheduled outfit
    @Update
    suspend fun updateScheduledOutfit(scheduledOutfit: CalendarEntity)

    // Delete a scheduled outfit by ID
    @Query("DELETE FROM scheduled_outfits WHERE scheduleId = :scheduleId")
    suspend fun deleteScheduledOutfit(scheduleId: Int)

    // Delete all scheduled outfits
    @Query("DELETE FROM scheduled_outfits")
    suspend fun deleteAllScheduledOutfits()

    // Count scheduled outfits
    @Query("SELECT COUNT(*) FROM scheduled_outfits")
    suspend fun getScheduledOutfitsCount(): Int

    // Get scheduled outfits for a specific outfit ID
    @Query("SELECT * FROM scheduled_outfits WHERE outfitId = :outfitId ORDER BY scheduledDate ASC")
    suspend fun getScheduledOutfitsByOutfitId(outfitId: Int): List<CalendarEntity>
}