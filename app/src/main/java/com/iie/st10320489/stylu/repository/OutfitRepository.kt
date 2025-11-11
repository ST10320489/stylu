package com.iie.st10320489.stylu.repository

import android.content.Context
import android.util.Log
import com.iie.st10320489.stylu.data.local.StyluDatabase
import com.iie.st10320489.stylu.data.local.entities.OutfitEntity
import com.iie.st10320489.stylu.data.local.entities.OutfitItemEntity
import com.iie.st10320489.stylu.data.models.outfit.Outfit
import com.iie.st10320489.stylu.data.models.outfit.OutfitItemDetail
import com.iie.st10320489.stylu.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.*

class OutfitRepository(private val context: Context) {

    private val database = StyluDatabase.getDatabase(context)
    private val outfitDao = database.outfitDao()
    private val apiService = ApiService(context)
    private val authRepository = AuthRepository(context)

    companion object {
        private const val TAG = "OutfitRepository"
    }

    // Get all outfits from cache as Flow
    fun getAllOutfits(): Flow<List<Outfit>> {
        Log.d(TAG, "getAllOutfits() called")
        return outfitDao.getAllOutfitsFlow().map { entities ->
            Log.d(TAG, "getAllOutfits: Retrieved ${entities.size} entities from database")
            entities.mapIndexed { index, entity ->
                Log.d(TAG, "getAllOutfits: Entity $index - ID: ${entity.outfitId}, Name: ${entity.name}, Schedule: ${entity.scheduledDate}")
                val items = outfitDao.getOutfitLayout(entity.outfitId)
                entity.toOutfit(items)
            }
        }
    }

    // Get outfits for a specific date
    fun getOutfitsByDate(date: String): Flow<List<Outfit>> {
        Log.d(TAG, "getOutfitsByDate() called with date: $date")
        return outfitDao.getAllOutfitsFlow().map { entities ->
            val filtered = entities.filter { it.scheduledDate == date }
            Log.d(TAG, "getOutfitsByDate: Found ${filtered.size} outfits for date $date")
            filtered.forEach { entity ->
                Log.d(TAG, "  - Outfit: ${entity.name}, Schedule: ${entity.scheduledDate}")
            }
            filtered.map { entity ->
                val items = outfitDao.getOutfitLayout(entity.outfitId)
                entity.toOutfit(items)
            }
        }
    }

    // Get all scheduled outfits
    fun getScheduledOutfits(): Flow<List<Outfit>> {
        Log.d(TAG, "getScheduledOutfits() called")
        return outfitDao.getAllOutfitsFlow().map { entities ->
            val scheduled = entities.filter { it.scheduledDate != null }
            Log.d(TAG, "getScheduledOutfits: Found ${scheduled.size} scheduled outfits")
            scheduled.forEach { entity ->
                Log.d(TAG, "  - ${entity.name}: ${entity.scheduledDate}")
            }
            scheduled.map { entity ->
                val items = outfitDao.getOutfitLayout(entity.outfitId)
                entity.toOutfit(items)
            }
        }
    }

    // Get scheduled dates for calendar indicators
    fun getScheduledDates(): Flow<List<String>> {
        Log.d(TAG, "getScheduledDates() called")
        return outfitDao.getScheduledDatesFlow().map { dates ->
            val filtered = dates.filterNotNull()
            Log.d(TAG, "getScheduledDates: Found ${filtered.size} scheduled dates: $filtered")
            filtered
        }
    }

