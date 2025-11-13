package com.iie.st10320489.stylu.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.network.UpdateProfileRequest
import com.iie.st10320489.stylu.utils.CachedProfile
import com.iie.st10320489.stylu.utils.ProfileCacheManager
import kotlinx.coroutines.launch

class ProfileSettingsFragment : Fragment() {

    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var contentLayout: LinearLayout

    private lateinit var apiService: ApiService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile_settings, container, false)

        // Initialize API Service
        apiService = ApiService(requireContext())

        initializeViews(view)
        setupClickListeners()
        fetchProfile()

        return view
    }

    private fun initializeViews(view: View) {
        etFirstName = view.findViewById(R.id.etFirstName)
        etLastName = view.findViewById(R.id.etUsername)
        etEmail = view.findViewById(R.id.etEmail)
        etPhone = view.findViewById(R.id.etCellNumber)
        etPassword = view.findViewById(R.id.etAddress)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)

        progressBar = view.findViewById(R.id.progressBar)
        contentLayout = view.findViewById(R.id.contentLayout)
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            saveProfile()
        }

        btnCancel.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun fetchProfile() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                // ✅ 1. Load cached data immediately if available
                val cached = ProfileCacheManager.getProfile(requireContext())
                if (cached != null) {
                    etFirstName.setText(cached.firstName ?: "")
                    etLastName.setText(cached.lastName ?: "")
                    etEmail.setText(cached.email ?: "")
                    etPhone.setText(cached.phoneNumber ?: "")
                }

                // ✅ 2. Check Internet before API call
                if (!isInternetAvailable()) {
                    Toast.makeText(
                        requireContext(),
                        "No internet connection. Showing last saved profile.",
                        Toast.LENGTH_LONG
                    ).show()
                    showLoading(false)
                    return@launch
                }

                // ✅ 3. Fetch fresh data from API
                val result = apiService.getCurrentProfile()

                result.onSuccess { profile ->
                    etFirstName.setText(profile.firstName ?: "")
                    etLastName.setText(profile.lastName ?: "")
                    etEmail.setText(profile.email ?: "")
                    etPhone.setText(profile.phoneNumber ?: "")
                    etPassword.setText("")
                    etEmail.isEnabled = false

                    // ✅ Cache new data
                    ProfileCacheManager.saveProfile(
                        requireContext(),
                        CachedProfile(
                            profile.firstName,
                            profile.lastName,
                            profile.email,
                            profile.phoneNumber
                        )
                    )

                    Toast.makeText(requireContext(), "Profile loaded successfully", Toast.LENGTH_SHORT).show()

                }.onFailure { exception ->
                    val message = when {
                        exception.message?.contains("timeout", true) == true ->
                            "Connection timed out. Please check your internet and try again."
                        exception.message?.contains("failed to connect", true) == true ->
                            "Unable to reach server. Try again later."
                        else ->
                            "Failed to load profile: ${exception.message}"
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                val message = when {
                    e.message?.contains("timeout", true) == true ->
                        "Request timed out. Please check your connection."
                    else ->
                        "Unexpected error: ${e.message}"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
    }



    private fun saveProfile() {
        lifecycleScope.launch {
            try {
                showLoading(true)

                // Get values from fields
                val firstName = etFirstName.text.toString().trim()
                val lastName = etLastName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString()

                // Validate required fields
                if (firstName.isEmpty() || lastName.isEmpty()) {
                    Toast.makeText(requireContext(), "First name and last name are required", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Validate email
                if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(requireContext(), "Please enter a valid email", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Validate password if provided
                if (password.isNotEmpty() && password.length < 6) {
                    Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Create update request
                val updateRequest = UpdateProfileRequest(
                    firstName = firstName,
                    lastName = lastName,
                    phoneNumber = if (phone.isEmpty()) null else phone,
                    email = email,
                    password = if (password.isEmpty()) null else password
                )

                // Update profile through API
                val result = apiService.updateProfile(updateRequest)

                result.onSuccess { message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressed()
                }.onFailure { exception ->
                    val message = exception.message ?: "Failed to update profile"
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
            contentLayout.visibility = View.GONE
        } else {
            progressBar.visibility = View.GONE
            contentLayout.visibility = View.VISIBLE
        }
    }

    private fun isInternetAvailable(): Boolean {
        val cm = requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

}