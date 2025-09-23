package com.iie.st10320489.stylu.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.iie.st10320489.stylu.MainActivity
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.ActivityLoginBinding
import com.iie.st10320489.stylu.databinding.ActivityWelcomeBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            // Route to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish() // optional: remove login from back stack
        }

        binding.btnBiometric.setOnClickListener {
            // For now, route to MainActivity as well
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
