package com.iie.st10320489.stylu.repository

import android.util.Log
import com.iie.st10320489.stylu.network.WeatherApiService
import com.iie.st10320489.stylu.ui.home.models.DailyWeather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WeatherRepository {
    private val weatherApi = WeatherApiService.create()

    companion object {
        private const val TAG = "WeatherRepository"
        private const val PRETORIA_LAT = -25.7479
        private const val PRETORIA_LON = 28.2293
    }

    suspend fun getWeeklyForecast(location: String = "Pretoria"): Result<List<DailyWeather>> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching 7-day weather for: $location")

                val response = weatherApi.getForecast(
                    latitude = PRETORIA_LAT,
                    longitude = PRETORIA_LON
                )

                Log.d(TAG, "API returned ${response.daily.time.size} days")

                val dailyWeather = (0 until minOf(7, response.daily.time.size)).map { index ->
                    val weatherCode = response.daily.weatherCode[index]
                    val day = DailyWeather(
                        day = formatDayName(response.daily.time[index]),
                        minTemp = response.daily.temperatureMin[index].toInt(),
                        maxTemp = response.daily.temperatureMax[index].toInt(),
                        condition = mapWeatherCondition(weatherCode),
                        background = mapWeatherBackground(weatherCode)
                    )

                    Log.d(TAG, "Day ${index + 1}: ${day.day} - ${day.minTemp}°C to ${day.maxTemp}°C - Code: $weatherCode -> ${day.condition}")
                    day
                }

                Log.d(TAG, "Successfully processed ${dailyWeather.size} days")
                Result.success(dailyWeather)

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching weather", e)
                Result.failure(e)
            }
        }

    private fun formatDayName(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            "Day"
        }
    }

    private fun mapWeatherCondition(code: Int): String {
        return when (code) {
            0, 1, 2 -> "sun"                    // Clear, mainly clear, partly cloudy
            3, 45, 48 -> "cloudy"               // Overcast, fog
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "rain"  // Rain and showers
            71, 73, 75, 77, 85, 86 -> "rain"    // Snow
            95, 96, 99 -> "thunder"             // Thunderstorm
            else -> "cloudy"
        }
    }

    private fun mapWeatherBackground(code: Int): String {
        return when (code) {
            0, 1, 2 -> "sun"                    // Clear, mainly clear, partly cloudy
            3, 45, 48 -> "cloudy"               // Overcast, fog
            else -> "rain"                       // All precipitation including thunderstorms
        }
    }
}