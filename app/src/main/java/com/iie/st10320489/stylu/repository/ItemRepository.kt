package com.iie.st10320489.stylu.repository

import android.util.Log
import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ItemRepository {

    companion object {
        private const val TAG = "ItemRepository"
        private const val SUPABASE_URL = DirectSupabaseAuth.SUPABASE_URL
        private const val SUPABASE_ANON_KEY = DirectSupabaseAuth.SUPABASE_ANON_KEY
    }

    /**
     * Fetch all items for the current user with category and subcategory info
     */
    suspend fun getUserItems(): Result<List<WardrobeItem>> = withContext(Dispatchers.IO) {
        try {
            val accessToken = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token found"))

            val userId = DirectSupabaseAuth.getCurrentUser()?.id
                ?: return@withContext Result.failure(Exception("No user ID found"))

            // Query items with joined category and subcategory data
            val url = URL("$SUPABASE_URL/rest/v1/item?select=*,sub_category(name,category(name))&user_id=eq.$userId&order=created_at.desc")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")

            val responseCode = connection.responseCode
            val response = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "Get user items response code: $responseCode")
            Log.d(TAG, "Get user items response: $response")

            if (responseCode == 200) {
                val items = parseItems(response)
                Result.success(items)
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(response)
                    errorJson.optString("message", "Failed to fetch items")
                } catch (e: Exception) {
                    "Failed to fetch items: HTTP $responseCode"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user items", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch items filtered by category
     */
    suspend fun getUserItemsByCategory(categoryName: String): Result<List<WardrobeItem>> = withContext(Dispatchers.IO) {
        try {
            val accessToken = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token found"))

            val userId = DirectSupabaseAuth.getCurrentUser()?.id
                ?: return@withContext Result.failure(Exception("No user ID found"))

            // Query with category filter
            val url = URL("$SUPABASE_URL/rest/v1/item?select=*,sub_category(name,category(name))&user_id=eq.$userId&sub_category.category.name=eq.$categoryName&order=created_at.desc")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")

            val responseCode = connection.responseCode
            val response = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "Get items by category response: $response")

            if (responseCode == 200) {
                val items = parseItems(response)
                Result.success(items)
            } else {
                Result.failure(Exception("Failed to fetch items: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching items by category", e)
            Result.failure(e)
        }
    }

    /**
     * Get item counts by category for the current user
     */
    suspend fun getItemCountsByCategory(): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        try {
            val accessToken = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token found"))

            val userId = DirectSupabaseAuth.getCurrentUser()?.id
                ?: return@withContext Result.failure(Exception("No user ID found"))

            // Get all items with category info
            val url = URL("$SUPABASE_URL/rest/v1/item?select=sub_category(category(name))&user_id=eq.$userId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")

            val responseCode = connection.responseCode
            val response = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            if (responseCode == 200) {
                val counts = parseCategoryCounts(response)
                Result.success(counts)
            } else {
                Result.failure(Exception("Failed to fetch category counts"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching category counts", e)
            Result.failure(e)
        }
    }

    /**
     * Delete an item
     */
    suspend fun deleteItem(itemId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(Exception("No access token found"))

            val url = URL("$SUPABASE_URL/rest/v1/item?item_id=eq.$itemId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "DELETE"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")

            val responseCode = connection.responseCode

            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete item: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting item", e)
            Result.failure(e)
        }
    }

    // Parse items from JSON response
// Parse items from JSON response
    private fun parseItems(json: String): List<WardrobeItem> {
        val items = mutableListOf<WardrobeItem>()
        val jsonArray = JSONArray(json)

        for (i in 0 until jsonArray.length()) {
            val itemJson = jsonArray.getJSONObject(i)

            // Extract subcategory and category info
            val subcategoryJson = itemJson.optJSONObject("sub_category")
            val subcategoryName = subcategoryJson?.optString("name") ?: "Unknown"
            val categoryJson = subcategoryJson?.optJSONObject("category")
            val categoryName = categoryJson?.optString("name") ?: "Unknown"

            // ✅ Fix image URL
            val imageUrl = itemJson.optString("image_url", "")

            // ✅ Fix name mapping
            val itemName = itemJson.optString("name", null)

            items.add(
                WardrobeItem(
                    itemId = itemJson.getInt("item_id"),
                    name = itemName ?: subcategoryName, // <-- real name if available
                    category = categoryName,
                    subcategory = subcategoryName,
                    imageUrl = imageUrl,
                    colour = itemJson.optString("colour", null),
                    size = itemJson.optString("size", null),
                    price = if (itemJson.isNull("price")) null else itemJson.getDouble("price"),
                    weatherTag = itemJson.optString("weather_tag", null),
                    timesWorn = itemJson.optInt("times_worn", 0)
                )
            )
        }

        return items
    }

    // Parse category counts from JSON response
    private fun parseCategoryCounts(json: String): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val jsonArray = JSONArray(json)

        for (i in 0 until jsonArray.length()) {
            val itemJson = jsonArray.getJSONObject(i)
            val subcategoryJson = itemJson.optJSONObject("sub_category")
            val categoryJson = subcategoryJson?.optJSONObject("category")
            val categoryName = categoryJson?.optString("name") ?: "Unknown"

            counts[categoryName] = counts.getOrDefault(categoryName, 0) + 1
        }

        return counts
    }
}