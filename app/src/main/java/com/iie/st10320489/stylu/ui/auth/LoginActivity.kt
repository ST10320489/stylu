package com.iie.st10320489.stylu.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iie.st10320489.stylu.MainActivity
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authViewModel: AuthViewModel by viewModels()
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeAuthState()
    }

    private fun setupUI() {
        // Password visibility toggle
        binding.btnTogglePassword.setOnClickListener {
            togglePasswordVisibility()
        }

        // Login button
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (validateInput(email, password)) {
                authViewModel.signIn(email, password)
            }
        }

        // Navigate to register
        binding.txtLink.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        // Biometric login (placeholder)
        binding.btnBiometric.setOnClickListener {
            Toast.makeText(this, "Biometric login coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePasswordVisibility() {
        if (isPasswordVisible) {
            binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            binding.btnTogglePassword.setImageResource(R.drawable.ic_eye_close)
        } else {
            binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
            binding.btnTogglePassword.setImageResource(R.drawable.ic_eye_open)
        }
        isPasswordVisible = !isPasswordVisible
        binding.etPassword.setSelection(binding.etPassword.text.length)
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email"
            isValid = false
        } else {
            binding.etEmail.error = null
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            isValid = false
        } else {
            binding.etPassword.error = null
        }

        return isValid
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Loading -> {
                        binding.btnLogin.isEnabled = false
                        binding.btnLogin.text = "Signing in..."
                    }
                    is AuthState.Success -> {
                        Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                    is AuthState.Error -> {
                        binding.btnLogin.isEnabled = true
                        binding.btnLogin.text = "Login"
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    is AuthState.Idle -> {
                        binding.btnLogin.isEnabled = true
                        binding.btnLogin.text = "Login"
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        authViewModel.resetState()
    }
}