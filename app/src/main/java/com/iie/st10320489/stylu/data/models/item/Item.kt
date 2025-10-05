package com.iie.st10320489.stylu.data.models.item

data class Item(
    val itemId: Int,
    val userId: String,
    val subcategoryId: Int,
    val colour: String?,
    val material: String?,
    val size: String?,
    val price: Double?,
    val imageUrl: String,
    val weatherTag: String?,
    val timesWorn: Int,
    val createdBy: String,
    val createdAt: String
)