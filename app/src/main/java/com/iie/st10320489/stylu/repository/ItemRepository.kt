package com.iie.st10320489.stylu.repository

import android.content.Context
import android.net.Uri
import com.iie.st10320489.stylu.auth.SessionManager
import com.iie.st10320489.stylu.data.models.item.ItemUploadRequest
import com.iie.st10320489.stylu.network.ItemApiService
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ItemRepository(private val context: Context) {

    private val sessionManager = SessionManager(context)
    private val itemApiService = ItemApiService(context)

    /**
     * Get access token helper
     */
    private fun getAccessToken(): String? {
        return sessionManager.getCurrentAccessToken()
    }

    /**
     * Get user ID helper
     */
    private fun getUserId(): String? {
        return sessionManager.getCurrentUserId()
    }

    /**
     * Get categories
     */
    suspend fun getCategories() = withContext(Dispatchers.IO) {
        itemApiService.getCategories()
    }

    /**
     * Upload image to Supabase Storage
     */
    suspend fun uploadImage(imageUri: Uri) = withContext(Dispatchers.IO) {
        val accessToken = getAccessToken()
            ?: return@withContext Result.failure<String>(Exception("Not authenticated"))

        itemApiService.uploadImage(imageUri, accessToken)
    }

    /**
     * Remove background from image
     */
    suspend fun removeBackground(imageUri: Uri) = withContext(Dispatchers.IO) {
        itemApiService.removeBackground(imageUri)
    }

    /**
     * Create item
     */
    suspend fun createItem(request: ItemUploadRequest) = withContext(Dispatchers.IO) {
        val accessToken = getAccessToken()
            ?: return@withContext Result.failure<com.iie.st10320489.stylu.data.models.response.ItemResponse>(Exception("Not authenticated"))

        itemApiService.createItem(accessToken, request)
    }

    /**
     * Get all user items (returns WardrobeItem for UI)
     */
    suspend fun getUserItems(): Result<List<WardrobeItem>> = withContext(Dispatchers.IO) {
        try {
            // TODO: Implement API call to get user items
            // For now, return empty list
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get items by subcategory
     */
    suspend fun getItemsBySubcategory(subcategoryId: Int): Result<List<WardrobeItem>> = withContext(Dispatchers.IO) {
        try {
            // TODO: Implement API call
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get item counts by category
     */
    suspend fun getItemCountsByCategory(): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        try {
            // TODO: Implement API call
            Result.success(emptyMap())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update item
     */
    suspend fun updateItem(itemId: Int, updates: Map<String, Any>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // TODO: Implement API call
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete item
     */
    suspend fun deleteItem(itemId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // TODO: Implement API call
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}