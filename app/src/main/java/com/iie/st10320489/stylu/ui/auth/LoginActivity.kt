package com.iie.st10320489.stylu.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.iie.st10320489.stylu.MainActivity
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.repository.AuthRepository
import com.iie.st10320489.stylu.service.MyFirebaseMessagingService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnGoogleSignIn: Button
    private lateinit var btnBiometric: ImageButton
    private lateinit var btnTogglePassword: ImageButton
    private lateinit var txtLink: TextView

    private lateinit var authViewModel: AuthViewModel
    private lateinit var authRepository: AuthRepository

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authRepository = AuthRepository(this)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        setContentView(R.layout.activity_login)
        initializeViews()
        setupBiometricUI()
        setupClickListeners()
        observeAuthState()

        // Check biometric and trigger if enabled
        if (authRepository.isBiometricEnabled() &&
            authRepository.isBiometricAvailable() == BiometricStatus.AVAILABLE) {
            Log.d("LoginActivity", "Biometric enabled - triggering prompt")
            btnBiometric.postDelayed({ showBiometricPrompt() }, 500)
        }
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            authViewModel.authState.collectLatest { state ->
                when (state) {
                    is AuthState.Success -> {
                        setButtonsEnabled(true)
                        val email = etEmail.text.toString().trim()
                        val password = etPassword.text.toString()

                        Log.d("DEBUG", "Login SUCCESS")

                        if (authRepository.isBiometricAvailable() == BiometricStatus.AVAILABLE &&
                            email.isNotEmpty() && password.isNotEmpty()) {

                            if (authRepository.isBiometricEnabled()) {
                                // Already enabled - just update and continue
                                authRepository.enableBiometric(email, password)
                                Log.d("DEBUG", "Updated biometric credentials")

                                // Continue immediately
                                registerFCMToken()
                                navigateToMain()
                            } else {
                                Log.d("DEBUG", "Showing biometric enable dialog...")
                                showEnableBiometricDialog(email, password)
                            }
                        } else {
                            Log.d("DEBUG", "Biometric not available, continuing...")
                            registerFCMToken()
                            navigateToMain()
                        }
                    }
                    is AuthState.Error -> {
                        setButtonsEnabled(true)
                        Log.e("DEBUG", "Login ERROR: ${state.message}")
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> setButtonsEnabled(true)
                }
            }
        }
    }

    private fun initializeViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        btnBiometric = findViewById(R.id.btnBiometric)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        txtLink = findViewById(R.id.txtLink)
    }

    private fun setupBiometricUI() {
        when (authRepository.isBiometricAvailable()) {
            BiometricStatus.AVAILABLE -> {
                btnBiometric.visibility = View.VISIBLE
                btnBiometric.alpha = if (authRepository.isBiometricEnabled()) 1.0f else 0.5f
            }
            BiometricStatus.NONE_ENROLLED -> {
                btnBiometric.visibility = View.VISIBLE
                btnBiometric.alpha = 0.5f
            }
            else -> {
                btnBiometric.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (validateInputs(email, password)) {
                setButtonsEnabled(false)
                authViewModel.signIn(email, password)
            }
        }

        btnBiometric.setOnClickListener {
            showBiometricPrompt()
        }

        btnTogglePassword.setOnClickListener {
            togglePasswordVisibility()
        }

        btnGoogleSignIn.setOnClickListener {
            setButtonsEnabled(false)
            lifecycleScope.launch {
                authViewModel.signInWithGoogle(this@LoginActivity)
            }
        }

        txtLink.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun togglePasswordVisibility() {
        if (isPasswordVisible) {
            etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            btnTogglePassword.setImageResource(R.drawable.ic_eye_close)
            isPasswordVisible = false
        } else {
            etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            btnTogglePassword.setImageResource(R.drawable.ic_eye_open)
            isPasswordVisible = true
        }
        etPassword.setSelection(etPassword.text.length)
    }

    private fun showBiometricPrompt() {
        Log.d("DEBUG", "showBiometricPrompt called")

        if (authRepository.isBiometricAvailable() != BiometricStatus.AVAILABLE) {
            Toast.makeText(this, "Biometric authentication not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (!authRepository.isBiometricEnabled()) {
            Toast.makeText(this, "Please log in with password first", Toast.LENGTH_SHORT).show()
            return
        }

        val credentials = authRepository.getBiometricCredentials()
        if (credentials == null) {
            Log.e("DEBUG", "Biometric enabled but NO credentials!")
            Toast.makeText(this, "Please log in with password", Toast.LENGTH_SHORT).show()
            authRepository.disableBiometric()
            btnBiometric.alpha = 0.5f
            return
        }

        authRepository.getBiometricManager().showBiometricPrompt(
            activity = this,
            title = "Login to Stylu",
            subtitle = "Use your fingerprint or face to log in",
            negativeButtonText = "Use Password",
            onSuccess = { _ ->
                val (email, password) = credentials
                Log.d("DEBUG", "Biometric SUCCESS")
                setButtonsEnabled(false)
                authViewModel.signIn(email, password)
            },
            onError = { errorCode, errString ->
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(this, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }
            },
            onFailed = {
                Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
            }
        )
    }


    private fun showEnableBiometricDialog(email: String, password: String) {
        Log.d("DEBUG", "💬 Showing enable biometric dialog")

        AlertDialog.Builder(this)
            .setTitle("Enable Biometric Login?")
            .setMessage("Use fingerprint or face recognition to log in faster next time?")
            .setCancelable(false)
            .setPositiveButton("Enable") { _, _ ->
                Log.d("DEBUG", "User clicked ENABLE")
                authRepository.enableBiometric(email, password)
                btnBiometric.alpha = 1.0f

                // Verify
                val enabled = authRepository.isBiometricEnabled()
                Log.d("DEBUG", "   - Biometric enabled: $enabled")

                Toast.makeText(this, "Biometric login enabled", Toast.LENGTH_SHORT).show()


                registerFCMToken()
                navigateToMain()
            }
            .setNegativeButton("Not Now") { _, _ ->
                Log.d("DEBUG", "User clicked NOT NOW")


                registerFCMToken()
                navigateToMain()
            }
            .show()
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            etEmail.error = "Email is required"
            etEmail.requestFocus()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Enter a valid email"
            etEmail.requestFocus()
            return false
        }

        if (password.isEmpty()) {
            etPassword.error = "Password is required"
            etPassword.requestFocus()
            return false
        }

        return true
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnLogin.isEnabled = enabled
        btnGoogleSignIn.isEnabled = enabled
        btnBiometric.isEnabled = enabled
    }

    private fun registerFCMToken() {
        lifecycleScope.launch {
            try {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                Log.d("FCM_TOKEN", "Token: ${token.take(20)}...")

                getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("fcm_token", token)
                    .apply()

                MyFirebaseMessagingService.registerTokenAfterLogin(this@LoginActivity)
            } catch (e: Exception) {
                Log.e("FCM_TOKEN", "Failed: ${e.message}")
            }
        }
    }

    private fun navigateToMain() {
        Log.d("DEBUG", "Navigating to MainActivity")

        startActivity(Intent(this@LoginActivity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}