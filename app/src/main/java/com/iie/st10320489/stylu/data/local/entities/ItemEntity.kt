package com.iie.st10320489.stylu.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val itemId: Int,
    val name: String?,
    val subcategory: String,
    val category: String,
    val colour: String?,
    val size: String?,
    val imageUrl: String,
    val weatherTag: String?,
    val timesWorn: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

fun ItemEntity.toWardrobeItem() = WardrobeItem(
    itemId = itemId,
    name = name,
    subcategory = subcategory,
    category = category,
    colour = colour,
    size = size,
    imageUrl = imageUrl,
    weatherTag = weatherTag,
    timesWorn = timesWorn
)

fun WardrobeItem.toEntity() = ItemEntity(
    itemId = itemId,
    name = name,
    subcategory = subcategory,
    category = category,
    colour = colour,
    size = size,
    imageUrl = imageUrl,
    weatherTag = weatherTag,
    timesWorn = timesWorn
)