package com.iie.st10320489.stylu.data.models.weather

import com.google.gson.annotations.SerializedName

data class OpenMeteoResponse(
    val daily: DailyData
)

data class DailyData(
    val time: List<String>,
    @SerializedName("temperature_2m_max")
    val temperatureMax: List<Double>,
    @SerializedName("temperature_2m_min")
    val temperatureMin: List<Double>,
    @SerializedName("weathercode")
    val weatherCode: List<Int>
)