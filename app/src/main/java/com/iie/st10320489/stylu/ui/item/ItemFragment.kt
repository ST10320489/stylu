package com.iie.st10320489.stylu.ui.item

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.FragmentItemBinding
import com.iie.st10320489.stylu.repository.DiscardedItemsManager
import com.iie.st10320489.stylu.repository.ItemRepository
import com.iie.st10320489.stylu.ui.item.models.ItemFilters
import com.iie.st10320489.stylu.ui.item.models.TimesWornFilter
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ItemFragment : Fragment() {

    private var _binding: FragmentItemBinding? = null
    private val binding get() = _binding!!

    private lateinit var itemRepository: ItemRepository
    private lateinit var discardedItemsManager: DiscardedItemsManager
    private lateinit var itemAdapter: ItemAdapter

    private var allItems: List<WardrobeItem> = emptyList()
    private var filteredItems: List<WardrobeItem> = emptyList()
    private var selectedCategory: String = "All"
    private var categoryCounts: Map<String, Int> = emptyMap()

    private var currentFilters = ItemFilters()
    private var searchQuery: String = ""
    private var searchJob: Job? = null

    // Track if this is the first load
    private var isFirstLoad = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize repository and managers
        itemRepository = ItemRepository(requireContext())
        discardedItemsManager = DiscardedItemsManager(requireContext())

        setupRecyclerView()
        setupSearchBar()
        setupFilterButton()
        setupOrganizeButton()
        setupSwipeRefresh()
        loadItemsWithCaching()
    }

    private fun setupRecyclerView() {
        val spanCount = 2
        val spacing = 16.dpToPx()
        val includeEdge = true

        binding.rvItems.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.rvItems.addItemDecoration(GridSpacingItemDecoration(spanCount, spacing, includeEdge))

        itemAdapter = ItemAdapter { item ->
            showItemOptions(item)
        }

        binding.rvItems.adapter = itemAdapter
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    applyFiltersAndSearch()
                }
            }
        })
    }

    private fun setupFilterButton() {
        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }
    }

    private fun setupOrganizeButton() {
        binding.btnOrganizeCloset.setOnClickListener {
            showOrganizeOptionsDialog()
        }
    }


    private fun setupSwipeRefresh() {
        binding.swipeRefresh?.setOnRefreshListener {
            // Force refresh from API
            loadItemsWithCaching(forceRefresh = true)
        }

        // Set color scheme for refresh indicator
        binding.swipeRefresh?.setColorSchemeResources(
            R.color.purple_primary,
            R.color.orange_secondary
        )
    }


    private fun loadItemsWithCaching(forceRefresh: Boolean = false) {
        lifecycleScope.launch {
            try {
                // Show loading message only on first load
                if (isFirstLoad && !forceRefresh) {
                    showLoadingWithMessage(
                        show = true,
                        message = "Loading your wardrobe...\n\n" +
                                "First load may take up to 60 seconds if the server is starting up."
                    )
                } else {
                    showLoading(true)
                }


                itemRepository.getUserItems(forceRefresh = forceRefresh)
                    .collect { result ->
                        result.onSuccess { items ->
                            // Filter out discarded items
                            allItems = items.filter { !discardedItemsManager.isItemDiscarded(it.itemId) }

                            // Calculate category counts from items
                            categoryCounts = calculateCategoryCounts(allItems)
                            setupCategoryButtons()
                            applyFiltersAndSearch()

                            // Mark first load as complete
                            isFirstLoad = false

                        }.onFailure { error ->
                            handleLoadError(error)
                        }

                        // Hide loading indicators
                        showLoading(false)
                        showLoadingWithMessage(show = false, message = "")
                        binding.swipeRefresh?.isRefreshing = false
                    }

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_message, e.message),
                    Toast.LENGTH_LONG
                ).show()
                showLoading(false)
                showLoadingWithMessage(show = false, message = "")
                binding.swipeRefresh?.isRefreshing = false
            }
        }
    }


    private fun handleLoadError(error: Throwable) {
        val errorMessage = when {
            error.message?.contains("timed out", ignoreCase = true) == true -> {
                "Server is starting up. This can take up to 60 seconds on first request.\n\n" +
                        "Please try again in a moment."
            }
            error.message?.contains("starting up", ignoreCase = true) == true -> {
                error.message ?: "Server is starting..."
            }
            error.message?.contains("authentication", ignoreCase = true) == true -> {
                "Authentication failed. Please log in again."
            }
            else -> {
                "Failed to load items: ${error.message}"
            }
        }

        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()

        // Only show empty state if we have no cached items
        if (allItems.isEmpty()) {
            itemAdapter.submitList(emptyList())
            showEmptyState(true)
        }
    }

    private fun calculateCategoryCounts(items: List<WardrobeItem>): Map<String, Int> {
        return items.groupBy { it.category }
            .mapValues { it.value.size }
    }

    @SuppressLint("SetTextI18n")
    private fun setupCategoryButtons() {
        val categoryContainer = binding.categoryContainer
        categoryContainer.removeAllViews()

        val categories = mutableListOf("All" to allItems.size)
        categories.addAll(categoryCounts.map { it.key to it.value })

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
                setTextColor(
                    if (isSelected) Color.WHITE else Color.parseColor("#5A2E5A")
                )

                setOnClickListener {
                    selectedCategory = category
                    setupCategoryButtons()
                    applyFiltersAndSearch()
                }
            }

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                33.dpToPx()
            )
            layoutParams.setMargins(0, 0, 2.dpToPx(), 0)
            button.layoutParams = layoutParams

            categoryContainer.addView(button)
        }
    }

    private fun applyFiltersAndSearch() {
        var items = if (selectedCategory == "All") {
            allItems
        } else {
            allItems.filter { it.category == selectedCategory }
        }

        if (searchQuery.isNotEmpty()) {
            val query = searchQuery.lowercase()
            items = items.filter { item ->
                item.name?.lowercase()?.contains(query) == true ||
                        item.colour?.lowercase()?.contains(query) == true ||
                        item.subcategory.lowercase().contains(query) ||
                        item.category.lowercase().contains(query)
            }
        }

        items = applyFilters(items)

        filteredItems = items
        itemAdapter.submitList(filteredItems)
        showEmptyState(filteredItems.isEmpty())
        updateActiveFiltersChips()
    }

    private fun applyFilters(items: List<WardrobeItem>): List<WardrobeItem> {
        var filtered = items

        if (currentFilters.colors.isNotEmpty()) {
            filtered = filtered.filter { item ->
                item.colour?.let { color ->
                    currentFilters.colors.any { filterColor ->
                        color.equals(filterColor, ignoreCase = true)
                    }
                } ?: false
            }
        }

        if (currentFilters.sizes.isNotEmpty()) {
            filtered = filtered.filter { item ->
                item.size?.let { size ->
                    currentFilters.sizes.contains(size)
                } ?: false
            }
        }

        if (currentFilters.weatherTags.isNotEmpty()) {
            filtered = filtered.filter { item ->
                item.weatherTag?.let { tag ->
                    currentFilters.weatherTags.contains(tag)
                } ?: false
            }
        }

        filtered = when (currentFilters.timesWornFilter) {
            TimesWornFilter.NEVER_WORN -> filtered.filter { it.timesWorn == 0 }
            TimesWornFilter.LEAST_WORN -> filtered.filter { it.timesWorn in 1..5 }
            TimesWornFilter.MOST_WORN -> filtered.filter { it.timesWorn >= 6 }
            TimesWornFilter.ALL -> filtered
        }

        return filtered
    }

    private fun showFilterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_filter_items, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val colorChipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.colorChipGroup)
        val sizeChipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.sizeChipGroup)
        val weatherChipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.weatherChipGroup)
        val timesWornRadioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.timesWornRadioGroup)
        val btnClear = dialogView.findViewById<Button>(R.id.btnClearFilters)
        val btnApply = dialogView.findViewById<Button>(R.id.btnApplyFilters)

        val tvColorLabel = dialogView.findViewById<TextView>(R.id.tvColorLabel)
        val tvSizeLabel = dialogView.findViewById<TextView>(R.id.tvSizeLabel)
        val tvWeatherLabel = dialogView.findViewById<TextView>(R.id.tvWeatherLabel)

        val availableColors = allItems.mapNotNull { it.colour }.distinct().sorted()
        if (availableColors.isNotEmpty()) {
            tvColorLabel.visibility = View.VISIBLE
            colorChipGroup.visibility = View.VISIBLE
            availableColors.forEach { color ->
                val chip = createFilterChip(color, currentFilters.colors.contains(color))
                colorChipGroup.addView(chip)
            }
        } else {
            tvColorLabel.visibility = View.GONE
            colorChipGroup.visibility = View.GONE
        }

        val availableSizes = allItems.mapNotNull { it.size }.distinct().sorted()
        if (availableSizes.isNotEmpty()) {
            tvSizeLabel.visibility = View.VISIBLE
            sizeChipGroup.visibility = View.VISIBLE
            availableSizes.forEach { size ->
                val chip = createFilterChip(size, currentFilters.sizes.contains(size))
                sizeChipGroup.addView(chip)
            }
        } else {
            tvSizeLabel.visibility = View.GONE
            sizeChipGroup.visibility = View.GONE
        }

        val availableWeather = allItems.mapNotNull { it.weatherTag }.distinct().sorted()
        if (availableWeather.isNotEmpty()) {
            tvWeatherLabel.visibility = View.VISIBLE
            weatherChipGroup.visibility = View.VISIBLE
            availableWeather.forEach { weather ->
                val chip = createFilterChip(weather, currentFilters.weatherTags.contains(weather))
                weatherChipGroup.addView(chip)
            }
        } else {
            tvWeatherLabel.visibility = View.GONE
            weatherChipGroup.visibility = View.GONE
        }

        when (currentFilters.timesWornFilter) {
            TimesWornFilter.ALL -> dialogView.findViewById<RadioButton>(R.id.rbAll).isChecked = true
            TimesWornFilter.NEVER_WORN -> dialogView.findViewById<RadioButton>(R.id.rbNeverWorn).isChecked = true
            TimesWornFilter.LEAST_WORN -> dialogView.findViewById<RadioButton>(R.id.rbLeastWorn).isChecked = true
            TimesWornFilter.MOST_WORN -> dialogView.findViewById<RadioButton>(R.id.rbMostWorn).isChecked = true
        }

        btnClear.setOnClickListener {
            currentFilters = ItemFilters()
            dialog.dismiss()
            applyFiltersAndSearch()
        }

        btnApply.setOnClickListener {
            val selectedColors = mutableSetOf<String>()
            for (i in 0 until colorChipGroup.childCount) {
                val chip = colorChipGroup.getChildAt(i) as? Chip
                if (chip?.isChecked == true) {
                    selectedColors.add(chip.text.toString())
                }
            }

            val selectedSizes = mutableSetOf<String>()
            for (i in 0 until sizeChipGroup.childCount) {
                val chip = sizeChipGroup.getChildAt(i) as? Chip
                if (chip?.isChecked == true) {
                    selectedSizes.add(chip.text.toString())
                }
            }

            val selectedWeather = mutableSetOf<String>()
            for (i in 0 until weatherChipGroup.childCount) {
                val chip = weatherChipGroup.getChildAt(i) as? Chip
                if (chip?.isChecked == true) {
                    selectedWeather.add(chip.text.toString())
                }
            }

            val timesWornFilter = when (timesWornRadioGroup.checkedRadioButtonId) {
                R.id.rbNeverWorn -> TimesWornFilter.NEVER_WORN
                R.id.rbLeastWorn -> TimesWornFilter.LEAST_WORN
                R.id.rbMostWorn -> TimesWornFilter.MOST_WORN
                else -> TimesWornFilter.ALL
            }

            currentFilters = ItemFilters(
                colors = selectedColors,
                sizes = selectedSizes,
                weatherTags = selectedWeather,
                timesWornFilter = timesWornFilter
            )

            dialog.dismiss()
            applyFiltersAndSearch()
        }

        dialog.show()
    }

    private fun createFilterChip(label: String, isChecked: Boolean): Chip {
        return Chip(requireContext()).apply {
            text = label
            isCheckable = true
            this.isChecked = isChecked

            if (isChecked) {
                setChipBackgroundColorResource(R.color.orange_secondary)
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            } else {
                setChipBackgroundColorResource(android.R.color.white)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_primary))
            }

            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    setChipBackgroundColorResource(R.color.orange_secondary)
                    setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                } else {
                    setChipBackgroundColorResource(android.R.color.white)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_primary))
                }
            }
        }
    }

    private fun updateActiveFiltersChips() {
        binding.activeFiltersChipGroup.removeAllViews()

        if (!currentFilters.isActive()) {
            binding.activeFiltersChipGroup.visibility = View.GONE
            return
        }

        binding.activeFiltersChipGroup.visibility = View.VISIBLE

        currentFilters.colors.forEach { color ->
            val chip = createActiveFilterChip(getString(R.string.filter_color, color))
            binding.activeFiltersChipGroup.addView(chip)
        }

        currentFilters.sizes.forEach { size ->
            val chip = createActiveFilterChip(getString(R.string.filter_size, size))
            binding.activeFiltersChipGroup.addView(chip)
        }

        currentFilters.weatherTags.forEach { weather ->
            val chip = createActiveFilterChip(getString(R.string.filter_weather, weather))
            binding.activeFiltersChipGroup.addView(chip)
        }

        if (currentFilters.timesWornFilter != TimesWornFilter.ALL) {
            val label = when (currentFilters.timesWornFilter) {
                TimesWornFilter.NEVER_WORN -> getString(R.string.never_worn)
                TimesWornFilter.LEAST_WORN -> getString(R.string.least_worn)
                TimesWornFilter.MOST_WORN -> getString(R.string.most_worn)
                else -> ""
            }
            val chip = createActiveFilterChip(label)
            binding.activeFiltersChipGroup.addView(chip)
        }
    }

    private fun createActiveFilterChip(label: String): Chip {
        return Chip(requireContext()).apply {
            text = label
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                removeFilter(label)
            }
        }
    }

    private fun removeFilter(label: String) {
        val updatedColors = currentFilters.colors.toMutableSet()
        val updatedSizes = currentFilters.sizes.toMutableSet()
        val updatedWeather = currentFilters.weatherTags.toMutableSet()
        var updatedTimesWorn = currentFilters.timesWornFilter

        currentFilters.colors.forEach { if (label.contains(it)) updatedColors.remove(it) }
        currentFilters.sizes.forEach { if (label.contains(it)) updatedSizes.remove(it) }
        currentFilters.weatherTags.forEach { if (label.contains(it)) updatedWeather.remove(it) }

        if (label == getString(R.string.never_worn) ||
            label == getString(R.string.least_worn) ||
            label == getString(R.string.most_worn)) {
            updatedTimesWorn = TimesWornFilter.ALL
        }

        currentFilters = ItemFilters(
            colors = updatedColors,
            sizes = updatedSizes,
            weatherTags = updatedWeather,
            timesWornFilter = updatedTimesWorn
        )

        applyFiltersAndSearch()
    }

    private fun showOrganizeOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_organize_options, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnOrganizeAll = dialogView.findViewById<Button>(R.id.btnOrganizeAll)
        val btnOrganizeLeastWorn = dialogView.findViewById<Button>(R.id.btnOrganizeLeastWorn)
        val btnViewDiscarded = dialogView.findViewById<Button>(R.id.btnViewDiscarded)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelOrganize)

        btnOrganizeAll.setOnClickListener {
            dialog.dismiss()
            navigateToOrganizeCloset(allItems)
        }

        btnOrganizeLeastWorn.setOnClickListener {
            dialog.dismiss()
            val leastWornItems = allItems.sortedBy { it.timesWorn }.take(20)
            if (leastWornItems.isEmpty()) {
                Toast.makeText(requireContext(), "No items to organize", Toast.LENGTH_SHORT).show()
            } else {
                navigateToOrganizeCloset(leastWornItems)
            }
        }

        btnViewDiscarded.setOnClickListener {
            dialog.dismiss()
            navigateToDiscardedItems()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun navigateToOrganizeCloset(items: List<WardrobeItem>) {
        try {
            val action = ItemFragmentDirections.actionItemsToOrganizeCloset(items.toTypedArray())
            findNavController().navigate(action)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.navigation_error, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun navigateToDiscardedItems() {
        try {
            findNavController().navigate(R.id.action_items_to_discardedItems)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.navigation_error, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showItemOptions(item: WardrobeItem) {
        val options = arrayOf(
            getString(R.string.view_details),
            getString(R.string.edit),
            getString(R.string.delete)
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(item.name ?: item.subcategory)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewItemDetails(item)
                    1 -> editItem(item)
                    2 -> confirmDelete(item)
                }
            }
            .show()
    }

    private fun viewItemDetails(item: WardrobeItem) {
        val na = getString(R.string.not_available)
        val message = buildString {
            append(getString(R.string.name_label, item.name ?: na) + "\n")
            append(getString(R.string.category_label, item.category) + "\n")
            append(getString(R.string.subcategory_label, item.subcategory) + "\n")
            append(getString(R.string.color_label, item.colour ?: na) + "\n")
            append(getString(R.string.size_label, item.size ?: na) + "\n")
            append(getString(R.string.weather_tag_label, item.weatherTag ?: na) + "\n")
            append(getString(R.string.times_worn_label, item.timesWorn))
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(item.name ?: item.subcategory)
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }


    private fun editItem(item: WardrobeItem) {
        try {
            val action = ItemFragmentDirections.actionItemsToEditItem(item)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.navigation_error, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmDelete(item: WardrobeItem) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_item_title))
            .setMessage(getString(R.string.delete_item_message, item.name ?: item.subcategory))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteItem(item)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteItem(item: WardrobeItem) {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val result = itemRepository.deleteItem(item.itemId)
                result.onSuccess {
                    Toast.makeText(requireContext(), getString(R.string.item_deleted), Toast.LENGTH_SHORT).show()

                    loadItemsWithCaching(forceRefresh = true)
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.failed_to_delete, error.message),
                        Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.error_message, e.message), Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoadingWithMessage(show: Boolean, message: String) {
        _binding?.let { binding ->
            if (show) {
                binding.progressBar.visibility = View.VISIBLE
                if (message.isNotEmpty()) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            } else {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showLoading(show: Boolean) {
        _binding?.progressBar?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState(show: Boolean) {
        _binding?.emptyStateLayout?.visibility = if (show) View.VISIBLE else View.GONE
        _binding?.rvItems?.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val spacing: Int,
        private val includeEdge: Boolean
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            val column = position % spanCount

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount
                outRect.right = (column + 1) * spacing / spanCount

                if (position < spanCount) {
                    outRect.top = spacing
                }
                outRect.bottom = spacing
            } else {
                outRect.left = column * spacing / spanCount
                outRect.right = spacing - (column + 1) * spacing / spanCount
                if (position >= spanCount) {
                    outRect.top = spacing
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isFirstLoad = false
        loadItemsWithCaching(forceRefresh = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}