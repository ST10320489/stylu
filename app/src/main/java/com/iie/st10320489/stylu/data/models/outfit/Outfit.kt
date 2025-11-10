package com.iie.st10320489.stylu.data.models.outfit

data class Outfit(
    val outfitId: Int,
    val userId: String,
    val name: String,
    val category: String?,
    val schedule: String?, // ISO date string if scheduled
    val items: List<OutfitItemDetail>,
    val createdAt: String
)

data class OutfitItemDetail(
    val itemId: Int,
    val name: String?,
    val imageUrl: String,
    val colour: String?,
    val subcategory: String,
    val layoutData: ItemLayoutData? = null
)

data class ItemLayoutData(
    val x: Float,
    val y: Float,
    val scale: Float,
    val width: Int,
    val height: Int
)

data class CreateOutfitRequest(
    val userId: String,
    val name: String,
    val category: String?,
    val itemIds: List<Int>,
    val schedule: String? = null
)

data class OutfitResponse(
    val outfitId: Int,
    val name: String,
    val category: String?,
    val schedule: String?,
    val createdAt: String
)

data class ScheduleOutfitRequest(
    val outfitId: Int,
    val schedule: String // ISO date format: yyyy-MM-dd
)