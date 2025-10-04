package com.iie.st10320489.stylu.ui.profile/*
package com.iie.st10320489.stylu.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.network.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ProfileState {
    object Idle : ProfileState()
    object Loading : ProfileState()
    data class Success(val profile: UserProfile) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

class ProfileViewModel : ViewModel() {
    private val apiService = ApiService()

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Idle)
    val profileState: StateFlow<ProfileState> = _profileState

    fun loadUserProfile() {
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading

            apiService.getUserProfile()
                .onSuccess { profile ->
                    _profileState.value = ProfileState.Success(profile)
                }
                .onFailure { exception ->
                    _profileState.value = ProfileState.Error(exception.message ?: "Failed to load profile")
                }
        }
    }
}
*/
