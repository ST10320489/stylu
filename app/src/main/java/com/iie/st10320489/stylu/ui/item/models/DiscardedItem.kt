package com.iie.st10320489.stylu.ui.item.models

data class DiscardedItem(
    val item: WardrobeItem,
    val discardedAt: Long = System.currentTimeMillis()
)