// ProfileRepository.kt
package com.iie.st10320489.stylu.repository

import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.apply
import kotlin.io.bufferedReader
import kotlin.io.readText
import kotlin.io.use

class ProfileRepository {

    suspend fun createUserProfile(
        userId: String,
        email: String,
        firstName: String,
        lastName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = DirectSupabaseAuth.getCurrentAccessToken()
                ?: return@withContext Result.failure(kotlin.Exception("No access token found"))

            val url = URL("${DirectSupabaseAuth.SUPABASE_URL}/rest/v1/user_profiles")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", DirectSupabaseAuth.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("user_id", userId)        // must match auth.uid()
                put("first_name", firstName)
                put("last_name", lastName)
                put("phone_number", "")
                put("language", "en")
            }

            connection.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                }
            }

            val responseCode = connection.responseCode
            val response = if (responseCode >= 400) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }

            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(kotlin.Exception("Profile creation failed: $response"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
