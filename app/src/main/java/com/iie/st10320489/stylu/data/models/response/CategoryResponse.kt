package com.iie.st10320489.stylu.data.models.response

import com.iie.st10320489.stylu.data.models.category.Category

data class CategoryResponse(
    val success: Boolean,
    val data: List<Category>?
)