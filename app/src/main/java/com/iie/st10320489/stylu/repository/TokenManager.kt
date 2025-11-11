package com.iie.st10320489.stylu.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class TokenManager(context: Context) {

    private val prefs = context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    companion object {
        private const val TAG = "TokenManager"
        private const val KEY_ACCESS_TOKEN = "access_token"
    }

    /**
     * Get valid access token (simplified - just returns current token)
     */
    suspend fun getValidAccessToken(): Result<String> = mutex.withLock {
        return try {
            val currentToken = prefs.getString(KEY_ACCESS_TOKEN, null)

            if (currentToken == null) {
                Log.e(TAG, "No access token available")
                return Result.failure(Exception("Not authenticated. Please login again."))
            }


            Result.success(currentToken)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token", e)
            Result.failure(e)
        }
    }
}