package com.iie.st10320489.stylu.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.repository.WeatherRepository
import com.iie.st10320489.stylu.ui.home.models.DailyWeather
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var weatherAdapter: WeatherAdapter
    private lateinit var rvWeeklyWeather: RecyclerView
    private val weatherRepository = WeatherRepository()

    companion object {
        private const val TAG = "HomeFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        rvWeeklyWeather = view.findViewById(R.id.rvWeeklyWeather)
        rvWeeklyWeather.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchWeather()
    }

    private fun fetchWeather() {
        Log.d(TAG, "fetchWeather() called")

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Calling weatherRepository.getWeeklyForecast...")
                val result = weatherRepository.getWeeklyForecast("Pretoria")

                result.onSuccess { weatherList ->
                    Log.d(TAG, "SUCCESS! Received ${weatherList.size} days of weather data")

                    if (weatherList.isNotEmpty()) {
                        val firstDay = weatherList[0]
                        Log.d(TAG, "First day: ${firstDay.day}, ${firstDay.minTemp}-${firstDay.maxTemp}°C, ${firstDay.condition}")
                    }

                    weatherAdapter = WeatherAdapter(weatherList)
                    rvWeeklyWeather.adapter = weatherAdapter

                    Toast.makeText(requireContext(), "Weather loaded for Pretoria!", Toast.LENGTH_SHORT).show()

                }.onFailure { error ->
                    Log.e(TAG, "FAILURE! Error: ${error.message}", error)

                    Toast.makeText(
                        requireContext(),
                        "Weather API failed: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    loadSampleData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "EXCEPTION caught: ${e.message}", e)

                Toast.makeText(
                    requireContext(),
                    "Exception: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                loadSampleData()
            }
        }
    }

    private fun loadSampleData() {
        Log.d(TAG, "Loading SAMPLE data (fallback)")

        val sampleData = listOf(
            DailyWeather("Mon", 12, 25, "sun", "sun"),
            DailyWeather("Tue", 14, 23, "cloudy", "cloudy"),
            DailyWeather("Wed", 10, 22, "rain", "rain"),
            DailyWeather("Thu", 15, 28, "sun", "sun"),
            DailyWeather("Fri", 13, 24, "cloudy", "cloudy"),
            DailyWeather("Sat", 11, 21, "thunder", "rain"),
            DailyWeather("Sun", 16, 29, "sun", "sun")
        )
        weatherAdapter = WeatherAdapter(sampleData)
        rvWeeklyWeather.adapter = weatherAdapter
    }
}