package com.iie.st10320489.stylu.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "outfit_items",
    primaryKeys = ["outfitId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["outfitId"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["itemId"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("outfitId"), Index("itemId")]
)
data class OutfitItemEntity(
    val outfitId: Int,
    val itemId: Int,
    val x: Float,
    val y: Float,
    val scale: Float,
    val width: Int,
    val height: Int,
    val rotation: Float = 0f,
    val zIndex: Int = 0
)

