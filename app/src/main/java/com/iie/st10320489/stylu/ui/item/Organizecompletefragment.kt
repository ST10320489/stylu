package com.iie.st10320489.stylu.ui.item

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.FragmentOrganizeCompleteBinding

class OrganizeCompleteFragment : Fragment() {

    private var _binding: FragmentOrganizeCompleteBinding? = null
    private val binding get() = _binding!!

    private var totalReviewed = 0
    private var keptCount = 0
    private var discardedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get arguments using Safe Args
        val args = OrganizeCompleteFragmentArgs.fromBundle(requireArguments())
        totalReviewed = args.totalReviewed
        keptCount = args.keptCount
        discardedCount = args.discardedCount
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrganizeCompleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        displayStatistics()
        setupButtons()
    }

    private fun displayStatistics() {
        binding.tvItemsReviewed.text = totalReviewed.toString()
        binding.tvItemsKept.text = keptCount.toString()
        binding.tvItemsDiscarded.text = discardedCount.toString()

        // Update summary text based on results
        val summaryText = when {
            discardedCount == 0 -> getString(R.string.organize_summary_kept_all)
            keptCount == 0 -> getString(R.string.organize_summary_discarded_all)
            else -> getString(R.string.organize_summary)
        }
        binding.tvSummary.text = summaryText
    }

    private fun setupButtons() {
        binding.btnViewDiscarded.apply {
            // Only show button if there are discarded items
            visibility = if (discardedCount > 0) View.VISIBLE else View.GONE

            setOnClickListener {
                try {
                    findNavController().navigate(R.id.action_complete_to_discardedItems)
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Navigation error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.btnDone.setOnClickListener {
            // Navigate back to wardrobe items
            try {
                findNavController().navigate(R.id.navigation_item)
            } catch (e: Exception) {
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}