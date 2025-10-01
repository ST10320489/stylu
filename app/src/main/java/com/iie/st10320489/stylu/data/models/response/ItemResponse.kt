package com.iie.st10320489.stylu.data.models.response

import com.iie.st10320489.stylu.data.models.item.Item

data class ItemResponse(
    val success: Boolean,
    val message: String?,
    val data: Item?
)