package com.iie.st10320489.stylu.ui.wardrobe

import android.graphics.Color
import android.os.Bundle
import android.util.Log
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.repository.ItemRepository
import com.iie.st10320489.stylu.repository.OutfitRepository
import kotlinx.coroutines.launch
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import kotlinx.coroutines.Job

/**
 * ✅ SIMPLIFIED: No complex caching, no infinite loops
 * - Load data on demand
 * - Simple refresh logic
 * - No Flow observers causing loops
 */
class WardrobeFragment : Fragment() {

    private lateinit var rvOutfits: RecyclerView
    private lateinit var categoryContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var etSearch: EditText

    private lateinit var outfitRepository: OutfitRepository
    private lateinit var itemRepository: ItemRepository
    private lateinit var outfitAdapter: OutfitAdapter

    private var allOutfits: List<ApiService.OutfitDetail> = emptyList()
    private var selectedCategory: String = "All"
    private var searchQuery: String = ""
    private var searchJob: Job? = null

    companion object {
        private const val TAG = "WardrobeFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wardrobe, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        outfitRepository = OutfitRepository(requireContext())
        itemRepository = ItemRepository(requireContext())

        initializeViews(view)
        setupRecyclerView()
        setupSwipeRefresh()
        setupSearch()

        // ✅ Load once on create
        loadOutfits()
    }

    private fun initializeViews(view: View) {
        rvOutfits = view.findViewById(R.id.rvOutfits)
        categoryContainer = view.findViewById(R.id.categoryContainer)
        progressBar = view.findViewById(R.id.progressBar)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        etSearch = view.findViewById(R.id.etSearch)
    }

    private fun setupRecyclerView() {
        rvOutfits.layoutManager = GridLayoutManager(requireContext(), 2)

        outfitAdapter = OutfitAdapter(
            onOutfitClick = { outfit ->
                val bundle = Bundle().apply {
                    putInt("outfitId", outfit.outfitId)
                }
                findNavController().navigate(R.id.action_wardrobe_to_outfit_detail, bundle)
            }
        )

        rvOutfits.adapter = outfitAdapter
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            loadOutfits()
        }

        swipeRefresh.setColorSchemeResources(
            R.color.purple_primary,
            R.color.orange_secondary
        )
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)  // debounce
                    filterOutfits()
                }
            }
        })
    }

    /**
     * ✅ Simple load - no loops, no caching complexity
     */
    // ✅ REPLACE these methods in your WardrobeFragment.kt

    private fun loadOutfits() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                // Get outfits from API
                val result = outfitRepository.getAllOutfits()

                result.onSuccess { outfits ->
                    Log.d(TAG, "✅ Loaded ${outfits.size} outfits")

                    // Convert to adapter format
                    allOutfits = outfits.map { outfit ->
                        ApiService.OutfitDetail(
                            outfitId = outfit.outfitId,
                            userId = outfit.userId,
                            name = outfit.name,
                            category = outfit.category,
                            schedule = outfit.schedule,
                            items = outfit.items.map { item ->
                                ApiService.OutfitItemDetail(
                                    itemId = item.itemId,
                                    name = item.name,
                                    imageUrl = item.imageUrl,
                                    colour = item.colour,
                                    subcategory = item.subcategory,
                                    layoutData = item.layoutData
                                )
                            },
                            createdAt = outfit.createdAt
                        )
                    }

                    setupCategoryButtons()
                    filterOutfits()

                }.onFailure { error ->
                    Log.e(TAG, "❌ Failed to load outfits: ${error.message}")
                    Toast.makeText(
                        requireContext(),
                        "Failed to load outfits: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading outfits", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }

    // ✅ FIXED: Use predefined categories matching CreateOutfitFragment
    private fun setupCategoryButtons() {
        if (!isAdded) return

        categoryContainer.removeAllViews()

        // ✅ Must match categories in CreateOutfitFragment dialog
        val predefinedCategories = arrayOf("Casual", "Formal", "Sport", "Party", "Work", "Other")

        // Count outfits per category
        val categoryCounts = mutableMapOf<String, Int>()
        predefinedCategories.forEach { category ->
            categoryCounts[category] = allOutfits.count {
                (it.category ?: "Other") == category
            }
        }

        // Build button list: "All" first, then categories with outfits
        val allCount = allOutfits.size
        val categories = buildList {
            add("All" to allCount)
            predefinedCategories.forEach { category ->
                val count = categoryCounts[category] ?: 0
                if (count > 0) { // ✅ Only show categories with outfits
                    add(category to count)
                }
            }
        }

        Log.d(TAG, "📊 Category buttons: ${categories.joinToString { "${it.first}(${it.second})" }}")

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
                    Log.d(TAG, "🔘 Selected category: $category")
                    setupCategoryButtons()
                    filterOutfits()
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

    private fun filterOutfits() {
        var filtered = if (selectedCategory == "All") {
            allOutfits
        } else {
            // ✅ Match exact category, handling null as "Other"
            allOutfits.filter { (it.category ?: "Other") == selectedCategory }
        }

        // Apply search filter
        if (searchQuery.isNotEmpty()) {
            val query = searchQuery.lowercase()
            filtered = filtered.filter { outfit ->
                outfit.name.lowercase().contains(query) ||
                        outfit.category?.lowercase()?.contains(query) == true ||
                        outfit.items.any { item ->
                            item.name?.lowercase()?.contains(query) == true ||
                                    item.subcategory.lowercase().contains(query)
                        }
            }
        }

        Log.d(TAG, "🔍 Category: '$selectedCategory', Search: '$searchQuery', Results: ${filtered.size}")
        outfitAdapter.submitList(filtered)
    }

    /**
     * ✅ Only refresh on resume (not infinite loop)
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - refreshing")
        loadOutfits()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
    }
}