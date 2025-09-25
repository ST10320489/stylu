// AuthViewModel.kt
package com.iie.st10320489.stylu.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iie.st10320489.stylu.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    private val authRepository = AuthRepository()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun signUp(email: String, password: String, firstName: String, lastName: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.signUp(email, password, firstName, lastName)
                .onSuccess { message ->
                    _authState.value = AuthState.Success(message)
                }
                .onFailure { exception ->
                    val errorMessage = when {
                        exception.message?.contains("User already registered") == true ->
                            "An account with this email already exists"
                        exception.message?.contains("Invalid email") == true ->
                            "Please enter a valid email address"
                        exception.message?.contains("Password should be at least 6 characters") == true ->
                            "Password must be at least 6 characters long"
                        else -> exception.message ?: "Sign up failed"
                    }
                    _authState.value = AuthState.Error(errorMessage)
                }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.signIn(email, password)
                .onSuccess { accessToken ->
                    _authState.value = AuthState.Success("Login successful")
                }
                .onFailure { exception ->
                    val errorMessage = when {
                        exception.message?.contains("Invalid login credentials") == true ->
                            "Invalid email or password"
                        exception.message?.contains("Email not confirmed") == true ->
                            "Please check your email and confirm your account first"
                        exception.message?.contains("Too many requests") == true ->
                            "Too many login attempts. Please try again later"
                        else -> exception.message ?: "Login failed"
                    }
                    _authState.value = AuthState.Error(errorMessage)
                }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}