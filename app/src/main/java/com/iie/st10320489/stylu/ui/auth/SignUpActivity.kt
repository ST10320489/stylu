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
import com.iie.st10320489.stylu.databinding.ActivitySignupBinding
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val authViewModel: AuthViewModel by viewModels()
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeAuthState()
    }

    private fun setupUI() {
        // Password visibility toggles
        binding.btnTogglePassword.setOnClickListener {
            togglePasswordVisibility()
        }

        binding.btnToggleConfirmPassword.setOnClickListener {
            toggleConfirmPasswordVisibility()
        }

        // Sign up button
        binding.btnSignUp.setOnClickListener {
            val firstName = binding.etFirstName.text.toString().trim()
            val lastName = binding.etLastName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            if (validateInput(firstName, lastName, email, password, confirmPassword)) {
                authViewModel.signUp(email, password, firstName, lastName)
            }
        }

        // Google sign up (OAuth)
        binding.btnGoogleSignUp.setOnClickListener {
            authViewModel.signInWithGoogle(this)
        }

        // Navigate to login
        binding.txtLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
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

    private fun toggleConfirmPasswordVisibility() {
        if (isConfirmPasswordVisible) {
            binding.etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            binding.btnToggleConfirmPassword.setImageResource(R.drawable.ic_eye_close)
        } else {
            binding.etConfirmPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
            binding.btnToggleConfirmPassword.setImageResource(R.drawable.ic_eye_open)
        }
        isConfirmPasswordVisible = !isConfirmPasswordVisible
        binding.etConfirmPassword.setSelection(binding.etConfirmPassword.text.length)
    }

    private fun validateInput(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        // First name validation
        if (firstName.isEmpty()) {
            binding.etFirstName.error = "First name is required"
            isValid = false
        } else if (firstName.length < 2) {
            binding.etFirstName.error = "First name must be at least 2 characters"
            isValid = false
        } else {
            binding.etFirstName.error = null
        }

        // Last name validation
        if (lastName.isEmpty()) {
            binding.etLastName.error = "Last name is required"
            isValid = false
        } else if (lastName.length < 2) {
            binding.etLastName.error = "Last name must be at least 2 characters"
            isValid = false
        } else {
            binding.etLastName.error = null
        }

        // Email validation
        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email"
            isValid = false
        } else {
            binding.etEmail.error = null
        }

        // Password validation
        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            binding.etPassword.error = null
        }

        // Confirm password validation
        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Please confirm your password"
            isValid = false
        } else if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            isValid = false
        } else {
            binding.etConfirmPassword.error = null
        }

        return isValid
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Loading -> {
                        binding.btnSignUp.isEnabled = false
                        binding.btnSignUp.text = "Creating Account..."
                        binding.btnGoogleSignUp.isEnabled = false
                    }
                    is AuthState.OAuthInProgress -> {
                        binding.btnSignUp.isEnabled = false
                        binding.btnGoogleSignUp.isEnabled = false
                        Toast.makeText(this@SignUpActivity, "Opening Google Sign-In...", Toast.LENGTH_SHORT).show()
                    }
                    is AuthState.Success -> {
                        if (state.message.contains("Google") || state.message.contains("Already logged in")) {
                            // For Google sign-in or existing session, go directly to MainActivity
                            Toast.makeText(this@SignUpActivity, state.message, Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@SignUpActivity, MainActivity::class.java))
                            finish()
                        } else {
                            // For regular signup, show message and navigate to login
                            binding.btnSignUp.isEnabled = true
                            binding.btnSignUp.text = "Register"
                            binding.btnGoogleSignUp.isEnabled = true
                            Toast.makeText(this@SignUpActivity, state.message, Toast.LENGTH_LONG).show()
                            startActivity(Intent(this@SignUpActivity, LoginActivity::class.java))
                            finish()
                        }
                    }
                    is AuthState.Error -> {
                        binding.btnSignUp.isEnabled = true
                        binding.btnSignUp.text = "Register"
                        binding.btnGoogleSignUp.isEnabled = true
                        Toast.makeText(this@SignUpActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    is AuthState.Idle -> {
                        binding.btnSignUp.isEnabled = true
                        binding.btnSignUp.text = "Register"
                        binding.btnGoogleSignUp.isEnabled = true
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