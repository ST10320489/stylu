package com.iie.st10320489.stylu.ui.item

import android.R.attr.button
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginRight
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.FragmentItemBinding
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem

class ItemFragment : Fragment() {

    private var _binding: FragmentItemBinding? = null
    private val binding get() = _binding!!

    private val categories = listOf(
        "All" to 12,
        "Tops" to 4,
        "Bottoms" to 3,
        "Shoes" to 2,
        "Accessories" to 3
    )

    private var selectedCategory: String = "All"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemBinding.inflate(inflater, container, false)

        setupCategoryButtons()
        setupRecyclerView()

        return binding.root
    }

    private fun setupCategoryButtons() {
        val categoryContainer = binding.categoryContainer
        categoryContainer.removeAllViews()

        for ((category, count) in categories) {
            val button = Button(requireContext()).apply {
                text = "$category ($count)"
                textSize = 16f
                setPadding(24, 0, 24, 0)
                isAllCaps = false
                setTextColor(Color.parseColor("#5A2E5A"))
                background = ContextCompat.getDrawable(
                    requireContext(),
                    if (category == selectedCategory) R.drawable.btn_primary_bg else R.drawable.btn_secondary_bg
                )
                setTextColor(if (category == selectedCategory) Color.WHITE else Color.parseColor("#5A2E5A"))
                setOnClickListener {
                    selectedCategory = category
                    setupCategoryButtons() // refresh buttons
                    filterItems(category)
                }
            }

            // Set LayoutParams with right margin
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                33.dpToPx() // height 33dp
            )
            layoutParams.setMargins(0, 0, 2.dpToPx(), 0) // right margin 2px
            button.layoutParams = layoutParams

            categoryContainer.addView(button)
        }
    }

    // Helper function to convert dp to px
    fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()


    private lateinit var itemAdapter: ItemAdapter

    private fun setupRecyclerView() {
        val spanCount = 2 // 2 columns
        val spacing = 16.dpToPx() // 16dp spacing
        val includeEdge = true

        binding.rvItems.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.rvItems.addItemDecoration(GridSpacingItemDecoration(spanCount, spacing, includeEdge))

        // 1️⃣ Initialize adapter
        itemAdapter = ItemAdapter()

        // 2️⃣ Assign to RecyclerView
        binding.rvItems.adapter = itemAdapter

        // 3️⃣ Submit initial list
        itemAdapter.submitList(getAllItems())
    }


    class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val spacing: Int,
        private val includeEdge: Boolean
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view) // item position
            val column = position % spanCount // item column

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount
                outRect.right = (column + 1) * spacing / spanCount

                if (position < spanCount) { // top edge
                    outRect.top = spacing
                }
                outRect.bottom = spacing // item bottom
            } else {
                outRect.left = column * spacing / spanCount
                outRect.right = spacing - (column + 1) * spacing / spanCount
                if (position >= spanCount) {
                    outRect.top = spacing
                }
            }
        }
    }



    private fun filterItems(category: String) {
        val filtered = if (category == "All") {
            getAllItems()
        } else {
            getAllItems().filter { it.category == category }
        }
        itemAdapter.submitList(filtered)
    }



    private fun getAllItems(): List<WardrobeItem> {
        // Mock data for now
        return listOf(
            WardrobeItem("White T-Shirt", "Tops", "white-shirt"),
            WardrobeItem("Blue Jeans", "Bottoms", "hoody"),
            WardrobeItem("Sneakers", "Shoes", "sneakers")
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
