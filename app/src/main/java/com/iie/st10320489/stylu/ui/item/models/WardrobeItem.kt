package com.iie.st10320489.stylu.ui.item.models

data class WardrobeItem(
    val itemId: Int,
    val name: String?,
    val subcategory: String,
    val category: String,
    val colour: String?,
    val size: String?,
    val imageUrl: String,
    val weatherTag: String?,
    val timesWorn: Int
)