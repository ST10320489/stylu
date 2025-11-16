package com.iie.st10320489.stylu.repository

import android.content.Context
import android.util.Log
import com.iie.st10320489.stylu.data.models.outfit.Outfit
import com.iie.st10320489.stylu.data.models.outfit.OutfitItemDetail
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.utils.SnapshotManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * ✅ SIMPLIFIED: API-Only Repository (No Complex Caching)
 * - Direct API calls only
 * - Snapshot management only
 * - Much simpler and more reliable
 */
class OutfitRepository(private val context: Context) {

    private val apiService = ApiService(context)
    private val authRepository = AuthRepository(context)

    companion object {
        private const val TAG = "OutfitRepository"
    }

    /**
     * ✅ Get all outfits directly from API
     */
    suspend fun getAllOutfits(): Result<List<Outfit>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getAllOutfits() - fetching from API")

        return@withContext try {
            val result = apiService.getUserOutfits()

            result.map { apiOutfits ->
                Log.d(TAG, "✅ API returned ${apiOutfits.size} outfits")

                apiOutfits.map { apiOutfit ->
                    Outfit(
                        outfitId = apiOutfit.outfitId,
                        userId = apiOutfit.userId,
                        name = apiOutfit.name,
                        category = apiOutfit.category,
                        items = apiOutfit.items.map { item ->
                            OutfitItemDetail(
                                itemId = item.itemId,
                                name = item.name,
                                imageUrl = item.imageUrl,
                                colour = item.colour,
                                subcategory = item.subcategory,
                                layoutData = item.layoutData?.let {
                                    com.iie.st10320489.stylu.network.ApiService.ItemLayoutData(
                                        x = it.x,
                                        y = it.y,
                                        scale = it.scale,
                                        width = it.width,
                                        height = it.height
                                    )
                                }
                            )
                        },
                        schedule = apiOutfit.schedule,
                        createdAt = apiOutfit.createdAt
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading outfits", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ Get single outfit by ID
     */
    suspend fun getOutfitById(outfitId: Int): Result<Outfit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = apiService.getUserOutfits()
            result.map { outfits ->
                outfits.find { it.outfitId == outfitId }?.let { apiOutfit ->
                    Outfit(
                        outfitId = apiOutfit.outfitId,
                        userId = apiOutfit.userId,
                        name = apiOutfit.name,
                        category = apiOutfit.category,
                        items = apiOutfit.items.map { item ->
                            OutfitItemDetail(
                                itemId = item.itemId,
                                name = item.name,
                                imageUrl = item.imageUrl,
                                colour = item.colour,
                                subcategory = item.subcategory,
                                layoutData = item.layoutData
                            )
                        },
                        schedule = apiOutfit.schedule,
                        createdAt = apiOutfit.createdAt
                    )
                } ?: throw Exception("Outfit not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading outfit $outfitId", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ Create outfit with layout
     */
    suspend fun createOutfitWithLayout(
        name: String,
        category: String,
        items: List<String>,
        schedule: String? = null
    ): Result<Outfit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "createOutfitWithLayout() - Name: $name, Items: ${items.size}")

        return@withContext try {
            val request = ApiService.CreateOutfitWithLayoutRequest(
                name = name,
                category = category,
                items = items
            )

            val result = apiService.createOutfitWithLayout(request)

            result.map { response ->
                Log.d(TAG, "✅ Created outfit ID: ${response.outfitId}")

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
            Log.e(TAG, "❌ Error creating outfit", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ Update outfit
     */
    suspend fun updateOutfit(
        outfitId: Int,
        name: String,
        items: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "updateOutfit() - ID: $outfitId")

        return@withContext try {
            val request = ApiService.CreateOutfitWithLayoutRequest(
                name = name,
                category = null,
                items = items
            )

            val result = apiService.updateOutfit(outfitId, request)

            result.onSuccess {
                Log.d(TAG, "✅ Updated outfit $outfitId")

                // ✅ Delete old snapshot so it regenerates
                SnapshotManager.deleteSnapshot(context, outfitId)
            }

            result.map { Unit }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating outfit", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ Update outfit schedule
     */
    suspend fun updateOutfitSchedule(outfitId: String, schedule: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "updateOutfitSchedule() - ID: $outfitId, Schedule: $schedule")

            return@withContext try {
                val id = outfitId.toIntOrNull()
                    ?: return@withContext Result.failure(Exception("Invalid outfit ID"))

                if (schedule != null) {
                    apiService.scheduleOutfit(id, schedule).map { Unit }
                } else {
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating schedule", e)
                Result.failure(e)
            }
        }

    /**
     * ✅ Delete outfit
     */
    suspend fun deleteOutfit(outfitId: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteOutfit() - ID: $outfitId")

        return@withContext try {
            val id = outfitId.toIntOrNull()
                ?: return@withContext Result.failure(Exception("Invalid outfit ID"))

            val result = apiService.deleteOutfit(id)

            result.onSuccess {
                Log.d(TAG, "✅ Deleted outfit $id from API")

                // ✅ Delete snapshot
                SnapshotManager.deleteSnapshot(context, id)
            }

            result.map { Unit }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting outfit", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ Get outfits by date (scheduled)
     */
    suspend fun getOutfitsByDate(date: String): Result<List<Outfit>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = getAllOutfits()
            result.map { outfits ->
                outfits.filter { it.schedule == date }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ Get all scheduled outfits
     */
    suspend fun getScheduledOutfits(): Result<List<Outfit>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = getAllOutfits()
            result.map { outfits ->
                outfits.filter { it.schedule != null }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ Get scheduled dates
     */
    suspend fun getScheduledDates(): Result<List<String>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = getAllOutfits()
            result.map { outfits ->
                outfits.mapNotNull { it.schedule }.distinct()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}