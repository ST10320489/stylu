package com.iie.st10320489.stylu.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.repository.AuthRepository
import com.iie.st10320489.stylu.ui.auth.WelcomeActivity
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private lateinit var authRepository: AuthRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize AuthRepository
        authRepository = AuthRepository(requireContext())

        // Navigate to Profile Settings
        view.findViewById<ImageView>(R.id.profileBtn).setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_profileSettings)
        }

        // Navigate to System Settings
        view.findViewById<ImageView>(R.id.systemBtn).setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_systemSettings)
        }

        // Handle Logout
        view.findViewById<LinearLayout>(R.id.logoutBtn).setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        lifecycleScope.launch {
            authRepository.signOut()
                .onSuccess {
                    // Navigate to Welcome/Login screen
                    val intent = Intent(requireContext(), WelcomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
                .onFailure { exception ->
                    Toast.makeText(
                        requireContext(),
                        "Logout failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}