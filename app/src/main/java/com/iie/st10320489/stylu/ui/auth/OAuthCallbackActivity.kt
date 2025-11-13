package com.iie.st10320489.stylu.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iie.st10320489.stylu.MainActivity
import com.iie.st10320489.stylu.auth.SimpleOAuthManager
import kotlinx.coroutines.launch

class OAuthCallbackActivity : AppCompatActivity() {

    private lateinit var oAuthManager: SimpleOAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        oAuthManager = SimpleOAuthManager(this)

        // Handle the OAuth callback
        intent?.data?.let { uri ->
            // Log the entire URI for debugging
            android.util.Log.d("OAuthCallback", "Received URI: $uri")
            android.util.Log.d("OAuthCallback", "Query params: ${uri.query}")

            lifecycleScope.launch {
                val result = oAuthManager.handleOAuthCallback(uri)

                if (result.isSuccess) {
                    android.util.Log.d("OAuthCallback", "Success: ${result.getOrNull()}")
                    val intent = Intent(this@OAuthCallbackActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                } else {
                    android.util.Log.e("OAuthCallback", "Failed: ${result.exceptionOrNull()?.message}")
                    val intent = Intent(this@OAuthCallbackActivity, LoginActivity::class.java)
                    intent.putExtra("oauth_error", result.exceptionOrNull()?.message ?: "OAuth failed")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                finish()
            }
        } ?: run {
            android.util.Log.e("OAuthCallback", "No URI data received")
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}