package com.iie.st10320489.stylu.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.iie.st10320489.stylu.data.models.*
import com.iie.st10320489.stylu.data.models.category.Category
import com.iie.st10320489.stylu.data.models.category.Subcategory
import com.iie.st10320489.stylu.data.models.item.Item
import com.iie.st10320489.stylu.data.models.item.ItemUploadRequest
import com.iie.st10320489.stylu.data.models.response.ItemResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ItemApiService(private val context: Context) {

    private val supabaseAuth = DirectSupabaseAuth(context)

    companion object {
        private const val TAG = "ItemApiService"
        private const val SUPABASE_URL = "https://fkmhmtioehokrukqwano.supabase.co"
        private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZrbWhtdGlvZWhva3J1a3F3YW5vIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgyMDAzNDIsImV4cCI6MjA3Mzc3NjM0Mn0.wg5fNm5_M8CRN3uzHnqvaxovIUDLCUWDcSiFJ14WqNE"
        private const val SUPABASE_STORAGE_BUCKET = "items"
        private const val REMOVE_BG_API_KEY = "MP6cjk1T5mfdMJrSGPk3gQUV"
    }

    // Get all categories with subcategories directly from Supabase
    suspend fun getCategories(): Result<List<Category>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$SUPABASE_URL/rest/v1/category?select=*,sub_category(*)")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Content-Type", "application/json")

            val responseCode = connection.responseCode
            val response = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "Get categories response: $response")

            if (responseCode == 200) {
                val categories = parseCategories(response)
                Result.success(categories)
            } else {
                Result.failure(Exception("Failed to load categories: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading categories", e)
            Result.failure(e)
        }
    }

    // Upload image to Supabase Storage
    suspend fun uploadImage(imageUri: Uri, accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Get bitmap from URI
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Compress and convert to byte array as PNG (keeps transparency)
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()

            // Generate unique filename with .png extension
            val userId = supabaseAuth.getCurrentUserId() ?: "unknown"
            val fileName = "${userId}_${UUID.randomUUID()}.png"

            // Upload to Supabase Storage
            val url = URL("$SUPABASE_URL/storage/v1/object/$SUPABASE_STORAGE_BUCKET/$fileName")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "image/png")
            connection.doOutput = true

            connection.outputStream.use { it.write(imageBytes) }

            val responseCode = connection.responseCode
            val response = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "Upload image response code: $responseCode")
            Log.d(TAG, "Upload image response: $response")

            if (responseCode in 200..201) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$SUPABASE_STORAGE_BUCKET/$fileName"
                Result.success(publicUrl)
            } else {
                Result.failure(Exception("Upload failed: $response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image", e)
            Result.failure(e)
        }
    }

    // Remove background using remove.bg API
    suspend fun removeBackground(imageUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            // Get image bytes
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val imageBytes = inputStream?.readBytes()
            inputStream?.close()

            if (imageBytes == null) {
                return@withContext Result.failure(Exception("Failed to read image"))
            }

            // Prepare multipart request
            val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
            val url = URL("https://api.remove.bg/v1.0/removebg")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("X-Api-Key", REMOVE_BG_API_KEY)
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.doOutput = true

            // Build multipart body
            val writer = DataOutputStream(connection.outputStream)

            // Add image file part
            writer.writeBytes("--$boundary\r\n")
            writer.writeBytes("Content-Disposition: form-data; name=\"image_file\"; filename=\"image.jpg\"\r\n")
            writer.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            writer.write(imageBytes)
            writer.writeBytes("\r\n")

            // Add size parameter
            writer.writeBytes("--$boundary\r\n")
            writer.writeBytes("Content-Disposition: form-data; name=\"size\"\r\n\r\n")
            writer.writeBytes("auto\r\n")

            writer.writeBytes("--$boundary--\r\n")
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            Log.d(TAG, "Remove.bg response code: $responseCode")

            if (responseCode == 200) {
                // Save the result image
                val resultBytes = connection.inputStream.readBytes()
                val outputFile = File(context.cacheDir, "nobg_${System.currentTimeMillis()}.png")
                FileOutputStream(outputFile).use { it.write(resultBytes) }

                Result.success(Uri.fromFile(outputFile))
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Background removal failed"
                Log.e(TAG, "Remove.bg error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing background", e)
            Result.failure(e)
        }
    }

    // Create item in Supabase database
    suspend fun createItem(accessToken: String, request: ItemUploadRequest): Result<ItemResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$SUPABASE_URL/rest/v1/item")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=representation")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("user_id", request.userId)
                put("subcategory_id", request.subcategoryId)
                request.colour?.let { put("colour", it) }
                request.material?.let { put("material", it) }
                request.size?.let { put("size", it) }
                request.price?.let { put("price", it) }
                put("image_url", request.imageUrl)
                request.weatherTag?.let { put("weather_tag", it) }
                put("times_worn", 0)
                put("created_by", request.createdBy)
            }

            Log.d(TAG, "Create item request: $requestBody")

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            val response = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            Log.d(TAG, "Create item response code: $responseCode")
            Log.d(TAG, "Create item response: $response")

            if (responseCode in 200..201) {
                val jsonArray = JSONArray(response)
                val itemJson = jsonArray.getJSONObject(0)

                val item = Item(
                    itemId = itemJson.getInt("item_id"),
                    userId = itemJson.getString("user_id"),
                    subcategoryId = itemJson.getInt("subcategory_id"),
                    colour = itemJson.optString("colour", null),
                    material = itemJson.optString("material", null),
                    size = itemJson.optString("size", null),
                    price = if (itemJson.isNull("price")) null else itemJson.getDouble("price"),
                    imageUrl = itemJson.getString("image_url"),
                    weatherTag = itemJson.optString("weather_tag", null),
                    timesWorn = itemJson.getInt("times_worn"),
                    createdBy = itemJson.getString("created_by"),
                    createdAt = itemJson.getString("created_at")
                )

                val itemResponse = ItemResponse(
                    success = true,
                    message = "Item created successfully",
                    data = item
                )
                Result.success(itemResponse)
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(response)
                    errorJson.optString("message", "Failed to create item")
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

    // Parse categories from JSON response
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
}