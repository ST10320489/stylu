package com.iie.st10320489.stylu.data.models.calendar

import com.iie.st10320489.stylu.network.ApiService
import java.util.Date

data class ScheduledOutfit(
    val scheduleId: Int,
    val date: Date,
    val outfit: ApiService.OutfitDetail,
    val weatherForecast: WeatherInfo? = null
)

data class WeatherInfo(
    val minTemp: Int,
    val maxTemp: Int,
    val condition: String,
    val precipitation: Int
)