package com.iie.st10320489.stylu.ui.wardrobe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.iie.st10320489.stylu.databinding.FragmentWardrobeBinding

class WardrobeFragment : Fragment() {

    private var _binding: FragmentWardrobeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val wardrobeViewModel =
            ViewModelProvider(this).get(WardrobeViewModel::class.java)

        _binding = FragmentWardrobeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textWardrobe
        wardrobeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}