package com.iie.st10320489.stylu.data.models.calendar

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class ScheduledOutfit(
    val scheduleId: Int,
    val date: Date,
    val outfit: OutfitInfo,
    val weatherForecast: WeatherInfo? = null
) : Parcelable

@Parcelize
data class OutfitInfo(
    val outfitId: Int,
    val name: String,
    val category: String,
    val items: List<OutfitItemInfo>
) : Parcelable

@Parcelize
data class OutfitItemInfo(
    val itemId: Int,
    val name: String,
    val imageUrl: String,
    val category: String,
    val subcategory: String
) : Parcelable

@Parcelize
data class WeatherInfo(
    val minTemp: Int,
    val maxTemp: Int,
    val condition: String,
    val precipitation: Int
) : Parcelable