package com.iie.st10320489.stylu.ui.wardrobe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.repository.ItemRepository
import com.iie.st10320489.stylu.ui.item.ItemAdapter
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class ItemLayout(
    val item: WardrobeItem,
    val x: Float,
    val y: Float,
    val scale: Float,
    val width: Int,
    val height: Int
)

class CreateOutfitFragment : Fragment() {

    private lateinit var canvasContainer: FrameLayout
    private lateinit var btnSaveOutfit: Button
    private lateinit var btnCancel: Button
    private lateinit var fabAddItems: FloatingActionButton
    private lateinit var progressBar: ProgressBar

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var rvAvailableItems: RecyclerView
    private lateinit var categoryContainer: LinearLayout
    private lateinit var etSearch: EditText

    private val itemRepository = ItemRepository()
    private lateinit var apiService: ApiService
    private lateinit var itemAdapter: ItemAdapter

    private var allItems: List<WardrobeItem> = emptyList()
    private var filteredItems: List<WardrobeItem> = emptyList()
    private var selectedCategory: String = "All"
    private val itemLayouts = mutableMapOf<Int, ItemLayout>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_create_outfit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiService = ApiService(requireContext())

        initializeViews(view)
        setupBottomSheet(view)
        setupRecyclerView()
        setupClickListeners()
        setupSearch()
        loadItems()
    }

    private fun initializeViews(view: View) {
        canvasContainer = view.findViewById(R.id.canvasContainer)
        btnSaveOutfit = view.findViewById(R.id.btnSaveOutfit)
        btnCancel = view.findViewById(R.id.btnCancel)
        fabAddItems = view.findViewById(R.id.fabAddItem)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun setupBottomSheet(view: View) {
        val bottomSheet = layoutInflater.inflate(R.layout.bottom_sheet_items, null)

        val rootLayout = view as CoordinatorLayout

        val layoutParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            behavior = BottomSheetBehavior<View>().apply {
                peekHeight = 0
                state = BottomSheetBehavior.STATE_HIDDEN
                isHideable = true
            }
        }

        bottomSheet.layoutParams = layoutParams
        rootLayout.addView(bottomSheet)

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        rvAvailableItems = bottomSheet.findViewById(R.id.rvAvailableItems)
        categoryContainer = bottomSheet.findViewById(R.id.categoryFilterContainer)
        etSearch = bottomSheet.findViewById(R.id.etSearch)
    }

    private fun setupRecyclerView() {
        rvAvailableItems.layoutManager = GridLayoutManager(requireContext(), 3)

        itemAdapter = ItemAdapter { item ->
            addItemToCanvas(item)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        rvAvailableItems.adapter = itemAdapter
    }

    private fun setupClickListeners() {
        fabAddItems.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        btnSaveOutfit.setOnClickListener {
            if (itemLayouts.isEmpty()) {
                Toast.makeText(requireContext(), "Please add at least one item", Toast.LENGTH_SHORT).show()
            } else {
                showSaveOutfitDialog()
            }
        }

        btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterItemsBySearch(s.toString())
            }
        })
    }

    private fun filterItemsBySearch(query: String) {
        val searchFiltered = if (query.isEmpty()) {
            filteredItems
        } else {
            filteredItems.filter { item ->
                item.name?.contains(query, ignoreCase = true) == true ||
                        item.subcategory.contains(query, ignoreCase = true) ||
                        item.colour?.contains(query, ignoreCase = true) == true
            }
        }
        itemAdapter.submitList(searchFiltered)
    }

    private fun loadItems() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                val result = itemRepository.getUserItems()
                result.onSuccess { items ->
                    allItems = items
                    setupCategoryButtons()
                    filterItems(selectedCategory)
                }.onFailure { error ->
                    Toast.makeText(requireContext(), "Failed to load items: ${error.message}", Toast.LENGTH_SHORT).show()
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

        val categoryCounts = allItems.groupingBy { it.category }.eachCount()
        val categories = listOf("All (${allItems.size})") + categoryCounts.map { "${it.key} (${it.value})" }

        for (categoryText in categories) {
            val category = categoryText.substringBefore(" (")

            val button = Button(requireContext()).apply {
                text = categoryText
                textSize = 13f
                setPadding(16, 0, 16, 0)
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
                    filterItems(category)
                }
            }

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (32 * resources.displayMetrics.density).toInt()
            )
            layoutParams.setMargins(0, 0, (8 * resources.displayMetrics.density).toInt(), 0)
            button.layoutParams = layoutParams

            categoryContainer.addView(button)
        }
    }

    private fun filterItems(category: String) {
        filteredItems = if (category == "All") allItems else allItems.filter { it.category == category }
        itemAdapter.submitList(filteredItems)
    }

    private fun addItemToCanvas(item: WardrobeItem) {
        if (itemLayouts.containsKey(item.itemId)) {
            Toast.makeText(requireContext(), "Item already added", Toast.LENGTH_SHORT).show()
            return
        }

        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.canvas_item, canvasContainer, false)

        val imageView = itemView.findViewById<ImageView>(R.id.ivCanvasItem)
        val removeBtn = itemView.findViewById<ImageButton>(R.id.btnRemoveItem)

        com.bumptech.glide.Glide.with(requireContext())
            .load(item.imageUrl)
            .fitCenter() // ensures full image is displayed
            .placeholder(R.drawable.cloudy)
            .error(R.drawable.sunny)
            .into(imageView)


        removeBtn.setOnClickListener {
            itemLayouts.remove(item.itemId)
            canvasContainer.removeView(itemView)
        }

        itemView.post {
            val initialLayout = ItemLayout(
                item = item,
                x = itemView.x,
                y = itemView.y,
                scale = 1f,
                width = itemView.width,
                height = itemView.height
            )
            itemLayouts[item.itemId] = initialLayout
        }

        makeDraggableAndScalable(itemView, item)
        canvasContainer.addView(itemView)
    }

    private fun makeDraggableAndScalable(view: View, item: WardrobeItem) {
        var dX = 0f
        var dY = 0f
        var scaleFactor = 1f

        val scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)

                view.scaleX = scaleFactor
                view.scaleY = scaleFactor

                itemLayouts[item.itemId] = itemLayouts[item.itemId]?.copy(scale = scaleFactor)
                    ?: ItemLayout(item, view.x, view.y, scaleFactor, view.width, view.height)

                return true
            }
        })

        view.setOnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    v.bringToFront()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleGestureDetector.isInProgress) {
                        val newX = event.rawX + dX
                        val newY = event.rawY + dY

                        val maxX = canvasContainer.width - v.width.toFloat()
                        val maxY = canvasContainer.height - v.height.toFloat()

                        v.x = newX.coerceIn(0f, maxX)
                        v.y = newY.coerceIn(0f, maxY)

                        itemLayouts[item.itemId] = itemLayouts[item.itemId]?.copy(x = v.x, y = v.y)
                            ?: ItemLayout(item, v.x, v.y, scaleFactor, v.width, v.height)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showSaveOutfitDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_outfit, null)

        val etOutfitName = dialogView.findViewById<EditText>(R.id.etOutfitName)
        val spCategory = dialogView.findViewById<Spinner>(R.id.spOutfitCategory)

        val categories = arrayOf("Casual", "Formal", "Sport", "Party", "Work", "Other")
        spCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Save Outfit")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etOutfitName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter outfit name", Toast.LENGTH_SHORT).show()
                } else {
                    val category = spCategory.selectedItem.toString()
                    saveOutfit(name, category)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveOutfit(name: String, category: String) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                btnSaveOutfit.isEnabled = false

                val itemsWithLayout = itemLayouts.values.map { layout ->
                    JSONObject().apply {
                        put("itemId", layout.item.itemId)
                        put("x", layout.x / canvasContainer.width)
                        put("y", layout.y / canvasContainer.height)
                        put("scale", layout.scale)
                        put("width", layout.width)
                        put("height", layout.height)
                    }.toString()
                }

                val request = ApiService.CreateOutfitWithLayoutRequest(
                    name = name,
                    category = category,
                    items = itemsWithLayout
                )

                val bitmap = getCanvasBitmap()
                val imagePath = saveBitmapToFile(bitmap, "outfit_${System.currentTimeMillis()}")


                val result = apiService.createOutfitWithLayout(request)
                result.onSuccess { savedOutfit ->
                    // savedOutfit.outfitId comes from API response
                    val bitmap = getCanvasBitmap()
                    saveBitmapToFile(bitmap, "outfit_${savedOutfit.outfitId}")

                    Toast.makeText(requireContext(), "Outfit saved!", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }.onFailure { error ->
                    Toast.makeText(requireContext(), "Failed to save: ${error.message}", Toast.LENGTH_SHORT).show()
                    btnSaveOutfit.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSaveOutfit.isEnabled = true
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun getCanvasBitmap(): Bitmap {
        // Hide FAB
        fabAddItems.visibility = View.INVISIBLE

        // Hide all remove buttons
        val removeButtons = mutableListOf<View>()
        for (i in 0 until canvasContainer.childCount) {
            val child = canvasContainer.getChildAt(i)
            val removeBtn = child.findViewById<View>(R.id.btnRemoveItem)
            if (removeBtn != null) {
                removeButtons.add(removeBtn)
                removeBtn.visibility = View.INVISIBLE
            }
        }

        // Draw bitmap
        val bitmap = Bitmap.createBitmap(canvasContainer.width, canvasContainer.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvasContainer.draw(canvas)

        // Restore visibility
        fabAddItems.visibility = View.VISIBLE
        removeButtons.forEach { it.visibility = View.VISIBLE }

        return bitmap
    }



    private fun saveBitmapToFile(bitmap: Bitmap, fileName: String) {
        val file = File(requireContext().filesDir, "$fileName.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }


}