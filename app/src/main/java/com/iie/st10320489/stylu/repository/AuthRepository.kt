package com.iie.st10320489.stylu.repository

import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {
    private val supabaseAuth = DirectSupabaseAuth

    suspend fun signUp(email: String, password: String, firstName: String, lastName: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext supabaseAuth.signUp(email, password, firstName, lastName)
    }

    suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext supabaseAuth.signIn(email, password)
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext supabaseAuth.signOut()
    }

    fun getCurrentAccessToken(): String? {
        return supabaseAuth.getCurrentAccessToken()
    }

    fun isLoggedIn(): Boolean {
        return supabaseAuth.isLoggedIn()
    }

    fun getCurrentUserEmail(): String? {
        return supabaseAuth.getCurrentUserEmail()
    }
}