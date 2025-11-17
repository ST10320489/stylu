package com.iie.st10320489.stylu.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.iie.st10320489.stylu.data.local.StyluDatabase
import com.iie.st10320489.stylu.data.local.entities.toEntity
import com.iie.st10320489.stylu.data.local.entities.toWardrobeItem
import com.iie.st10320489.stylu.data.models.category.Category
import com.iie.st10320489.stylu.data.models.category.Subcategory
import com.iie.st10320489.stylu.data.models.item.ItemUploadRequest
import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import com.iie.st10320489.stylu.network.ItemApiService
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL


/**
 * ItemRepository with Room Database Caching
 *
 * ARCHITECTURE:
 * 1. Cache-first strategy: Show cached data immediately
 * 2. Background sync: Fetch fresh data from API
 * 3. Offline support: App works without internet
 * 4. Auto-refresh: Updates cache when data changes
 */
class ItemRepository(private val context: Context? = null) {

    private val baseUrl = "https://stylu-api-x69c.onrender.com"
    private val itemApiService = context?.let { ItemApiService(it) }
    private val supabaseAuth = context?.let { DirectSupabaseAuth(it) }

    // Room Database
    private val database = context?.let { StyluDatabase.getDatabase(it) }
    private val itemDao = database?.itemDao()

    companion object {
        private const val TAG = "ItemRepository"
        private const val CONNECT_TIMEOUT = 60000  // 60 seconds
        private const val READ_TIMEOUT = 30000     // 30 seconds
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Helper to get current access token
     */
    private fun getAccessToken(): String? {
        return supabaseAuth?.getCurrentAccessToken()
    }

    /**
     * Get all categories with subcategories from API
     */
    suspend fun getCategories(): Result<List<Category>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val url = URL("$baseUrl/api/Item/categories")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            Log.d(TAG, "GET Categories - URL: $url")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "GET Categories - Response Code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val categories = parseCategories(responseText)
                Result.success(categories)
            } else {
                Result.failure(Exception("Failed to load categories: HTTP $responseCode"))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout loading categories", e)
            Result.failure(Exception("Request timed out. Server may be starting up. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error loading categories", e)
            Result.failure(e)
        }
    }

