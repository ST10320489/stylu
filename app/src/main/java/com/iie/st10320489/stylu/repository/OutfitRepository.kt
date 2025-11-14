package com.iie.st10320489.stylu.repository

import android.content.Context
import android.util.Log
import com.iie.st10320489.stylu.data.local.StyluDatabase
import com.iie.st10320489.stylu.data.local.entities.OutfitEntity
import com.iie.st10320489.stylu.data.local.entities.OutfitItemEntity
import com.iie.st10320489.stylu.data.models.outfit.Outfit
import com.iie.st10320489.stylu.data.models.outfit.OutfitItemDetail
import com.iie.st10320489.stylu.network.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ✅ ONLINE-FIRST OutfitRepository with Offline Fallback
 * ✅ ENHANCED: Better item sync validation and logging
 *
 * Architecture:
 * 1. ONLINE (Default): Try API → Cache to Room → Return API data
 * 2. OFFLINE (Fallback): API fails → Load from Room cache → Return cached data
 * 3. Real-time updates: Flow observes Room for automatic UI updates
 */
class OutfitRepository(private val context: Context) {

    private val database = StyluDatabase.getDatabase(context)
    private val outfitDao = database.outfitDao()
    private val itemDao = database.itemDao()
    private val apiService = ApiService(context)
    private val authRepository = AuthRepository(context)

