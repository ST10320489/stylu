package com.iie.st10320489.stylu.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.iie.st10320489.stylu.data.models.category.Category
import com.iie.st10320489.stylu.data.models.category.Subcategory
import com.iie.st10320489.stylu.data.models.item.ItemUploadRequest
import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import com.iie.st10320489.stylu.network.ItemApiService
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ItemRepository(private val context: Context? = null) {

    private val baseUrl = "https://stylu-api-x69c.onrender.com"

    private val itemApiService = context?.let { ItemApiService() }

    companion object {
        private const val TAG = "ItemRepository"
    }

    /**
     * Get all categories with subcategories from API
     */
    suspend fun getCategories(): Result<List<Category>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val url = URL("$baseUrl/api/Item/categories")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            Log.d(TAG, "GET Categories - URL: $url")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "GET Categories - Response Code: $responseCode")
            Log.d(TAG, "GET Categories - Response: $responseText")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val categories = parseCategories(responseText)
                Result.success(categories)
            } else {
                Result.failure(Exception("Failed to load categories: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading categories", e)
            Result.failure(e)
        }
    }

    /**
     * Upload image to Supabase Storage (still direct call as it's storage, not database)
     */
    suspend fun uploadImage(imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            if (context == null) {
                return@withContext Result.failure(Exception("Context required for image upload"))
            }

            itemApiService?.uploadImage(imageUri, context, token)
                ?: Result.failure(Exception("ItemApiService not initialized"))
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image", e)
            Result.failure(e)
        }
    }

    /**
     * Remove background from image (still direct call as it's external API)
     */
    suspend fun removeBackground(imageUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (context == null) {
                return@withContext Result.failure(Exception("Context required for background removal"))
            }

            itemApiService?.removeBackground(imageUri, context)
                ?: Result.failure(Exception("ItemApiService not initialized"))
        } catch (e: Exception) {
            Log.e(TAG, "Error removing background", e)
            Result.failure(e)
        }
    }

    /**
     * Create item through API
     */
    suspend fun createItem(request: ItemUploadRequest): Result<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val url = URL("$baseUrl/api/Item")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
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

            Log.d(TAG, "POST Create Item - URL: $url")
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
            Log.d(TAG, "POST Create Item - Response: $responseText")

            if (responseCode in 200..201) {
                val jsonResponse = JSONObject(responseText)
                val dataObject = jsonResponse.getJSONObject("data")
                val itemId = dataObject.getInt("itemId")
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
        } catch (e: Exception) {
            Log.e(TAG, "Error creating item", e)
            Result.failure(e)
        }
    }

    /**
     * Get all user items from API
     */
    suspend fun getUserItems(): Result<List<WardrobeItem>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val url = URL("$baseUrl/api/Item")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            Log.d(TAG, "GET User Items - URL: $url")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "GET User Items - Response Code: $responseCode")
            Log.d(TAG, "GET User Items - Response: $responseText")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val items = parseUserItems(responseText)
                Result.success(items)
            } else {
                Result.failure(Exception("Failed to fetch items: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user items", e)
            Result.failure(e)
        }
    }

    /**
     * Get item counts by category from API
     */
    suspend fun getItemCountsByCategory(): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val url = URL("$baseUrl/api/Item/counts")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            Log.d(TAG, "GET Item Counts - URL: $url")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "GET Item Counts - Response Code: $responseCode")
            Log.d(TAG, "GET Item Counts - Response: $responseText")

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
     * Update item through API
     */
    suspend fun updateItem(itemId: Int, updates: Map<String, Any>): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val url = URL("$baseUrl/api/Item/$itemId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true

            val requestBody = JSONObject(updates)

            Log.d(TAG, "PUT Update Item - URL: $url")
            Log.d(TAG, "PUT Update Item - Request: $requestBody")

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

            Log.d(TAG, "PUT Update Item - Response Code: $responseCode")
            Log.d(TAG, "PUT Update Item - Response: $responseText")

            if (responseCode == HttpURLConnection.HTTP_OK) {
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
     * Delete item through API
     */
    suspend fun deleteItem(itemId: Int): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val url = URL("$baseUrl/api/Item/$itemId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            Log.d(TAG, "DELETE Item - URL: $url")

            val responseCode = connection.responseCode
            val responseText = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() } ?: "Item deleted successfully"
            }

            Log.d(TAG, "DELETE Item - Response Code: $responseCode")
            Log.d(TAG, "DELETE Item - Response: $responseText")

            if (responseCode in 200..299) {
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

    // Helper methods for parsing responses

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