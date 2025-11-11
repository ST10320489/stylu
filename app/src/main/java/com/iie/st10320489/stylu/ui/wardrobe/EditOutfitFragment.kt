package com.iie.st10320489.stylu.ui.wardrobe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.repository.ItemRepository
import com.iie.st10320489.stylu.repository.OutfitRepository
import com.iie.st10320489.stylu.ui.item.ItemAdapter
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class EditItemLayout(
    val item: WardrobeItem,
    var x: Float,
    var y: Float,
    var scale: Float,
    val width: Int,
    val height: Int
)

class EditOutfitFragment : Fragment() {

    private lateinit var canvasContainer: FrameLayout
    private lateinit var btnSaveOutfit: Button
    private lateinit var btnCancel: Button
    private lateinit var fabAddItems: FloatingActionButton
    private lateinit var progressBar: ProgressBar

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var rvAvailableItems: RecyclerView
    private lateinit var categoryContainer: LinearLayout
    private lateinit var etSearch: EditText

    private val itemRepository by lazy { ItemRepository(requireContext()) }
    private val outfitRepository by lazy { OutfitRepository(requireContext()) }
    private val apiService by lazy { ApiService(requireContext()) }

    private lateinit var itemAdapter: ItemAdapter

    private var allItems: List<WardrobeItem> = emptyList()
    private var filteredItems: List<WardrobeItem> = emptyList()
    private var selectedCategory: String = "All"
    private val itemLayouts = mutableMapOf<Int, EditItemLayout>()

    // Edit mode data
    private var outfitId: Int = -1
    private var outfitName: String = ""
    private var originalOutfit: ApiService.OutfitDetail? = null

