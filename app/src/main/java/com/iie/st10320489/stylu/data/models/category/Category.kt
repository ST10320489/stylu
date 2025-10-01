package com.iie.st10320489.stylu.data.models.category

import com.iie.st10320489.stylu.data.models.category.Subcategory

data class Category(
    val categoryId: Int,
    val name: String,
    val subcategories: List<Subcategory> = emptyList()
)