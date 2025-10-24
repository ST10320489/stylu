package com.iie.st10320489.stylu.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        setContentView(R.layout.activity_login)

        authRepository = AuthRepository(this)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        initializeViews()
        setupBiometricUI()
        setupClickListeners()
        observeAuthState()

        if (authRepository.isBiometricEnabled() &&
            authRepository.isBiometricAvailable() == BiometricStatus.AVAILABLE) {
            btnBiometric.post { showBiometricPrompt() }
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
        // Check if biometric is available
        when (authRepository.isBiometricAvailable()) {
            BiometricStatus.AVAILABLE -> {
                btnBiometric.visibility = View.VISIBLE

                // Show visual feedback if biometric is enabled
                if (authRepository.isBiometricEnabled()) {
                    btnBiometric.alpha = 1.0f // Full opacity
                } else {
                    btnBiometric.alpha = 0.5f // Dimmed
                }
            }
            BiometricStatus.NO_HARDWARE -> {
                btnBiometric.visibility = View.GONE
            }
            BiometricStatus.NONE_ENROLLED -> {
                btnBiometric.visibility = View.VISIBLE
                btnBiometric.alpha = 0.5f
                Toast.makeText(this, "Please set up biometric in device settings", Toast.LENGTH_SHORT).show()
            }
            BiometricStatus.HARDWARE_UNAVAILABLE -> {
                btnBiometric.visibility = View.GONE
            }
            BiometricStatus.UNSUPPORTED -> {
                btnBiometric.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (validateInputs(email, password)) {
                // Disable buttons during login
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
            // Hide password
            etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            btnTogglePassword.setImageResource(R.drawable.ic_eye_close)
            isPasswordVisible = false
        } else {
            // Show password
            etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            btnTogglePassword.setImageResource(R.drawable.ic_eye_open)
            isPasswordVisible = true
        }
        // Move cursor to end
        etPassword.setSelection(etPassword.text.length)
    }

    private fun showBiometricPrompt() {
        if (authRepository.isBiometricAvailable() != BiometricStatus.AVAILABLE) {
            Toast.makeText(this, "Biometric authentication not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (!authRepository.isBiometricEnabled()) {
            // Don’t show an error — just hide the button or dim it
            Toast.makeText(this, "Biometric login not enabled yet. Use password first.", Toast.LENGTH_SHORT).show()
            return
        }

        // Show the biometric prompt
        authRepository.getBiometricManager().showBiometricPrompt(
            activity = this,
            title = "Login to Stylu",
            subtitle = "Use your fingerprint or face to log in",
            negativeButtonText = "Use Password",
            onSuccess = { _ ->
                val credentials = authRepository.getBiometricCredentials()
                if (credentials != null) {
                    val (email, password) = credentials
                    setButtonsEnabled(false)
                    authViewModel.signIn(email, password)
                } else {
                    // Credentials missing, disable biometric
                    authRepository.disableBiometric()
                    btnBiometric.alpha = 0.5f
                }
            },
            onError = { errorCode, errString ->
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(this, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }
            },
            onFailed = {
                Toast.makeText(this, "Authentication failed. Try again.", Toast.LENGTH_SHORT).show()
            }
        )
    }


    private fun showEnableBiometricDialog(email: String, password: String) {
        AlertDialog.Builder(this)
            .setTitle("Enable Biometric Login?")
            .setMessage("Would you like to use fingerprint or face recognition to log in next time?")
            .setPositiveButton("Enable") { _, _ ->
                authRepository.enableBiometric(email, password)
                btnBiometric.alpha = 1.0f
                Toast.makeText(this, "Biometric login enabled!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Not Now", null)
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

    private fun observeAuthState() {
        lifecycleScope.launch {
            authViewModel.authState.collectLatest { state ->
                when (state) {
                    is AuthState.Success -> {
                        setButtonsEnabled(true)

                        val email = etEmail.text.toString().trim()
                        val password = etPassword.text.toString()

                        // Offer biometric enrollment only if available & not already enabled
                        if (!authRepository.isBiometricEnabled() &&
                            authRepository.isBiometricAvailable() == BiometricStatus.AVAILABLE &&
                            email.isNotEmpty() && password.isNotEmpty()) {
                            showEnableBiometricDialog(email, password)
                        }

                        // Navigate to MainActivity
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                    }
                    is AuthState.Error -> {
                        setButtonsEnabled(true)
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> setButtonsEnabled(true)
                }
            }
        }
    }

}