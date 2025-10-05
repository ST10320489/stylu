package com.iie.st10320489.stylu.ui.item

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.FragmentItemBinding
import com.iie.st10320489.stylu.repository.ItemRepository
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem
import kotlinx.coroutines.launch

class ItemFragment : Fragment() {

    private var _binding: FragmentItemBinding? = null
    private val binding get() = _binding!!

    private lateinit var itemRepository: ItemRepository
    private lateinit var itemAdapter: ItemAdapter

    private var allItems: List<WardrobeItem> = emptyList()
    private var selectedCategory: String = "All"
    private var categoryCounts: Map<String, Int> = emptyMap()

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

        // Initialize repository with context
        itemRepository = ItemRepository(requireContext())

        setupRecyclerView()
        loadItemsFromAPI()
    }

    private fun setupRecyclerView() {
        val spanCount = 2 // 2 columns
        val spacing = 16.dpToPx() // 16dp spacing
        val includeEdge = true

        binding.rvItems.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.rvItems.addItemDecoration(GridSpacingItemDecoration(spanCount, spacing, includeEdge))

        // Initialize adapter with click listener
        itemAdapter = ItemAdapter { item ->
            // Handle item click - navigate to detail view or show options
            showItemOptions(item)
        }

        binding.rvItems.adapter = itemAdapter
    }

    private fun loadItemsFromAPI() {
        lifecycleScope.launch {
            try {
                showLoading(true)

                // Fetch all items from API
                val itemsResult = itemRepository.getUserItems()
                itemsResult.onSuccess { items ->
                    allItems = items

                    // Fetch category counts from API
                    val countsResult = itemRepository.getItemCountsByCategory()
                    countsResult.onSuccess { counts ->
                        categoryCounts = counts
                        setupCategoryButtons()
                        filterItems(selectedCategory)
                    }.onFailure {
                        // If counts fail, still show items with manual count
                        categoryCounts = calculateCategoryCounts(items)
                        setupCategoryButtons()
                        filterItems(selectedCategory)
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Failed to load items: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Show empty state
                    itemAdapter.submitList(emptyList())
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun calculateCategoryCounts(items: List<WardrobeItem>): Map<String, Int> {
        return items.groupBy { it.category }
            .mapValues { it.value.size }
    }

    private fun setupCategoryButtons() {
        val categoryContainer = binding.categoryContainer
        categoryContainer.removeAllViews()

        // Get unique categories from items
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
                    filterItems(category)
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

    private fun filterItems(category: String) {
        val filtered = if (category == "All") {
            allItems
        } else {
            allItems.filter { it.category == category }
        }
        itemAdapter.submitList(filtered)
    }

    private fun showItemOptions(item: WardrobeItem) {
        // Show dialog with options: View Details, Edit, Delete
        val options = arrayOf("View Details", "Edit", "Delete")

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
        // Create a simple details dialog
        val message = buildString {
            append("Name: ${item.name ?: "N/A"}\n")
            append("Category: ${item.category}\n")
            append("Subcategory: ${item.subcategory}\n")
            append("Color: ${item.colour ?: "N/A"}\n")
            append("Size: ${item.size ?: "N/A"}\n")
            append("Weather Tag: ${item.weatherTag ?: "N/A"}\n")
            append("Times Worn: ${item.timesWorn}")
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(item.name ?: item.subcategory)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun editItem(item: WardrobeItem) {
        // Create an edit dialog with editable fields
        val editView = layoutInflater.inflate(R.layout.dialog_edit_item, null)

        val etName = editView.findViewById<android.widget.EditText>(R.id.etEditName)
        val etColor = editView.findViewById<android.widget.EditText>(R.id.etEditColor)
        val etSize = editView.findViewById<android.widget.EditText>(R.id.etEditSize)

        // Pre-fill with current values
        etName.setText(item.name)
        etColor.setText(item.colour)
        etSize.setText(item.size)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Edit Item")
            .setView(editView)
            .setPositiveButton("Save") { _, _ ->
                val updates = mutableMapOf<String, Any>()

                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) updates["name"] = name

                val color = etColor.text.toString().trim()
                if (color.isNotEmpty()) updates["colour"] = color

                val size = etSize.text.toString().trim()
                if (size.isNotEmpty()) updates["size"] = size

                if (updates.isNotEmpty()) {
                    updateItem(item.itemId, updates)
                } else {
                    Toast.makeText(requireContext(), "No changes made", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateItem(itemId: Int, updates: Map<String, Any>) {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val result = itemRepository.updateItem(itemId, updates)
                result.onSuccess { message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    loadItemsFromAPI() // Refresh list
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Failed to update: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun confirmDelete(item: WardrobeItem) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete ${item.name ?: item.subcategory}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteItem(item: WardrobeItem) {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val result = itemRepository.deleteItem(item.itemId)
                result.onSuccess {
                    Toast.makeText(requireContext(), "Item deleted", Toast.LENGTH_SHORT).show()
                    loadItemsFromAPI() // Refresh list
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Failed to delete: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    // Helper function to convert dp to px
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
        // Refresh items when returning to fragment (e.g., after adding new item)
        loadItemsFromAPI()  // Changed from loadItemsFromDatabase()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}