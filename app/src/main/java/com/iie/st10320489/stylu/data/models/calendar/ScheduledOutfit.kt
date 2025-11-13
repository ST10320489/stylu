package com.iie.st10320489.stylu.data.models.calendar

import com.iie.st10320489.stylu.network.ApiService
import java.util.Date

// Simplified - reuses OutfitDetail from ApiService
data class ScheduledOutfit(
    val scheduleId: Int,
    val date: Date,
    val outfit: ApiService.OutfitDetail,
    val eventName: String? = null,
    val notes: String? = null,
    val weatherForecast: WeatherInfo? = null
)

data class WeatherInfo(
    val minTemp: Int,
    val maxTemp: Int,
    val condition: String,
    val precipitation: Int
)

data class CalendarEvent(
    val eventId: Int,
    val userId: String,
    val outfitId: Int,
    val eventDate: Date,
    val eventName: String?,
    val notes: String?
)

