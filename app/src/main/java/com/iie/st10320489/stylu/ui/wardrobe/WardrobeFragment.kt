package com.iie.st10320489.stylu.ui.wardrobe

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import kotlinx.coroutines.launch

class WardrobeFragment : Fragment() {

    private lateinit var rvOutfits: RecyclerView
    private lateinit var categoryContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var apiService: ApiService
    private lateinit var outfitAdapter: OutfitAdapter

    private var allOutfits: List<ApiService.OutfitDetail> = emptyList()
    private var selectedCategory: String = "All"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wardrobe, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiService = ApiService(requireContext())

        initializeViews(view)
        setupRecyclerView()
        loadOutfits()
    }

    private fun initializeViews(view: View) {
        rvOutfits = view.findViewById(R.id.rvOutfits)
        categoryContainer = view.findViewById(R.id.categoryContainer)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun setupRecyclerView() {
        rvOutfits.layoutManager = GridLayoutManager(requireContext(), 2)

        outfitAdapter = OutfitAdapter { outfit ->
            val bundle = Bundle().apply {
                putInt("outfitId", outfit.outfitId)
            }
            findNavController().navigate(R.id.action_wardrobe_to_outfit_detail, bundle)
        }

        rvOutfits.adapter = outfitAdapter
    }

    private fun loadOutfits() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                val result = apiService.getUserOutfits()
                result.onSuccess { outfits ->
                    allOutfits = outfits
                    setupCategoryButtons()
                    filterOutfits(selectedCategory)
                }.onFailure { error ->
                    Toast.makeText(requireContext(), "Failed to load outfits: ${error.message}", Toast.LENGTH_SHORT).show()
                    outfitAdapter.submitList(emptyList())
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupCategoryButtons() {
        categoryContainer.removeAllViews()

        val categoryCounts = allOutfits.groupingBy { it.category ?: "Other" }.eachCount()
        val categories = listOf("All" to allOutfits.size) + categoryCounts.toList()

        for ((category, count) in categories) {
            val button = Button(requireContext()).apply {
                text = "$category ($count)"
                textSize = 16f
                setPadding(24, 0, 24, 0)
                isAllCaps = false

                val isSelected = category == selectedCategory
                background = ContextCompat.getDrawable(
                    requireContext(),
                    if (isSelected) R.drawable.btn_primary_bg else R.drawable.btn_secondary_bg
                )
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#5A2E5A"))

                setOnClickListener {
                    selectedCategory = category
                    setupCategoryButtons()
                    filterOutfits(category)
                }
            }

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (33 * resources.displayMetrics.density).toInt()
            )
            layoutParams.setMargins(0, 0, (8 * resources.displayMetrics.density).toInt(), 0)
            button.layoutParams = layoutParams

            categoryContainer.addView(button)
        }
    }

    private fun filterOutfits(category: String) {
        val filtered = if (category == "All") {
            allOutfits
        } else {
            allOutfits.filter { (it.category ?: "Other") == category }
        }
        outfitAdapter.submitList(filtered)
    }

    override fun onResume() {
        super.onResume()
        loadOutfits()
    }
}