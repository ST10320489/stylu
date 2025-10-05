package com.iie.st10320489.stylu.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.FragmentHomeBinding
import com.iie.st10320489.stylu.ui.home.models.DailyWeather

class HomeFragment : Fragment() {

    private lateinit var weatherAdapter: WeatherAdapter
    private lateinit var rvWeeklyWeather: RecyclerView

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
        // Use Retrofit or Ktor to fetch 7-day forecast from WeatherAPI
        // Example: https://www.weatherapi.com/docs/#forecast
        // Parse response into List<DailyWeather> and submit to adapter

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
