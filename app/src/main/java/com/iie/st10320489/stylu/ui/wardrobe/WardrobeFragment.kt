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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ✅ ONLINE-FIRST WardrobeFragment with Offline Support
 * ✅ FIXED: Ensures items are fully synced before loading outfits
 *
 * Features:
 * - Loads items FIRST and waits for cache to complete
 * - Then loads outfits (which need items to display)
 * - Falls back to cache when offline
 * - Pull-to-refresh support
 * - Real-time UI updates via Flow
 */
class WardrobeFragment : Fragment() {

    private lateinit var rvOutfits: RecyclerView
    private lateinit var categoryContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var outfitRepository: OutfitRepository
    private lateinit var itemRepository: ItemRepository
    private lateinit var outfitAdapter: OutfitAdapter

    private var allOutfits: List<ApiService.OutfitDetail> = emptyList()
    private var selectedCategory: String = "All"
    private var observerJob: Job? = null
    private var isFirstLoad = true

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

        // Load items first (required for outfit display), then outfits
        loadItemsAndOutfits()
    }

    private fun initializeViews(view: View) {
        rvOutfits = view.findViewById(R.id.rvOutfits)
        categoryContainer = view.findViewById(R.id.categoryContainer)
        progressBar = view.findViewById(R.id.progressBar)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
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
            loadItemsAndOutfits(forceRefresh = true)
        }

        swipeRefresh.setColorSchemeResources(
            R.color.purple_primary,
            R.color.orange_secondary
        )
    }

    /**
     * ✅ FIXED: Ensures items are fully synced before loading outfits
     *
     * STEP 1: Load items and WAIT for them to be cached
     * STEP 2: Load outfits (which can now find items in cache)
     * STEP 3: Observe cache for real-time updates
     */
    private fun loadItemsAndOutfits(forceRefresh: Boolean = false) {
        // Cancel any existing observer
        observerJob?.cancel()

        lifecycleScope.launch {
            try {
                // Show loading only on first load
                if (isFirstLoad && !forceRefresh) {
                    showLoadingWithMessage(
                        show = true,
                        message = "Loading your wardrobe...\n\nFirst load may take up to 60 seconds."
                    )
                } else {
                    progressBar.visibility = View.VISIBLE
                }

                // ✅ STEP 1: Load items into cache and WAIT for completion
                Log.d(TAG, "📦 Step 1: Loading items into cache...")
                var itemsLoaded = false
                var itemCount = 0

                itemRepository.getUserItems(forceRefresh = forceRefresh).collect { itemResult ->
                    itemResult.onSuccess { items ->
                        itemCount = items.size
                        Log.d(TAG, "✅ Items loaded: $itemCount items")
                        itemsLoaded = true

                        // ✅ Wait a moment for cache writes to complete
                        delay(500)

                        // ✅ STEP 2: Now load outfits (items are in cache!)
                        Log.d(TAG, "👕 Step 2: Loading outfits (items are cached)...")
                        loadOutfitsAfterItemsReady(forceRefresh, itemCount)

                    }.onFailure { error ->
                        Log.e(TAG, "❌ Failed to load items: ${error.message}")
                        handleLoadError(error)

                        // Still try to load outfits from cache
                        if (!itemsLoaded) {
                            Log.d(TAG, "⚠️ Attempting to load outfits with cached items...")
                            loadOutfitsAfterItemsReady(forceRefresh, 0)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading wardrobe", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
                showLoadingWithMessage(show = false, message = "")
                swipeRefresh.isRefreshing = false
            }
        }
    }

    /**
     * ✅ Load outfits AFTER items are ready in cache
     */
    private suspend fun loadOutfitsAfterItemsReady(forceRefresh: Boolean, itemCount: Int) {
        try {
            outfitRepository.getAllOutfits(forceRefresh = forceRefresh).collect { outfitResult ->
                outfitResult.onSuccess { outfits ->
                    Log.d(TAG, "✅ Outfits loaded: ${outfits.size} outfits")

                    // ✅ Validate that outfits have items
                    val validOutfits = outfits.filter { it.items.isNotEmpty() }
                    val emptyOutfits = outfits.filter { it.items.isEmpty() }

                    if (emptyOutfits.isNotEmpty()) {
                        Log.w(TAG, "⚠️ ${emptyOutfits.size} outfits have no items!")
                        emptyOutfits.forEach { outfit ->
                            Log.w(TAG, "   - Outfit: ${outfit.name} (ID: ${outfit.outfitId}) has 0 items")
                        }
                    }

                    Log.d(TAG, "✅ Valid outfits: ${validOutfits.size} (with items)")

                    // Convert to ApiService.OutfitDetail for adapter
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

                    setupCategoryButtonsSafe()
                    filterOutfits(selectedCategory)
                    isFirstLoad = false

                    // Hide loading indicators
                    progressBar.visibility = View.GONE
                    showLoadingWithMessage(show = false, message = "")
                    swipeRefresh.isRefreshing = false

                    // Show summary
                    val summary = "Loaded $itemCount items and ${allOutfits.size} outfits"
                    Log.d(TAG, "✅ $summary")

                }.onFailure { error ->
                    handleLoadError(error)

                    // Hide loading indicators
                    progressBar.visibility = View.GONE
                    showLoadingWithMessage(show = false, message = "")
                    swipeRefresh.isRefreshing = false
                }
            }

            // ✅ STEP 3: Observe cache for real-time updates
            observeOutfitCache()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading outfits", e)
            progressBar.visibility = View.GONE
            showLoadingWithMessage(show = false, message = "")
            swipeRefresh.isRefreshing = false
        }
    }

    /**
     * ✅ Observe outfit cache for real-time updates
     */
    private fun observeOutfitCache() {
        observerJob?.cancel()

        observerJob = lifecycleScope.launch {
            try {
                outfitRepository.getAllOutfitsFlow().collect { outfits ->
                    Log.d(TAG, "🔄 Cache update: ${outfits.size} outfits")

                    // Update allOutfits with cached data
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

                    setupCategoryButtonsSafe()
                    filterOutfits(selectedCategory)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error observing cache", e)
            }
        }
    }

    private fun handleLoadError(error: Throwable) {
        val errorMessage = when {
            error.message?.contains("timed out", ignoreCase = true) == true -> {
                "Server is starting up. This can take up to 60 seconds.\n\nPlease try again."
            }
            error.message?.contains("authentication", ignoreCase = true) == true -> {
                "Session expired. Please log in again."
            }
            error.message?.contains("cached data", ignoreCase = true) == true -> {
                "📱 Offline mode - showing cached outfits"
            }
            error.message?.contains("connect", ignoreCase = true) == true -> {
                "📱 No internet - showing cached outfits"
            }
            else -> "Error loading outfits: ${error.message}"
        }

        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()

        // Show empty state only if we have no cached data
        if (allOutfits.isEmpty()) {
            outfitAdapter.submitList(emptyList())
        }
    }

    private fun setupCategoryButtonsSafe() {
        if (!isAdded) return

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
                    setupCategoryButtonsSafe()
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

        Log.d(TAG, "🔍 Filtering: $category - ${filtered.size} outfits")

        // ✅ Log which outfits are being displayed
        filtered.forEachIndexed { index, outfit ->
            Log.d(TAG, "  ${index + 1}. ${outfit.name} (${outfit.items.size} items)")
        }

        outfitAdapter.submitList(filtered)
    }

    private fun showLoadingWithMessage(show: Boolean, message: String) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show && message.isNotEmpty()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - refreshing wardrobe")
        isFirstLoad = false // Don't show "first load" message on resume
        loadItemsAndOutfits(forceRefresh = false) // Use cache, but refresh if stale
    }

    override fun onDestroyView() {
        super.onDestroyView()
        observerJob?.cancel()
    }
}