    /**
     * Upload image to Supabase Storage
     */
    suspend fun uploadImage(imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            if (context == null) {
                return@withContext Result.failure(Exception("Context required for image upload"))
            }

            itemApiService?.uploadImage(imageUri, token)
                ?: Result.failure(Exception("ItemApiService not initialized"))
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image", e)
            Result.failure(e)
        }
    }

    /**
     * Remove background from image
     */
    suspend fun removeBackground(imageUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (context == null) {
                return@withContext Result.failure(Exception("Context required for background removal"))
            }

            itemApiService?.removeBackground(imageUri)
                ?: Result.failure(Exception("ItemApiService not initialized"))
        } catch (e: Exception) {
            Log.e(TAG, "Error removing background", e)
            Result.failure(e)
        }
    }

    /**
     * Create item through API and cache it
     */
    suspend fun createItem(request: ItemUploadRequest): Result<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val url = URL("$baseUrl/api/Item")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("userId", request.userId)
                put("subcategoryId", request.subcategoryId)
                request.name?.let { put("name", it) }
                request.colour?.let { put("colour", it) }
                request.material?.let { put("material", it) }
                request.size?.let { put("size", it) }
                request.price?.let { put("price", it) }
                put("imageUrl", request.imageUrl)
                request.weatherTag?.let { put("weatherTag", it) }
                put("createdBy", request.createdBy)
            }

            Log.d(TAG, "POST Create Item - Request: $requestBody")

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "POST Create Item - Response Code: $responseCode")

            if (responseCode in 200..201) {
                val jsonResponse = JSONObject(responseText)
                val dataObject = jsonResponse.getJSONObject("data")
                val itemId = dataObject.getInt("itemId")

                // 🔥 TRIGGER CACHE REFRESH (don't wait for it)
                refreshCacheInBackground()

                Result.success(itemId)
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(responseText)
                    errorJson.optString("error", "Failed to create item")
                } catch (e: Exception) {
                    "Failed to create item: HTTP $responseCode"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout creating item", e)
            Result.failure(Exception("Request timed out. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error creating item", e)
            Result.failure(e)
        }
    }

    /**
     * Get all user items with CACHING
     *
     * 1. Emit cached items immediately
     * 2. Check if cache is stale (>5 minutes)
     * 3. If stale, fetch from API in background
     * 4. Update cache and emit fresh data
     */
    fun getUserItems(forceRefresh: Boolean = false): Flow<Result<List<WardrobeItem>>> = flow {
        try {
            Log.d(TAG, "getUserItems() called, forceRefresh=$forceRefresh")

            // STEP 1: Emit cached data first (instant load!)
            if (!forceRefresh && itemDao != null) {
                val cachedItems = itemDao.getAllItems()
                if (cachedItems.isNotEmpty()) {
                    val cacheAge = System.currentTimeMillis() - (cachedItems.firstOrNull()?.updatedAt ?: 0)
                    Log.d(TAG, "Cache HIT: ${cachedItems.size} items, age: ${cacheAge}ms")

                    // Emit cached data immediately
                    emit(Result.success(cachedItems.map { it.toWardrobeItem() }))

                    // If cache is fresh, skip API
                    if (cacheAge < CACHE_VALIDITY_MS) {
                        Log.d(TAG, "Cache is FRESH, skipping API call")
                        return@flow
                    }

                    Log.d(TAG, "Cache is STALE, fetching from API...")
                } else {
                    Log.d(TAG, "Cache MISS: No items in cache")
                }
            }

            // STEP 2: Fetch from API (background thread)
            val token = getAccessToken()
            if (token == null) {
                Log.e(TAG, "No access token available")

                // Fallback to cached data if any
                if (itemDao != null) {
                    val cachedItems = itemDao.getAllItems()
                    if (cachedItems.isEmpty()) {
                        emit(Result.failure(Exception("No access token available. Please login again.")))
                    }
                } else {
                    emit(Result.failure(Exception("No access token available. Please login again.")))
                }
                return@flow
            }

            // 🔹 Move network call to IO thread
            val (responseCode, responseText) = withContext(Dispatchers.IO) {
                val url = URL("$baseUrl/api/Item")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT

                Log.d(TAG, "🌐 Fetching from API...")

                val code = connection.responseCode
                val text = if (code >= 400) {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } else {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }

                Pair(code, text)
            }

            Log.d(TAG, "API Response Code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val items = parseUserItems(responseText)
                Log.d(TAG, "API returned ${items.size} items")

                // STEP 3: Update cache
                if (itemDao != null) {
                    withContext(Dispatchers.IO) {
                        val entities = items.map { it.toEntity() }
                        itemDao.deleteAllItems()
                        itemDao.insertItems(entities)
                        Log.d(TAG, "Cache UPDATED with ${entities.size} items")
                    }
                }

                // STEP 4: Emit fresh data
                emit(Result.success(items))

            } else {
                val errorMessage = when (responseCode) {
                    401 -> "Authentication failed. Please login again."
                    403 -> "Access denied."
                    else -> "Failed to fetch items: HTTP $responseCode"
                }
                Log.e(TAG, errorMessage)

                if (itemDao != null) {
                    val cachedItems = itemDao.getAllItems()
                    if (cachedItems.isEmpty()) {
                        emit(Result.failure(Exception(errorMessage)))
                    }
                } else {
                    emit(Result.failure(Exception(errorMessage)))
                }
            }

        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching user items", e)

            if (itemDao != null) {
                val cachedItems = itemDao.getAllItems()
                if (cachedItems.isNotEmpty()) {
                    Log.d(TAG, "Using cache due to timeout")
                    emit(Result.success(cachedItems.map { it.toWardrobeItem() }))
                } else {
                    emit(Result.failure(Exception("Request timed out. Please try again.")))
                }
            } else {
                emit(Result.failure(Exception("Request timed out. Please try again.")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user items", e)

            if (itemDao != null) {
                val cachedItems = itemDao.getAllItems()
                if (cachedItems.isNotEmpty()) {
                    Log.d(TAG, "Using cache due to exception")
                    emit(Result.success(cachedItems.map { it.toWardrobeItem() }))
                } else {
                    emit(Result.failure(e))
                }
            } else {
                emit(Result.failure(e))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get items Flow for real-time updates
     */
    fun getUserItemsFlow(): Flow<List<WardrobeItem>>? {
        return itemDao?.getAllItemsFlow()?.map { entities ->
            entities.map { it.toWardrobeItem() }
        }
    }

    /**
     * Get item counts by category with caching
     */
    suspend fun getItemCountsByCategory(): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Try cache first
            if (itemDao != null) {
                val cachedItems = itemDao.getAllItems()
                if (cachedItems.isNotEmpty()) {
                    val counts = cachedItems.groupBy { it.category }
                        .mapValues { it.value.size }
                    Log.d(TAG, "Counts from cache: $counts")
                    return@withContext Result.success(counts)
                }
            }

            // Fallback to API
            val token = getAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val url = URL("$baseUrl/api/Item/counts")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val counts = parseItemCounts(responseText)
                Result.success(counts)
            } else {
                Result.failure(Exception("Failed to fetch item counts: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching item counts", e)
            Result.failure(e)
        }
    }

    /**
     * Update item through API and update cache
     */
    suspend fun updateItem(itemId: Int, updates: Map<String, Any>): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val url = URL("$baseUrl/api/Item/$itemId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doOutput = true

            val requestBody = JSONObject(updates)

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {

                refreshCacheInBackground()

                val jsonResponse = JSONObject(responseText)
                val message = jsonResponse.optString("message", "Item updated successfully")
                Result.success(message)
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(responseText)
                    errorJson.optString("error", "Failed to update item")
                } catch (e: Exception) {
                    "Failed to update item: HTTP $responseCode"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating item", e)
            Result.failure(e)
        }
    }

    /**
     * Delete item through API and remove from cache
     */
    suspend fun deleteItem(itemId: Int): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val url = URL("$baseUrl/api/Item/$itemId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() } ?: "Item deleted successfully"
            }

            if (responseCode in 200..299) {

                if (itemDao != null) {
                    withContext(Dispatchers.IO) {
                        itemDao.deleteItem(itemId)
                        Log.d(TAG, "Item $itemId removed from cache")
                    }
                }

                Result.success("Item deleted successfully")
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(responseText)
                    errorJson.optString("error", "Failed to delete item")
                } catch (e: Exception) {
                    "Failed to delete item: HTTP $responseCode"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting item", e)
            Result.failure(e)
        }
    }

    /**
     * Clear all cached items
     */
    suspend fun clearCache() {
        itemDao?.deleteAllItems()
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Refresh cache in background (fire and forget)
     */
    private fun refreshCacheInBackground() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Background cache refresh triggered")
                getUserItems(forceRefresh = true).collect { /* ignore result */ }
            } catch (e: Exception) {
                Log.e(TAG, "Background refresh failed", e)
            }
        }
    }

    // Helper parsing methods (unchanged)

    private fun parseCategories(json: String): List<Category> {
        val categories = mutableListOf<Category>()
        val jsonArray = JSONArray(json)

        for (i in 0 until jsonArray.length()) {
            val categoryJson = jsonArray.getJSONObject(i)
            val subcategoriesArray = categoryJson.optJSONArray("sub_category") ?: JSONArray()
            val subcategories = mutableListOf<Subcategory>()

            for (j in 0 until subcategoriesArray.length()) {
                val subcategoryJson = subcategoriesArray.getJSONObject(j)
                subcategories.add(
                    Subcategory(
                        subcategoryId = subcategoryJson.getInt("subcategory_id"),
                        categoryId = subcategoryJson.getInt("category_id"),
                        name = subcategoryJson.getString("name")
                    )
                )
            }

            categories.add(
                Category(
                    categoryId = categoryJson.getInt("category_id"),
                    name = categoryJson.getString("name"),
                    subcategories = subcategories
                )
            )
        }

        return categories
    }

    private fun parseUserItems(json: String): List<WardrobeItem> {
        val items = mutableListOf<WardrobeItem>()
        val jsonArray = JSONArray(json)

        for (i in 0 until jsonArray.length()) {
            val itemJson = jsonArray.getJSONObject(i)

            val subCategoryName = itemJson.optJSONObject("sub_category")
                ?.optString("name", "Unknown") ?: "Unknown"

            val categoryName = itemJson.optJSONObject("sub_category")
                ?.optJSONObject("category")
                ?.optString("name", "Unknown") ?: "Unknown"

            items.add(
                WardrobeItem(
                    itemId = itemJson.getInt("item_id"),
                    name = itemJson.optString("name", null),
                    subcategory = subCategoryName,
                    category = categoryName,
                    colour = itemJson.optString("colour", null),
                    size = itemJson.optString("size", null),
                    imageUrl = itemJson.getString("image_url"),
                    weatherTag = itemJson.optString("weather_tag", null),
                    timesWorn = itemJson.getInt("times_worn")
                )
            )
        }

        return items
    }

    private fun parseItemCounts(json: String): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val jsonObject = JSONObject(json)

        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            counts[key] = jsonObject.getInt(key)
        }

        return counts
    }
}