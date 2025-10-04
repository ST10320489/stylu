package com.iie.st10320489.stylu.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.iie.st10320489.stylu.R

class ProfileFragment : Fragment() {

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

        // Navigate to Profile Settings
        view.findViewById<ImageView>(R.id.profileBtn).setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_profileSettings)
        }

        // Navigate to System Settings
        view.findViewById<ImageView>(R.id.systemBtn).setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_systemSettings)
        }
    }
}
