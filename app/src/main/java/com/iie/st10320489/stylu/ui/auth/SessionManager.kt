package com.iie.st10320489.stylu.auth

import android.content.Context
import android.content.Intent
import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import com.iie.st10320489.stylu.ui.auth.LoginActivity

class SessionManager(private val context: Context) {

    private val supabaseAuth = DirectSupabaseAuth

    fun redirectToLogin() {
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }

    fun getCurrentUser() = supabaseAuth.getCurrentUser()

    fun getCurrentAccessToken() = supabaseAuth.getCurrentAccessToken()

    fun isAuthenticated() = supabaseAuth.isLoggedIn()

    fun getCurrentUserEmail() = supabaseAuth.getCurrentUserEmail()
}