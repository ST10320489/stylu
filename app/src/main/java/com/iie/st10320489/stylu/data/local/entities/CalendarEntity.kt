package com.iie.st10320489.stylu.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_outfits")
data class CalendarEntity(
    @PrimaryKey
    val scheduleId: Int,
    val userId: String,
    val outfitId: Int,
    val scheduledDate: Long, // Store as timestamp for easier querying
    val eventName: String?,
    val notes: String?,

    // Weather info (optional)
    val weatherMinTemp: Int?,
    val weatherMaxTemp: Int?,
    val weatherCondition: String?,
    val weatherPrecipitation: Int?,

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// Extension functions to convert between domain models and entities
fun CalendarEntity.toScheduledOutfit(outfit: com.iie.st10320489.stylu.network.ApiService.OutfitDetail): com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit {
    return com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit(
        scheduleId = scheduleId,
        date = java.util.Date(scheduledDate),
        outfit = outfit,
        eventName = eventName,
        notes = notes,
        weatherForecast = if (weatherMinTemp != null && weatherMaxTemp != null && weatherCondition != null && weatherPrecipitation != null) {
            com.iie.st10320489.stylu.data.models.calendar.WeatherInfo(
                minTemp = weatherMinTemp,
                maxTemp = weatherMaxTemp,
                condition = weatherCondition,
                precipitation = weatherPrecipitation
            )
        } else null
    )
}

fun com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit.toEntity(userId: String): CalendarEntity {
    return CalendarEntity(
        scheduleId = scheduleId,
        userId = userId,
        outfitId = outfit.outfitId,
        scheduledDate = date.time,
        eventName = eventName,
        notes = notes,
        weatherMinTemp = weatherForecast?.minTemp,
        weatherMaxTemp = weatherForecast?.maxTemp,
        weatherCondition = weatherForecast?.condition,
        weatherPrecipitation = weatherForecast?.precipitation,
        updatedAt = System.currentTimeMillis()
    )
}