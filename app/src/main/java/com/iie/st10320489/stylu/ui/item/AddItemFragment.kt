package com.iie.st10320489.stylu.ui.item

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.data.models.*
import com.iie.st10320489.stylu.data.models.category.Category
import com.iie.st10320489.stylu.data.models.item.ItemUploadRequest
import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import com.iie.st10320489.stylu.network.ItemApiService
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class AddItemFragment : Fragment() {

    private lateinit var ivItemImage: ImageView
    private lateinit var btnSelectImage: Button
    private lateinit var etItemName: EditText
    private lateinit var spCategory: Spinner
    private lateinit var spSubcategory: Spinner
    private lateinit var etColor: EditText
    private lateinit var etSize: EditText
    private lateinit var etPrice: EditText
    private lateinit var spWeatherTag: Spinner
    private lateinit var btnRemoveBackground: Button
    private lateinit var btnSaveItem: Button
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar

    private val itemApiService = ItemApiService()
    private var selectedImageUri: Uri? = null
    private var processedImageUri: Uri? = null
    private var currentPhotoPath: String? = null
    private var categories: List<Category> = emptyList()

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            startImageCrop(it)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoPath != null) {
            selectedImageUri = Uri.fromFile(File(currentPhotoPath!!))
            startImageCrop(selectedImageUri!!)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_item, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupClickListeners()
        loadCategories()
        setupWeatherTags()
    }

    private fun initializeViews(view: View) {
        ivItemImage = view.findViewById(R.id.ivItemImage)
        btnSelectImage = view.findViewById(R.id.btnSelectImage)
        etItemName = view.findViewById(R.id.etItemName)
        spCategory = view.findViewById(R.id.spCategory)
        spSubcategory = view.findViewById(R.id.spSubcategory)
        etColor = view.findViewById(R.id.etColor)
        etSize = view.findViewById(R.id.etSize)
        etPrice = view.findViewById(R.id.etPrice)
        spWeatherTag = view.findViewById(R.id.spWeatherTag)
        btnRemoveBackground = view.findViewById(R.id.btnRemoveBackground)
        btnSaveItem = view.findViewById(R.id.btnSaveItem)
        btnCancel = view.findViewById(R.id.btnCancel)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        btnSelectImage.setOnClickListener {
            showImageSourceDialog()
        }

        btnRemoveBackground.setOnClickListener {
            processedImageUri?.let { uri ->
                removeBackground(uri)
            } ?: run {
                Toast.makeText(requireContext(), "Please select an image first", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveItem.setOnClickListener {
            if (validateInputs()) {
                uploadItem()
            }
        }

        btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }

        spCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadSubcategories(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Image Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        try {
            val photoFile = createImageFile()
            val photoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            currentPhotoPath = photoFile.absolutePath
            cameraLauncher.launch(photoUri)
        } catch (e: IOException) {
            Toast.makeText(requireContext(), "Error creating image file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun createImageFile(): File {
        val timeStamp = System.currentTimeMillis()
        val storageDir = requireContext().getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun startImageCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))

        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1080, 1080)
            .start(requireContext(), this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = UCrop.getOutput(data!!)
            resultUri?.let {
                processedImageUri = it
                ivItemImage.setImageURI(it)
                btnRemoveBackground.isEnabled = true
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            Toast.makeText(requireContext(), "Crop error: ${cropError?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeBackground(imageUri: Uri) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                btnRemoveBackground.isEnabled = false

                val result = itemApiService.removeBackground(imageUri, requireContext())
                result.onSuccess { removedBgUri ->
                    processedImageUri = removedBgUri
                    ivItemImage.setImageURI(removedBgUri)
                    Toast.makeText(requireContext(), "Background removed successfully", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(requireContext(), "Failed to remove background: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                btnRemoveBackground.isEnabled = true
            }
        }
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            try {
                val result = itemApiService.getCategories()
                result.onSuccess { loadedCategories ->
                    categories = loadedCategories
                    val categoryNames = loadedCategories.map { it.name }
                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        categoryNames
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spCategory.adapter = adapter
                }.onFailure { error ->
                    Toast.makeText(requireContext(), "Failed to load categories", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading categories", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSubcategories(categoryPosition: Int) {
        if (categoryPosition < categories.size) {
            val subcategories = categories[categoryPosition].subcategories
            val subcategoryNames = subcategories.map { it.name }
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                subcategoryNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spSubcategory.adapter = adapter
        }
    }

    private fun setupWeatherTags() {
        val weatherTags = arrayOf("All Weather", "Hot", "Cold", "Rainy", "Sunny", "Windy")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, weatherTags)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spWeatherTag.adapter = adapter
    }

    private fun validateInputs(): Boolean {
        if (processedImageUri == null) {
            Toast.makeText(requireContext(), "Please select an image", Toast.LENGTH_SHORT).show()
            return false
        }

        if (etItemName.text.toString().trim().isEmpty()) {
            etItemName.error = "Item name is required"
            return false
        }

        if (spCategory.selectedItem == null) {
            Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show()
            return false
        }

        if (spSubcategory.selectedItem == null) {
            Toast.makeText(requireContext(), "Please select a subcategory", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun uploadItem() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                btnSaveItem.isEnabled = false

                val accessToken = DirectSupabaseAuth.getCurrentAccessToken()
                if (accessToken == null) {
                    Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val userId = DirectSupabaseAuth.getCurrentUser()?.id
                if (userId == null) {
                    Toast.makeText(requireContext(), "User ID not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Upload image to Supabase Storage
                val imageUrl = itemApiService.uploadImage(processedImageUri!!, requireContext(), accessToken)

                imageUrl.onSuccess { url ->
                    // Create item record in database
                    val itemRequest = ItemUploadRequest(
                        userId = userId,
                        subcategoryId = getSelectedSubcategoryId(),
                        name = etItemName.text.toString().trim(),   // <-- set name
                        colour = etColor.text.toString().trim().takeIf { it.isNotEmpty() },
                        material = null,
                        size = etSize.text.toString().trim().takeIf { it.isNotEmpty() },
                        price = etPrice.text.toString().toDoubleOrNull(),
                        imageUrl = url,
                        weatherTag = spWeatherTag.selectedItem.toString(),
                        createdBy = "user"
                    )


                    val result = itemApiService.createItem(accessToken, itemRequest)
                    result.onSuccess { response ->
                        Toast.makeText(requireContext(), "Item added successfully!", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }.onFailure { error ->
                        Toast.makeText(requireContext(), "Failed to create item: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { error ->
                    Toast.makeText(requireContext(), "Failed to upload image: ${error.message}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                btnSaveItem.isEnabled = true
            }
        }
    }

    private fun getSelectedSubcategoryId(): Int {
        val categoryPosition = spCategory.selectedItemPosition
        val subcategoryPosition = spSubcategory.selectedItemPosition

        if (categoryPosition >= 0 && categoryPosition < categories.size) {
            val category = categories[categoryPosition]
            if (subcategoryPosition >= 0 && subcategoryPosition < category.subcategories.size) {
                return category.subcategories[subcategoryPosition].subcategoryId
            }
        }

        return 0
    }
}