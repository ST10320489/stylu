package com.iie.st10320489.stylu.ui.item.models

// Update your WardrobeItem model to match database structure
data class WardrobeItem(
    val itemId: Int,
    val name: String,  // We'll derive this from subcategory or colour
    val category: String,
    val subcategory: String,
    val imageUrl: String,
    val colour: String?,
    val size: String?,
    val price: Double?,
    val weatherTag: String?,
    val timesWorn: Int = 0
)