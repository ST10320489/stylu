package com.iie.st10320489.stylu.ui.home.models

data class DailyWeather(
    val day: String,
    val minTemp: Int,
    val maxTemp: Int,
    val condition: String, // "sun", "cloudy", "rain", "thunder"
    val background: String // "sun", "cloudy", "rain"
)