    // Sync with server
    suspend fun syncOutfits(): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "syncOutfits() started")
        try {
            val result = apiService.getUserOutfits()

            result.onSuccess { apiOutfits ->
                Log.d(TAG, "syncOutfits: Received ${apiOutfits.size} outfits from API")

                // Convert API response to entities and save
                val entities = apiOutfits.mapIndexed { index, apiOutfit ->
                    Log.d(TAG, "syncOutfits: Processing outfit $index - ID: ${apiOutfit.outfitId}, Name: ${apiOutfit.name}, Schedule: ${apiOutfit.schedule}")
                    OutfitEntity(
                        outfitId = apiOutfit.outfitId,
                        userId = apiOutfit.userId,
                        name = apiOutfit.name,
                        category = apiOutfit.category,
                        scheduledDate = apiOutfit.schedule,
                        createdAt = apiOutfit.createdAt,
                        updatedAt = Calendar.getInstance().time.toString()
                    )
                }

                Log.d(TAG, "syncOutfits: Inserting ${entities.size} entities into database")
                outfitDao.insertOutfits(entities)

                // Save outfit items
                apiOutfits.forEach { apiOutfit ->
                    val items = apiOutfit.items.mapIndexed { index, item ->
                        OutfitItemEntity(
                            outfitId = apiOutfit.outfitId,
                            itemId = item.itemId,
                            x = item.layoutData?.x ?: 0f,
                            y = item.layoutData?.y ?: 0f,
                            scale = item.layoutData?.scale ?: 1f,
                            width = item.layoutData?.width ?: 0,
                            height = item.layoutData?.height ?: 0,
                            rotation = 0f,
                            zIndex = index
                        )
                    }
                    outfitDao.insertOutfitItems(items)
                }

                Log.d(TAG, "syncOutfits: Successfully synced ${entities.size} outfits")
            }

            result.onFailure { error ->
                Log.e(TAG, "syncOutfits: Failed - ${error.message}", error)
            }

            return@withContext result.map { Unit }
        } catch (e: Exception) {
            Log.e(TAG, "syncOutfits: Exception occurred", e)
            return@withContext Result.failure(e)
        }
    }

    // Create new outfit
    suspend fun createOutfit(
        name: String,
        category: String,
        items: List<String>,
        schedule: String? = null
    ): Result<Outfit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "createOutfit() called")
        Log.d(TAG, "  Name: $name")
        Log.d(TAG, "  Category: $category")
        Log.d(TAG, "  Items: $items")
        Log.d(TAG, "  Schedule: $schedule")

        try {
            val itemIds = items.mapNotNull { it.toIntOrNull() }
            Log.d(TAG, "  Converted item IDs: $itemIds")

            // Create via API
            val request = ApiService.CreateOutfitRequest(
                name = name,
                category = category,
                itemIds = itemIds
            )

            val result = apiService.createOutfit(request)

            result.onSuccess { response ->
                Log.d(TAG, "createOutfit: API success - Outfit ID: ${response.outfitId}")

                // Save to local database
                val entity = OutfitEntity(
                    outfitId = response.outfitId,
                    userId = authRepository.getCurrentUserId() ?: "",
                    name = response.name,
                    category = response.category,
                    scheduledDate = schedule,
                    createdAt = response.createdAt,
                    updatedAt = Calendar.getInstance().time.toString()
                )

                Log.d(TAG, "createOutfit: Inserting entity with schedule: ${entity.scheduledDate}")
                outfitDao.insertOutfit(entity)

                // Verify it was saved
                val saved = outfitDao.getOutfitById(response.outfitId)
                Log.d(TAG, "createOutfit: Verification - Saved outfit schedule: ${saved?.scheduledDate}")
            }

            result.onFailure { error ->
                Log.e(TAG, "createOutfit: API failed - ${error.message}", error)
            }

            return@withContext result.map { response ->
                Outfit(
                    outfitId = response.outfitId,
                    userId = authRepository.getCurrentUserId() ?: "",
                    name = response.name,
                    category = response.category,
                    items = emptyList(),
                    schedule = schedule,
                    createdAt = response.createdAt
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "createOutfit: Exception occurred", e)
            Result.failure(e)
        }
    }

    // Update outfit schedule
    suspend fun updateOutfitSchedule(outfitId: String, schedule: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "updateOutfitSchedule() called")
            Log.d(TAG, "  Outfit ID: $outfitId")
            Log.d(TAG, "  New Schedule: $schedule")

            try {
                val id = outfitId.toIntOrNull()
                if (id == null) {
                    Log.e(TAG, "updateOutfitSchedule: Invalid outfit ID")
                    return@withContext Result.failure(Exception("Invalid outfit ID"))
                }

                if (schedule != null) {
                    Log.d(TAG, "updateOutfitSchedule: Calling API to schedule outfit")
                    // Update via API
                    val result = apiService.scheduleOutfit(id, schedule)

                    result.onSuccess {
                        Log.d(TAG, "updateOutfitSchedule: API success, updating local database")
                        // Update local database
                        outfitDao.updateScheduledDate(
                            id,
                            schedule,
                            Calendar.getInstance().time.toString()
                        )

                        // Verify
                        val updated = outfitDao.getOutfitById(id)
                        Log.d(TAG, "updateOutfitSchedule: Verification - Updated schedule: ${updated?.scheduledDate}")
                    }

                    result.onFailure { error ->
                        Log.e(TAG, "updateOutfitSchedule: API failed - ${error.message}", error)
                    }

                    return@withContext result.map { Unit }
                } else {
                    Log.d(TAG, "updateOutfitSchedule: Removing schedule from local database")
                    outfitDao.updateScheduledDate(
                        id,
                        null,
                        Calendar.getInstance().time.toString()
                    )

                    // Verify
                    val updated = outfitDao.getOutfitById(id)
                    Log.d(TAG, "updateOutfitSchedule: Verification - Schedule removed: ${updated?.scheduledDate}")

                    return@withContext Result.success(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateOutfitSchedule: Exception occurred", e)
                Result.failure(e)
            }
        }

    // Delete outfit
    suspend fun deleteOutfit(outfitId: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteOutfit() called for ID: $outfitId")
        try {
            val id = outfitId.toIntOrNull() ?: return@withContext Result.failure(
                Exception("Invalid outfit ID")
            )

            // Delete via API
            val result = apiService.deleteOutfit(id)

            result.onSuccess {
                Log.d(TAG, "deleteOutfit: API success, deleting from local database")
                // Delete from local database
                outfitDao.deleteOutfitComplete(id)
                Log.d(TAG, "deleteOutfit: Successfully deleted outfit $id")
            }

            result.onFailure { error ->
                Log.e(TAG, "deleteOutfit: Failed - ${error.message}", error)
            }

            return@withContext result.map { Unit }
        } catch (e: Exception) {
            Log.e(TAG, "deleteOutfit: Exception occurred", e)
            Result.failure(e)
        }
    }

    private suspend fun OutfitEntity.toOutfit(itemEntities: List<OutfitItemEntity>): Outfit {
        val items = itemEntities.map { itemEntity ->
            OutfitItemDetail(
                itemId = itemEntity.itemId,
                name = null,
                imageUrl = "",
                colour = null,
                subcategory = ""
            )
        }

        return Outfit(
            outfitId = this.outfitId,
            userId = this.userId,
            name = this.name,
            category = this.category,
            items = items,
            schedule = this.scheduledDate,
            createdAt = this.createdAt
        )
    }
}