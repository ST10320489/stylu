package com.iie.st10320489.stylu.data.models.response

// Image upload models
data class ImageUploadResponse(
    val success: Boolean,
    val imageUrl: String?,
    val error: String?
)