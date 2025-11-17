package com.iie.st10320489.stylu.data.models.item

data class ItemUploadRequest(
    val userId: String,
    val subcategoryId: Int,
    val name: String,
    val colour: String? = null,
    val material: String? = null,
    val size: String? = null,
    val price: Double? = null,
    val imageUrl: String,
    val weatherTag: String? = null,
    val createdBy: String? = "user"
)