    companion object {
        private const val TAG = "EditOutfitFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_outfit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get outfit data from arguments
        outfitId = arguments?.getInt("outfitId", -1) ?: -1
        outfitName = arguments?.getString("outfitName", "") ?: ""

        if (outfitId == -1) {
            Toast.makeText(requireContext(), "Invalid outfit", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        initializeViews(view)
        setupBottomSheet(view)
        setupRecyclerView()
        setupClickListeners()
        setupSearch()
        setupBackPressHandler()

        loadItems()
        loadExistingOutfit()
    }

    private fun initializeViews(view: View) {
        canvasContainer = view.findViewById(R.id.canvasContainer)
        btnSaveOutfit = view.findViewById(R.id.btnSaveOutfit)
        btnCancel = view.findViewById(R.id.btnCancel)
        fabAddItems = view.findViewById(R.id.fabAddItem)
        progressBar = view.findViewById(R.id.progressBar)

        // Update button text for edit mode
        btnSaveOutfit.text = "Update Outfit"
    }

    private fun loadExistingOutfit() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                Log.d(TAG, "Loading outfit with ID: $outfitId")

                val result = apiService.getUserOutfits()
                result.onSuccess { outfits ->
                    val outfit = outfits.find { it.outfitId == outfitId }

                    if (outfit == null) {
                        Toast.makeText(requireContext(), "Outfit not found", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                        return@onSuccess
                    }

                    originalOutfit = outfit
                    outfitName = outfit.name

                    Log.d(TAG, "Loaded outfit: ${outfit.name} with ${outfit.items.size} items")

                    // Wait for canvas to be measured
                    canvasContainer.post {
                        outfit.items.forEach { apiItem ->
                            // Convert ApiService.OutfitItemDetail to WardrobeItem
                            val wardrobeItem = WardrobeItem(
                                itemId = apiItem.itemId,
                                name = apiItem.name ?: "Item ${apiItem.itemId}",
                                imageUrl = apiItem.imageUrl,
                                colour = apiItem.colour,
                                subcategory = apiItem.subcategory,
                                category = "Unknown", // We don't have category in OutfitItemDetail
                                size = null, // ✅ Added missing parameter
                                weatherTag = null, // ✅ Added missing parameter
                                timesWorn = 0 // ✅ Added missing parameter
                            )

                            addItemToCanvas(wardrobeItem, apiItem.layoutData)
                        }
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Failed to load outfit: ${error.message}")
                    Toast.makeText(
                        requireContext(),
                        "Failed to load outfit: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading outfit", e)
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
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
            addItemToCanvas(item, null)
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
                showUpdateConfirmationDialog()
            }
        }

        btnCancel.setOnClickListener {
            showExitConfirmationDialog()
        }
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            showExitConfirmationDialog()
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
            itemRepository.getUserItems().collect { result ->
                result.onSuccess { items ->
                    allItems = items
                    setupCategoryButtons()
                    filterItems(selectedCategory)
                }.onFailure { error ->
                    Toast.makeText(requireContext(), "Failed to load items: ${error.message}", Toast.LENGTH_SHORT).show()
                }
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

    private fun addItemToCanvas(item: WardrobeItem, existingLayout: ApiService.ItemLayoutData?) {
        if (itemLayouts.containsKey(item.itemId)) {
            Toast.makeText(requireContext(), "Item already added", Toast.LENGTH_SHORT).show()
            return
        }

        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.canvas_item, canvasContainer, false)

        val imageView = itemView.findViewById<ImageView>(R.id.ivCanvasItem)
        val removeBtn = itemView.findViewById<ImageButton>(R.id.btnRemoveItem)

        Glide.with(requireContext())
            .load(item.imageUrl)
            .fitCenter()
            .placeholder(R.drawable.cloudy)
            .error(R.drawable.sunny)
            .into(imageView)

        removeBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Remove Item")
                .setMessage("Remove this item from the outfit?")
                .setPositiveButton("Remove") { _, _ ->
                    itemLayouts.remove(item.itemId)
                    canvasContainer.removeView(itemView)
                    Toast.makeText(requireContext(), "Item removed", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Apply existing layout or use defaults
        itemView.post {
            if (existingLayout != null) {
                // Use existing layout data
                val layoutParams = FrameLayout.LayoutParams(
                    existingLayout.width,
                    existingLayout.height
                )
                itemView.layoutParams = layoutParams
                itemView.x = existingLayout.x * canvasContainer.width
                itemView.y = existingLayout.y * canvasContainer.height
                itemView.scaleX = existingLayout.scale
                itemView.scaleY = existingLayout.scale

                removeBtn.scaleX = 1f / existingLayout.scale
                removeBtn.scaleY = 1f / existingLayout.scale

                itemLayouts[item.itemId] = EditItemLayout(
                    item = item,
                    x = itemView.x,
                    y = itemView.y,
                    scale = existingLayout.scale,
                    width = existingLayout.width,
                    height = existingLayout.height
                )
            } else {
                // New item - center it
                val initialLayout = EditItemLayout(
                    item = item,
                    x = itemView.x,
                    y = itemView.y,
                    scale = 1f,
                    width = itemView.width,
                    height = itemView.height
                )
                itemLayouts[item.itemId] = initialLayout
            }
        }

        makeDraggableAndScalable(itemView, item, removeBtn)
        canvasContainer.addView(itemView)

        Log.d(TAG, "Added item ${item.itemId} to canvas")
    }

    private fun makeDraggableAndScalable(view: View, item: WardrobeItem, removeBtn: ImageButton) {
        var dX = 0f
        var dY = 0f
        var isScaling = false

        val scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                view.elevation = 8f * resources.displayMetrics.density
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val layout = itemLayouts[item.itemId] ?: return false

                var newScale = layout.scale * detector.scaleFactor
                newScale = newScale.coerceIn(0.3f, 5.0f)

                view.scaleX = newScale
                view.scaleY = newScale

                removeBtn.scaleX = 1f / newScale
                removeBtn.scaleY = 1f / newScale

                layout.scale = newScale
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                view.elevation = 4f * resources.displayMetrics.density
            }
        })

        var touchCount = 0

        view.setOnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    v.bringToFront()
                    touchCount = 1
                    true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    touchCount = event.pointerCount
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isScaling && !scaleGestureDetector.isInProgress && touchCount == 1) {
                        val newX = event.rawX + dX
                        val newY = event.rawY + dY

                        val maxX = canvasContainer.width.toFloat()
                        val maxY = canvasContainer.height.toFloat()

                        v.x = newX.coerceIn(-v.width * 0.2f, maxX - v.width * 0.8f)
                        v.y = newY.coerceIn(-v.height * 0.2f, maxY - v.height * 0.8f)

                        itemLayouts[item.itemId]?.let {
                            it.x = v.x
                            it.y = v.y
                        }
                    }
                    true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    touchCount = event.pointerCount - 1
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchCount = 0
                    isScaling = false
                    false
                }

                else -> false
            }
        }
    }

    private fun showUpdateConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Update Outfit")
            .setMessage("Save changes to \"$outfitName\"?")
            .setPositiveButton("Update") { _, _ ->
                updateOutfit()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Discard Changes?")
            .setMessage("Are you sure you want to exit without saving changes?")
            .setPositiveButton("Exit") { _, _ ->
                findNavController().navigateUp()
            }
            .setNegativeButton("Stay", null)
            .show()
    }

    private fun updateOutfit() {
        Log.d(TAG, "updateOutfit() called for outfit ID: $outfitId")

        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                btnSaveOutfit.isEnabled = false

                // Build layout JSON for each item
                val itemsJson = itemLayouts.values.map { layout ->
                    JSONObject().apply {
                        put("itemId", layout.item.itemId)
                        put("x", (layout.x / canvasContainer.width).toDouble())
                        put("y", (layout.y / canvasContainer.height).toDouble())
                        put("scale", layout.scale.toDouble())
                        put("width", layout.width)
                        put("height", layout.height)
                    }.toString()
                }

                Log.d(TAG, "Updating with ${itemsJson.size} items")

                val result = outfitRepository.updateOutfit(
                    outfitId = outfitId,
                    name = outfitName,
                    items = itemsJson
                )

                result.onSuccess {
                    // Save updated bitmap
                    val bitmap = getCanvasBitmap()
                    saveBitmapToFile(bitmap, "outfit_$outfitId")

                    Toast.makeText(requireContext(), "Outfit updated!", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }.onFailure { error ->
                    Log.e(TAG, "Failed to update outfit: ${error.message}")
                    Toast.makeText(
                        requireContext(),
                        "Failed to update: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating outfit", e)
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                progressBar.visibility = View.GONE
                btnSaveOutfit.isEnabled = true
            }
        }
    }

    private fun getCanvasBitmap(): Bitmap {
        fabAddItems.visibility = View.INVISIBLE

        val removeButtons = mutableListOf<View>()
        for (i in 0 until canvasContainer.childCount) {
            val child = canvasContainer.getChildAt(i)
            val removeBtn = child.findViewById<View>(R.id.btnRemoveItem)
            if (removeBtn != null) {
                removeButtons.add(removeBtn)
                removeBtn.visibility = View.INVISIBLE
            }
        }

        val bitmap = Bitmap.createBitmap(
            canvasContainer.width,
            canvasContainer.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvasContainer.draw(canvas)

        fabAddItems.visibility = View.VISIBLE
        removeButtons.forEach { it.visibility = View.VISIBLE }

        return bitmap
    }

    private fun saveBitmapToFile(bitmap: Bitmap, fileName: String) {
        val file = File(requireContext().filesDir, "$fileName.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Log.d(TAG, "Saved bitmap to: ${file.absolutePath}")
    }
}