    companion object {
        private const val TAG = "OutfitRepository"
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * ✅ ONLINE-FIRST: Get all outfits
     * 1. Emit cached data immediately (instant load)
     * 2. Check if cache is stale
     * 3. If stale, fetch from API and update cache
     * 4. Emit fresh data
     */
    fun getAllOutfits(forceRefresh: Boolean = false): Flow<Result<List<Outfit>>> = flow {
        try {
            Log.d(TAG, "getAllOutfits() called, forceRefresh=$forceRefresh")

            // STEP 1: Emit cached data first (instant load!)
            if (!forceRefresh) {
                val cachedOutfits = outfitDao.getAllOutfits()
                if (cachedOutfits.isNotEmpty()) {
                    Log.d(TAG, "✅ Cache HIT: ${cachedOutfits.size} outfits")

                    val outfits = cachedOutfits.mapNotNull { entity ->
                        try {
                            val items = outfitDao.getOutfitLayout(entity.outfitId)
                            entity.toOutfit(items)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to convert outfit ${entity.outfitId}", e)
                            null
                        }
                    }

                    emit(Result.success(outfits))

                    // Check cache age
                    val cacheAge = System.currentTimeMillis() - (cachedOutfits.firstOrNull()?.updatedAt?.toLongOrNull() ?: 0)
                    if (cacheAge < CACHE_VALIDITY_MS) {
                        Log.d(TAG, "Cache is FRESH, skipping API call")
                        return@flow
                    }

                    Log.d(TAG, "Cache is STALE, fetching from API...")
                } else {
                    Log.d(TAG, "❌ Cache MISS: No outfits in cache")
                }
            }

            // STEP 2: Fetch from API
            Log.d(TAG, "📡 Fetching outfits from API...")
            val apiResult = apiService.getUserOutfits()

            apiResult.onSuccess { apiOutfits ->
                Log.d(TAG, "✅ API returned ${apiOutfits.size} outfits")

                // ✅ Log each outfit's items
                apiOutfits.forEach { outfit ->
                    Log.d(TAG, "  - Outfit: ${outfit.name} has ${outfit.items.size} items")
                }

                // STEP 3: Save to cache
                withContext(Dispatchers.IO) {
                    val entities = apiOutfits.map { apiOutfit ->
                        OutfitEntity(
                            outfitId = apiOutfit.outfitId,
                            userId = apiOutfit.userId,
                            name = apiOutfit.name,
                            category = apiOutfit.category,
                            scheduledDate = apiOutfit.schedule,
                            createdAt = apiOutfit.createdAt,
                            updatedAt = System.currentTimeMillis().toString()
                        )
                    }

                    Log.d(TAG, "💾 Caching ${entities.size} outfits...")
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

                        if (items.isNotEmpty()) {
                            outfitDao.insertOutfitItems(items)
                            Log.d(TAG, "  💾 Cached ${items.size} items for outfit: ${apiOutfit.name}")
                        } else {
                            Log.w(TAG, "  ⚠️ No items to cache for outfit: ${apiOutfit.name}")
                        }
                    }

                    Log.d(TAG, "✅ Cache UPDATED")
                }

                // STEP 4: Convert and emit fresh data
                val outfits = apiOutfits.map { apiOutfit ->
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

                emit(Result.success(outfits))

            }.onFailure { error ->
                Log.e(TAG, "❌ API failed: ${error.message}")

                // Fallback to cache on API failure
                val cachedOutfits = outfitDao.getAllOutfits()
                if (cachedOutfits.isNotEmpty()) {
                    Log.d(TAG, "⚠️ Using cached data due to API failure")
                    val outfits = cachedOutfits.mapNotNull { entity ->
                        try {
                            val items = outfitDao.getOutfitLayout(entity.outfitId)
                            entity.toOutfit(items)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    emit(Result.success(outfits))
                } else {
                    emit(Result.failure(error))
                }
            }

        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "⏱️ Timeout", e)
            handleOfflineMode(e, "Request timed out. Server may be starting up.")
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "📱 No connection", e)
            handleOfflineMode(e, "Cannot connect to server. Showing cached data.")
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "📱 No internet", e)
            handleOfflineMode(e, "No internet connection. Showing cached data.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error", e)
            handleOfflineMode(e, "Error loading outfits. Showing cached data.")
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<Result<List<Outfit>>>.handleOfflineMode(
        error: Exception,
        message: String
    ) {
        val cachedOutfits = outfitDao.getAllOutfits()
        if (cachedOutfits.isNotEmpty()) {
            Log.d(TAG, "📱 Offline mode: Using cache")
            val outfits = cachedOutfits.mapNotNull { entity ->
                try {
                    val items = outfitDao.getOutfitLayout(entity.outfitId)
                    entity.toOutfit(items)
                } catch (e: Exception) {
                    null
                }
            }
            emit(Result.success(outfits))
        } else {
            emit(Result.failure(Exception(message)))
        }
    }

    /**
     * Get outfits Flow for real-time updates from cache
     */
    fun getAllOutfitsFlow(): Flow<List<Outfit>> {
        Log.d(TAG, "getAllOutfitsFlow() - observing cache")
        return outfitDao.getAllOutfitsFlow().map { entities ->
            Log.d(TAG, "🔄 Flow update: ${entities.size} outfits")
            entities.mapNotNull { entity ->
                try {
                    val items = outfitDao.getOutfitLayout(entity.outfitId)
                    entity.toOutfit(items)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to convert outfit ${entity.outfitId}", e)
                    null
                }
            }
        }
    }

    /**
     * Get outfits for a specific date
     */
    fun getOutfitsByDate(date: String): Flow<List<Outfit>> {
        Log.d(TAG, "getOutfitsByDate() called with date: $date")
        return outfitDao.getAllOutfitsFlow().map { entities ->
            val filtered = entities.filter { it.scheduledDate == date }
            Log.d(TAG, "📅 Found ${filtered.size} outfits for date $date")
            filtered.mapNotNull { entity ->
                try {
                    val items = outfitDao.getOutfitLayout(entity.outfitId)
                    entity.toOutfit(items)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Get all scheduled outfits
     */
    fun getScheduledOutfits(): Flow<List<Outfit>> {
        Log.d(TAG, "getScheduledOutfits() called")
        return outfitDao.getAllOutfitsFlow().map { entities ->
            val scheduled = entities.filter { it.scheduledDate != null }
            Log.d(TAG, "📅 Found ${scheduled.size} scheduled outfits")
            scheduled.mapNotNull { entity ->
                try {
                    val items = outfitDao.getOutfitLayout(entity.outfitId)
                    entity.toOutfit(items)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Get scheduled dates for calendar indicators
     */
    fun getScheduledDates(): Flow<List<String>> {
        return outfitDao.getScheduledDatesFlow().map { dates ->
            dates.filterNotNull()
        }
    }

    /**
     * ✅ ONLINE-FIRST: Create new outfit
     */
    suspend fun createOutfit(
        name: String,
        category: String,
        items: List<String>,
        schedule: String? = null
    ): Result<Outfit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "createOutfit() - Name: $name, Items: ${items.size}")

        return@withContext try {
            val itemIds = items.mapNotNull { it.toIntOrNull() }

            val request = ApiService.CreateOutfitRequest(
                name = name,
                category = category,
                itemIds = itemIds
            )

            val result = apiService.createOutfit(request)

            result.onSuccess { response ->
                Log.d(TAG, "✅ API success - Outfit ID: ${response.outfitId}")

                val entity = OutfitEntity(
                    outfitId = response.outfitId,
                    userId = authRepository.getCurrentUserId() ?: "",
                    name = response.name,
                    category = response.category,
                    scheduledDate = schedule,
                    createdAt = response.createdAt,
                    updatedAt = System.currentTimeMillis().toString()
                )

                outfitDao.insertOutfit(entity)
                Log.d(TAG, "💾 Cached new outfit")

                refreshCacheInBackground()
            }

            result.onFailure { error ->
                Log.e(TAG, "❌ API failed: ${error.message}")
            }

            result.map { response ->
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
     * ✅ Create new outfit WITH LAYOUT DATA
     * This sends items with positions, scales, and sizes to save in outfit_item table
     */
    suspend fun createOutfitWithLayout(
        name: String,
        category: String,
        items: List<String>,  // JSON strings with layout data
        schedule: String? = null
    ): Result<Outfit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "createOutfitWithLayout() - Name: $name, Items: ${items.size}")

        return@withContext try {
            // ✅ Use the SAME request format as updateOutfit
            val request = ApiService.CreateOutfitWithLayoutRequest(
                name = name,
                category = category,
                items = items  // JSON strings: ["{itemId:52,x:0.3,...}", ...]
            )

            // ✅ Call the createOutfitWithLayout API endpoint
            val result = apiService.createOutfitWithLayout(request)

            result.onSuccess { response ->
                Log.d(TAG, "✅ API success - Outfit ID: ${response.outfitId}")

                val entity = OutfitEntity(
                    outfitId = response.outfitId,
                    userId = authRepository.getCurrentUserId() ?: "",
                    name = response.name,
                    category = response.category,
                    scheduledDate = schedule,
                    createdAt = response.createdAt,
                    updatedAt = System.currentTimeMillis().toString()
                )

                outfitDao.insertOutfit(entity)
                Log.d(TAG, "💾 Cached new outfit with layout")

                refreshCacheInBackground()
            }

            result.onFailure { error ->
                Log.e(TAG, "❌ API failed: ${error.message}")
            }

            result.map { response ->
                Outfit(
                    outfitId = response.outfitId,
                    userId = authRepository.getCurrentUserId() ?: "",
                    name = response.name,
                    category = response.category,
                    items = emptyList(),  // Items will be loaded from cache refresh
                    schedule = schedule,
                    createdAt = response.createdAt
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating outfit with layout", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ ONLINE-FIRST: Update outfit
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
                Log.d(TAG, "✅ Outfit updated via API")
                refreshCacheInBackground()
            }

            result.onFailure { error ->
                Log.e(TAG, "❌ Update failed: ${error.message}")
            }

            result.map { Unit }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating outfit", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ ONLINE-FIRST: Update outfit schedule
     */
    suspend fun updateOutfitSchedule(outfitId: String, schedule: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "updateOutfitSchedule() - ID: $outfitId, Schedule: $schedule")

            return@withContext try {
                val id = outfitId.toIntOrNull()
                if (id == null) {
                    return@withContext Result.failure(Exception("Invalid outfit ID"))
                }

                if (schedule != null) {
                    val result = apiService.scheduleOutfit(id, schedule)

                    result.onSuccess {
                        Log.d(TAG, "✅ Schedule updated via API")
                        outfitDao.updateScheduledDate(
                            id,
                            schedule,
                            System.currentTimeMillis().toString()
                        )
                    }

                    result.onFailure { error ->
                        Log.e(TAG, "❌ Schedule update failed: ${error.message}")
                    }

                    result.map { Unit }
                } else {
                    outfitDao.updateScheduledDate(
                        id,
                        null,
                        System.currentTimeMillis().toString()
                    )
                    Result.success(Unit)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating schedule", e)
                Result.failure(e)
            }
        }

    /**
     * ✅ ONLINE-FIRST: Delete outfit
     */
    suspend fun deleteOutfit(outfitId: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteOutfit() - ID: $outfitId")

        return@withContext try {
            val id = outfitId.toIntOrNull() ?: return@withContext Result.failure(
                Exception("Invalid outfit ID")
            )

            val result = apiService.deleteOutfit(id)

            result.onSuccess {
                Log.d(TAG, "✅ Deleted via API")
                outfitDao.deleteOutfitComplete(id)
                Log.d(TAG, "💾 Removed from cache")
            }

            result.onFailure { error ->
                Log.e(TAG, "❌ API delete failed: ${error.message}")
                outfitDao.deleteOutfitComplete(id)
                Log.d(TAG, "💾 Removed from cache (offline)")
            }

            result.map { Unit }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting outfit", e)
            Result.failure(e)
        }
    }

    /**
     * Refresh cache in background (fire and forget)
     */
    private fun refreshCacheInBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔄 Background cache refresh triggered")
                getAllOutfits(forceRefresh = true).collect { /* ignore result */ }
            } catch (e: Exception) {
                Log.e(TAG, "Background refresh failed", e)
            }
        }
    }

    /**
     * ✅ ENHANCED: Convert OutfitEntity to Outfit with detailed logging
     */
    private suspend fun OutfitEntity.toOutfit(itemEntities: List<OutfitItemEntity>): Outfit {
        Log.d(TAG, "🔄 Converting outfit: $name (ID: $outfitId)")
        Log.d(TAG, "   Expected ${itemEntities.size} items")

        // ✅ Check which items are in cache
        val cachedItemIds = itemDao.getAllItems().map { it.itemId }.toSet()
        Log.d(TAG, "   Items in cache: ${cachedItemIds.size} total items")

        val items = itemEntities.mapNotNull { itemEntity ->
            val itemDetails = itemDao.getItemById(itemEntity.itemId)

            if (itemDetails != null) {
                Log.d(TAG, "   ✅ Found item ${itemEntity.itemId} in cache")
                OutfitItemDetail(
                    itemId = itemEntity.itemId,
                    name = itemDetails.name,
                    imageUrl = itemDetails.imageUrl,
                    colour = itemDetails.colour,
                    subcategory = itemDetails.subcategory,
                    layoutData = com.iie.st10320489.stylu.network.ApiService.ItemLayoutData(
                        x = itemEntity.x,
                        y = itemEntity.y,
                        scale = itemEntity.scale,
                        width = itemEntity.width,
                        height = itemEntity.height
                    )
                )
            } else {
                Log.w(TAG, "   ❌ Item ${itemEntity.itemId} NOT FOUND in cache!")
                Log.w(TAG, "      Is item ${itemEntity.itemId} in cache? ${itemEntity.itemId in cachedItemIds}")
                null
            }
        }

        Log.d(TAG, "   Result: ${items.size} items loaded for outfit")

        if (items.size < itemEntities.size) {
            Log.w(TAG, "   ⚠️ MISSING ${itemEntities.size - items.size} items!")
            val missingIds = itemEntities.map { it.itemId } - items.map { it.itemId }.toSet()
            Log.w(TAG, "   Missing item IDs: $missingIds")
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