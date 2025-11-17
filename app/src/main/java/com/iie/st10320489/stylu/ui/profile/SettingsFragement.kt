package com.iie.st10320489.stylu.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.iie.st10320489.stylu.R

import com.iie.st10320489.stylu.repository.AuthRepository
import com.iie.st10320489.stylu.ui.auth.BiometricStatus
import com.iie.st10320489.stylu.ui.auth.LoginActivity
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var authRepository: AuthRepository
    private lateinit var profileBtn: ImageView
    private lateinit var systemBtn: ImageView
    private lateinit var logoutBtn: LinearLayout
    private lateinit var biometricSwitch: Switch
    private lateinit var biometricContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        authRepository = AuthRepository(requireContext())

        initializeViews(view)
        setupBiometricUI()
        setupClickListeners()

        return view
    }

    private fun initializeViews(view: View) {
        profileBtn = view.findViewById(R.id.profileBtn)
        systemBtn = view.findViewById(R.id.systemBtn)
        logoutBtn = view.findViewById(R.id.logoutBtn)


        try {
            biometricContainer = view.findViewById(R.id.biometricContainer)
            biometricSwitch = view.findViewById(R.id.biometricSwitch)
        } catch (e: Exception) {

        }
    }

    private fun setupBiometricUI() {

        if (::biometricSwitch.isInitialized) {
            when (authRepository.isBiometricAvailable()) {
                BiometricStatus.AVAILABLE -> {
                    biometricContainer.visibility = View.VISIBLE
                    biometricSwitch.isChecked = authRepository.isBiometricEnabled()

                    biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked && !authRepository.isBiometricEnabled()) {

                            biometricSwitch.isChecked = false
                            Toast.makeText(
                                requireContext(),
                                "Please login again to enable biometric authentication",
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (!isChecked && authRepository.isBiometricEnabled()) {
                            // Disable biometric
                            showDisableBiometricDialog()
                        }
                    }
                }
                else -> {
                    if (::biometricContainer.isInitialized) {
                        biometricContainer.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun showDisableBiometricDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Disable Biometric Login")
            .setMessage("You will need to enter your password next time you login.")
            .setPositiveButton("Disable") { _, _ ->
                authRepository.disableBiometric()
                biometricSwitch.isChecked = false
                Toast.makeText(requireContext(), "Biometric login disabled", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                biometricSwitch.isChecked = true
            }
            .show()
    }

    private fun setupClickListeners() {
        profileBtn.setOnClickListener {

            // TODO: Implement navigation to ProfileFragment or ProfileActivity
            Toast.makeText(requireContext(), "Profile Settings", Toast.LENGTH_SHORT).show()
        }

        systemBtn.setOnClickListener {

            // TODO: Implement navigation to SystemSettingsFragment or SystemActivity
            Toast.makeText(requireContext(), "System Settings", Toast.LENGTH_SHORT).show()
        }

        logoutBtn.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun showLogoutConfirmationDialog() {
        val message = if (authRepository.isBiometricEnabled()) {
            "Are you sure you want to logout?\n\nBiometric login will be disabled."
        } else {
            "Are you sure you want to logout?"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage(message)
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        // Show loading state if needed
        logoutBtn.isEnabled = false

        lifecycleScope.launch {
            try {
                // Sign out using AuthRepository
                authRepository.signOut()
                    .onSuccess {
                        Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
                        navigateToLogin()
                    }
                    .onFailure { exception ->
                        logoutBtn.isEnabled = true
                        Toast.makeText(
                            requireContext(),
                            "Logout failed: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            } catch (e: Exception) {
                logoutBtn.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    "Error during logout: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onResume() {
        super.onResume()
        // Refresh biometric status when returning to settings
        if (::biometricSwitch.isInitialized) {
            biometricSwitch.isChecked = authRepository.isBiometricEnabled()
        }
    }
}