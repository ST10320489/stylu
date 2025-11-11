package com.iie.st10320489.stylu.ui.item

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.FragmentEditItemBinding
import com.iie.st10320489.stylu.repository.ItemRepository
import kotlinx.coroutines.launch

class EditItemFragment : Fragment() {

    private var _binding: FragmentEditItemBinding? = null
    private val binding get() = _binding!!

    private val args: EditItemFragmentArgs by navArgs()
    private lateinit var itemRepository: ItemRepository

    // Category and subcategory mappings
    private val categorySubcategoryMap = mapOf(
        "Tops" to listOf("T-Shirt", "Shirt", "Blouse", "Sweater", "Hoodie", "Tank Top"),
        "Bottoms" to listOf("Jeans", "Trousers", "Shorts", "Skirt", "Leggings"),
        "Dresses" to listOf("Casual Dress", "Formal Dress", "Maxi Dress", "Mini Dress"),
        "Outerwear" to listOf("Jacket", "Coat", "Blazer", "Cardigan", "Vest"),
        "Footwear" to listOf("Sneakers", "Boots", "Sandals", "Heels", "Flats"),
        "Accessories" to listOf("Hat", "Scarf", "Belt", "Bag", "Jewelry", "Sunglasses")
    )

    private val weatherOptions = listOf("Hot", "Warm", "Cool", "Cold", "Rainy", "All Weather")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        itemRepository = ItemRepository(requireContext())

        setupSpinners()
        loadItemData()
        setupButtons()
    }

    private fun setupSpinners() {
        // Setup Category Spinner
        val categories = categorySubcategoryMap.keys.toList()
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categories
        )
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spCategory.adapter = categoryAdapter

        // Setup Weather Spinner
        val weatherAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            weatherOptions
        )
        weatherAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spWeatherTag.adapter = weatherAdapter

        // Listen for category changes to update subcategories
        binding.spCategory.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCategory = categories[position]
                updateSubcategorySpinner(selectedCategory)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun updateSubcategorySpinner(category: String) {
        val subcategories = categorySubcategoryMap[category] ?: emptyList()
        val subcategoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            subcategories
        )
        subcategoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spSubcategory.adapter = subcategoryAdapter
    }

    private fun loadItemData() {
        val item = args.item

        // Load image if available
        if (!item.imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(item.imageUrl)
                .placeholder(R.drawable.default_img)
                .error(R.drawable.default_img)
                .into(binding.ivItemImage)
        }

        // Populate form fields
        binding.etItemName.setText(item.name ?: "")
        binding.etColor.setText(item.colour ?: "")
        binding.etSize.setText(item.size ?: "")

        // Set category spinner
        val categoryPosition = categorySubcategoryMap.keys.indexOf(item.category)
        if (categoryPosition >= 0) {
            binding.spCategory.setSelection(categoryPosition)
        }

        // Set subcategory spinner (needs to be set after category)
        binding.spCategory.post {
            val subcategories = categorySubcategoryMap[item.category] ?: emptyList()
            val subcategoryPosition = subcategories.indexOf(item.subcategory)
            if (subcategoryPosition >= 0) {
                binding.spSubcategory.setSelection(subcategoryPosition)
            }
        }

        // Set weather tag spinner
        val weatherPosition = weatherOptions.indexOf(item.weatherTag)
        if (weatherPosition >= 0) {
            binding.spWeatherTag.setSelection(weatherPosition)
        }

        // Disable image selection for editing
    }

    private fun setupButtons() {
        binding.btnUpdateItem.setOnClickListener {
            updateItem()
        }

        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun updateItem() {
        // Validate required fields
        val name = binding.etItemName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etItemName.error = "This field is required"
            return
        }


        val category = binding.spCategory.selectedItem.toString()
        val subcategory = binding.spSubcategory.selectedItem.toString()
        val color = binding.etColor.text.toString().trim()
        val size = binding.etSize.text.toString().trim()
        val priceText = binding.etPrice.text.toString().trim()
        val weatherTag = binding.spWeatherTag.selectedItem.toString()

        // Build updates map
        val updates = mutableMapOf<String, Any>()
        updates["name"] = name
        updates["category"] = category
        updates["subcategory"] = subcategory

        if (color.isNotEmpty()) updates["colour"] = color
        if (size.isNotEmpty()) updates["size"] = size
        if (weatherTag.isNotEmpty()) updates["weatherTag"] = weatherTag

        // Handle price (optional field)
        if (priceText.isNotEmpty()) {
            val price = priceText.toDoubleOrNull()
            if (price != null) {
                updates["price"] = price
            } else {
                return
            }
        }

        // Show loading and update item
        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = itemRepository.updateItem(args.item.itemId, updates)

                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        "Item updated successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigateUp()
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.failed_to_update, error.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_message, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnUpdateItem.isEnabled = !show
        binding.btnCancel.isEnabled = !show
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}