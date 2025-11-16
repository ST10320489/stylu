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
import com.iie.st10320489.stylu.utils.SnapshotManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

data class EditItemLayout(
    val item: WardrobeItem,
    var x: Float,
    var y: Float,
    var scale: Float,
    val width: Int,
    val height: Int
)

/**
 * ✅ COMPLETE: Full functionality WITH debug logging
 */
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

    private var outfitId: Int = -1
    private var outfitName: String = ""
    private var canvasWidth: Int = 0
    private var canvasHeight: Int = 0

    // ✅ FIX: Store canvas dimensions when items are loaded/added
    private var capturedCanvasWidth: Int = 0
    private var capturedCanvasHeight: Int = 0

    companion object {
        private const val TAG = "EditOutfit_DEBUG"
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

        outfitId = arguments?.getInt("outfitId", -1) ?: -1
        outfitName = arguments?.getString("outfitName", "") ?: ""

        Log.d(TAG, "╔═══════════════════════════════════════════════════════")
        Log.d(TAG, "║ EDIT OUTFIT FRAGMENT STARTED")
        Log.d(TAG, "╠═══════════════════════════════════════════════════════")
        Log.d(TAG, "║ Outfit ID: $outfitId")
        Log.d(TAG, "║ Outfit Name: $outfitName")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════")

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

        // ✅ Wait for canvas to be measured BEFORE loading
        canvasContainer.viewTreeObserver.addOnGlobalLayoutListener(
            object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    canvasContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    canvasWidth = canvasContainer.width
                    canvasHeight = canvasContainer.height

                    Log.d(TAG, "")
                    Log.d(TAG, "📐 CANVAS MEASURED")
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d(TAG, "Canvas Size: ${canvasWidth}x${canvasHeight}")
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d(TAG, "")

                    loadExistingOutfit()
                }
            }
        )
    }

    private fun initializeViews(view: View) {
        canvasContainer = view.findViewById(R.id.canvasContainer)
        btnSaveOutfit = view.findViewById(R.id.btnSaveOutfit)
        btnCancel = view.findViewById(R.id.btnCancel)
        fabAddItems = view.findViewById(R.id.fabAddItem)
        progressBar = view.findViewById(R.id.progressBar)

        btnSaveOutfit.text = "Update Outfit"
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
                updateOutfit()
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

    private fun loadExistingOutfit() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                Log.d(TAG, "🌐 LOADING OUTFIT FROM API...")

                val existingSnapshot = File(requireContext().filesDir, "outfit_$outfitId.png")
                Log.d(TAG, "")
                Log.d(TAG, "📸 CHECKING EXISTING SNAPSHOT")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(TAG, "File path: ${existingSnapshot.absolutePath}")
                Log.d(TAG, "File exists: ${existingSnapshot.exists()}")
                if (existingSnapshot.exists()) {
                    Log.d(TAG, "File size: ${existingSnapshot.length()} bytes")
                    Log.d(TAG, "Last modified: ${java.util.Date(existingSnapshot.lastModified())}")
                }
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(TAG, "")

                val result = apiService.getUserOutfits()
                result.onSuccess { outfits ->
                    val outfit = outfits.find { it.outfitId == outfitId }

                    if (outfit == null) {
                        Log.e(TAG, "❌ Outfit $outfitId not found in API response")
                        Toast.makeText(requireContext(), "Outfit not found", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                        return@onSuccess
                    }

                    outfitName = outfit.name

                    Log.d(TAG, "")
                    Log.d(TAG, "✅ OUTFIT LOADED FROM API")
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d(TAG, "Outfit Name: ${outfit.name}")
                    Log.d(TAG, "Items Count: ${outfit.items.size}")
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d(TAG, "")

                    loadItemsToCanvas(outfit)

                }.onFailure { error ->
                    Log.e(TAG, "")
                    Log.e(TAG, "❌ FAILED TO LOAD OUTFIT FROM API")
                    Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.e(TAG, "Error: ${error.message}")
                    Log.e(TAG, "Stack trace: ${error.stackTraceToString()}")
                    Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.e(TAG, "")

                    Toast.makeText(
                        requireContext(),
                        "Failed to load outfit: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ EXCEPTION IN LOAD", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadItemsToCanvas(outfit: ApiService.OutfitDetail) {
        Log.d(TAG, "")
        Log.d(TAG, "📦 LOADING ITEMS TO CANVAS")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "Canvas: ${canvasWidth}x${canvasHeight}")
        Log.d(TAG, "Items to load: ${outfit.items.size}")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "")

        // ✅ FIX: Capture canvas size when loading existing items
        capturedCanvasWidth = canvasWidth
        capturedCanvasHeight = canvasHeight
        Log.d(TAG, "📐 CAPTURED CANVAS SIZE: ${capturedCanvasWidth}x${capturedCanvasHeight}")
        Log.d(TAG, "")

        outfit.items.forEachIndexed { index, apiItem ->
            val wardrobeItem = WardrobeItem(
                itemId = apiItem.itemId,
                name = apiItem.name ?: "Item",
                imageUrl = apiItem.imageUrl,
                colour = apiItem.colour,
                subcategory = apiItem.subcategory,
                category = "Unknown",
                size = null,
                weatherTag = null,
                timesWorn = 0
            )

            Log.d(TAG, "Item #${index + 1} (ID: ${apiItem.itemId}):")
            Log.d(TAG, "  Name: ${apiItem.name}")

            addItemToCanvas(wardrobeItem, apiItem.layoutData)
        }

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "✅ Finished loading ${outfit.items.size} items")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "")
    }

    private fun addItemToCanvas(item: WardrobeItem, existingLayout: ApiService.ItemLayoutData?) {
        if (itemLayouts.containsKey(item.itemId)) {
            Toast.makeText(requireContext(), "Item already added", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ FIX: Capture canvas size if adding new items
        if (capturedCanvasWidth == 0 || capturedCanvasHeight == 0) {
            capturedCanvasWidth = canvasWidth
            capturedCanvasHeight = canvasHeight
            Log.d(TAG, "📐 CAPTURED CANVAS SIZE (new item): ${capturedCanvasWidth}x${capturedCanvasHeight}")
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
                    Log.d(TAG, "🗑️ Removing item ${item.itemId}")
                    itemLayouts.remove(item.itemId)
                    canvasContainer.removeView(itemView)
                    Toast.makeText(requireContext(), "Item removed", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        if (existingLayout != null) {
            Log.d(TAG, "  Stored Layout Data:")
            Log.d(TAG, "    ├─ Relative X: ${existingLayout.x}")
            Log.d(TAG, "    ├─ Relative Y: ${existingLayout.y}")
            Log.d(TAG, "    ├─ Scale: ${existingLayout.scale}")
            Log.d(TAG, "    └─ Size: ${existingLayout.width}x${existingLayout.height}")

            val layoutParams = FrameLayout.LayoutParams(
                existingLayout.width,
                existingLayout.height
            )
            itemView.layoutParams = layoutParams

            canvasContainer.addView(itemView)

            itemView.post {
                try {
                    val absoluteX = existingLayout.x * canvasWidth
                    val absoluteY = existingLayout.y * canvasHeight

                    Log.d(TAG, "  Calculated Absolute Position:")
                    Log.d(TAG, "    ├─ X: ${existingLayout.x} * $canvasWidth = $absoluteX")
                    Log.d(TAG, "    └─ Y: ${existingLayout.y} * $canvasHeight = $absoluteY")

                    itemView.x = absoluteX
                    itemView.y = absoluteY
                    itemView.scaleX = existingLayout.scale
                    itemView.scaleY = existingLayout.scale

                    removeBtn.scaleX = 1f / existingLayout.scale
                    removeBtn.scaleY = 1f / existingLayout.scale

                    Log.d(TAG, "  Applied to View:")
                    Log.d(TAG, "    ├─ Position: ($absoluteX, $absoluteY)")
                    Log.d(TAG, "    ├─ Scale: ${existingLayout.scale}")
                    Log.d(TAG, "    └─ ✅ POSITIONED SUCCESSFULLY")
                    Log.d(TAG, "")

                    itemLayouts[item.itemId] = EditItemLayout(
                        item = item,
                        x = absoluteX,
                        y = absoluteY,
                        scale = existingLayout.scale,
                        width = existingLayout.width,
                        height = existingLayout.height
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "  ❌ ERROR POSITIONING ITEM", e)
                    Log.d(TAG, "")
                }
            }
        } else {
            canvasContainer.addView(itemView)

            itemView.post {
                itemLayouts[item.itemId] = EditItemLayout(
                    item = item,
                    x = itemView.x,
                    y = itemView.y,
                    scale = 1f,
                    width = itemView.width,
                    height = itemView.height
                )
            }
        }

        makeDraggableAndScalable(itemView, item.itemId, removeBtn)
    }

    private fun makeDraggableAndScalable(view: View, itemId: Int, removeBtn: ImageButton) {
        var dX = 0f
        var dY = 0f
        var isScaling = false

        val scaleGestureDetector = ScaleGestureDetector(requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isScaling = true
                    view.elevation = 8f * resources.displayMetrics.density
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val layout = itemLayouts[itemId] ?: return false

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

                        itemLayouts[itemId]?.let {
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
        Log.d(TAG, "")
        Log.d(TAG, "╔═══════════════════════════════════════════════════════")
        Log.d(TAG, "║ UPDATING OUTFIT")
        Log.d(TAG, "╠═══════════════════════════════════════════════════════")
        Log.d(TAG, "║ Outfit ID: $outfitId")
        Log.d(TAG, "║ Outfit Name: $outfitName")
        Log.d(TAG, "║ Items Count: ${itemLayouts.size}")
        Log.d(TAG, "║ Canvas: ${canvasWidth}x${canvasHeight}")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════")
        Log.d(TAG, "")

        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                btnSaveOutfit.isEnabled = false

                Log.d(TAG, "📊 UPDATED ITEM LAYOUT DATA:")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // ✅ FIX: Use CAPTURED canvas dimensions
                val canvasWidthForSaving = if (capturedCanvasWidth > 0) capturedCanvasWidth else canvasWidth
                val canvasHeightForSaving = if (capturedCanvasHeight > 0) capturedCanvasHeight else canvasHeight

                Log.d(TAG, "Using canvas size for calculations: ${canvasWidthForSaving}x${canvasHeightForSaving}")
                Log.d(TAG, "Current canvas size: ${canvasContainer.width}x${canvasContainer.height}")
                Log.d(TAG, "")

                val itemsJson = itemLayouts.values.mapIndexed { index, layout ->
                    val relativeX = layout.x / canvasWidthForSaving
                    val relativeY = layout.y / canvasHeightForSaving

                    Log.d(TAG, "Item #${index + 1} (ID: ${layout.item.itemId}):")
                    Log.d(TAG, "  ├─ Absolute: (${layout.x}, ${layout.y})")
                    Log.d(TAG, "  ├─ Relative: ($relativeX, $relativeY)")
                    Log.d(TAG, "  ├─ Scale: ${layout.scale}")
                    Log.d(TAG, "  └─ Size: ${layout.width}x${layout.height}")

                    JSONObject().apply {
                        put("itemId", layout.item.itemId)
                        put("x", relativeX.toDouble())
                        put("y", relativeY.toDouble())
                        put("scale", layout.scale.toDouble())
                        put("width", layout.width)
                        put("height", layout.height)
                    }.toString()
                }

                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(TAG, "")

                Log.d(TAG, "🌐 SENDING UPDATE TO API...")
                val result = outfitRepository.updateOutfit(
                    outfitId = outfitId,
                    name = outfitName,
                    items = itemsJson
                )

                result.onSuccess {
                    Log.d(TAG, "")
                    Log.d(TAG, "✅ API UPDATE SUCCESS")
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d(TAG, "")

                    val oldSnapshot = File(requireContext().filesDir, "outfit_$outfitId.png")
                    if (oldSnapshot.exists()) {
                        val deleted = oldSnapshot.delete()
                        Log.d(TAG, "🗑️ OLD SNAPSHOT")
                        Log.d(TAG, "Path: ${oldSnapshot.absolutePath}")
                        Log.d(TAG, "Deleted: $deleted")
                        Log.d(TAG, "")
                    }

                    Log.d(TAG, "📸 CREATING NEW SNAPSHOT...")
                    Log.d(TAG, "Canvas: ${canvasContainer.width}x${canvasContainer.height}")

                    val bitmap = getCanvasBitmap()

                    Log.d(TAG, "Bitmap: ${bitmap.width}x${bitmap.height} (${bitmap.byteCount} bytes)")
                    Log.d(TAG, "")

                    Log.d(TAG, "💾 SAVING NEW SNAPSHOT...")
                    val saved = SnapshotManager.saveSnapshot(requireContext(), outfitId, bitmap)

                    if (saved) {
                        val newSnapshot = File(requireContext().filesDir, "outfit_$outfitId.png")

                        Log.d(TAG, "")
                        Log.d(TAG, "✅ NEW SNAPSHOT SAVED")
                        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        Log.d(TAG, "Path: ${newSnapshot.absolutePath}")
                        Log.d(TAG, "Size: ${newSnapshot.length()} bytes")
                        Log.d(TAG, "Modified: ${java.util.Date(newSnapshot.lastModified())}")
                        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        Log.d(TAG, "")
                    } else {
                        Log.e(TAG, "❌ FAILED TO SAVE NEW SNAPSHOT")
                        Log.d(TAG, "")
                    }

                    Toast.makeText(requireContext(), "Outfit updated! ✅", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()

                }.onFailure { error ->
                    Log.e(TAG, "")
                    Log.e(TAG, "❌ API UPDATE FAILED")
                    Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.e(TAG, "Error: ${error.message}")
                    Log.e(TAG, "Stack trace: ${error.stackTraceToString()}")
                    Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.e(TAG, "")

                    Toast.makeText(
                        requireContext(),
                        "Failed to update: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ EXCEPTION IN UPDATE", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
}