package com.iie.st10320489.stylu.data.models.outfit

data class Outfit(
    val outfitId: Int,  // ✅ Changed from String to Int
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
    val subcategory: String
)

data class CreateOutfitRequest(
    val userId: String,
    val name: String,
    val category: String?,
    val itemIds: List<Int>
)

data class OutfitResponse(
    val outfitId: Int,
    val name: String,
    val category: String?,
    val createdAt: String
)