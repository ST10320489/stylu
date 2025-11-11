package com.iie.st10320489.stylu.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outfits")
data class OutfitEntity(
    @PrimaryKey
    val outfitId: Int,
    val userId: String,
    val name: String,
    val category: String? = null,
    val scheduledDate: String? = null,
    val createdAt: String,
    val updatedAt: String
)

