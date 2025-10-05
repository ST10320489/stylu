package com.iie.st10320489.stylu.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.network.UpdateProfileRequest
import kotlinx.coroutines.launch

class ProfileSettingsFragment : Fragment() {

    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

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
        lifecycleScope.launch {
            try {
                showLoading(true)

                // Fetch profile from API
                val result = apiService.getCurrentProfile()

                result.onSuccess { profile ->
                    // Populate fields with profile data
                    etFirstName.setText(profile.firstName ?: "")
                    etLastName.setText(profile.lastName ?: "")
                    etEmail.setText(profile.email ?: "")
                    etPhone.setText(profile.phoneNumber ?: "")
                    etPassword.setText("") // Never show password

                    // Make email read-only (users shouldn't change their email easily)
                    etEmail.isEnabled = false

                    Toast.makeText(requireContext(), "Profile loaded successfully", Toast.LENGTH_SHORT).show()
                }.onFailure { exception ->
                    val message = exception.message ?: "Failed to load profile"
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
        btnSave.isEnabled = !isLoading
        btnCancel.isEnabled = !isLoading
        etFirstName.isEnabled = !isLoading
        etLastName.isEnabled = !isLoading
        etPhone.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading

        if (isLoading) {
            btnSave.text = "Saving..."
        } else {
            btnSave.text = "Save Changes"
        }
    }
}