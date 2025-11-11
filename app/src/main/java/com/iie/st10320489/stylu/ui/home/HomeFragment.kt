package com.iie.st10320489.stylu.ui.home

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.repository.WeatherRepository
import com.iie.st10320489.stylu.utils.LocationHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var weatherAdapter: WeatherAdapter
    private lateinit var rvWeeklyWeather: RecyclerView
    private val weatherRepository = WeatherRepository()
    private lateinit var locationHelper: LocationHelper
    private lateinit var apiService: ApiService

    // Outfit views
    private lateinit var ivScheduledOutfit: ImageView
    private lateinit var tvDate: TextView
    private lateinit var tvWeatherLabel: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var calendarBtn: ImageView

    companion object {
        private const val TAG = "HomeFragment"
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                Log.d(TAG, "Location permission granted")
                fetchWeather()
            }
            else -> {
                Log.w(TAG, "Location permission denied, using default location")
                Toast.makeText(
                    requireContext(),
                    "Location permission denied. Using Pretoria as default.",
                    Toast.LENGTH_LONG
                ).show()
                fetchWeather()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        rvWeeklyWeather = view.findViewById(R.id.rvWeeklyWeather)
        rvWeeklyWeather.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        // Initialize outfit views
        ivScheduledOutfit = view.findViewById(R.id.ivScheduledOutfit)
        tvDate = view.findViewById(R.id.tvDate)
        tvWeatherLabel = view.findViewById(R.id.tvWeatherLabel)
        tvTemperature = view.findViewById(R.id.tvTemperature)
        calendarBtn = view.findViewById(R.id.calendarBtn)

        locationHelper = LocationHelper(requireContext())
        apiService = ApiService(requireContext())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestLocationAndFetchWeather()
        loadTodaysOutfit()

        val notifButton = view.findViewById<ImageButton>(R.id.ivNotification)
        notifButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_notifications)
        }

        calendarBtn.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_calendar)
        }

        // Set today's date
        val today = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()).format(Date())
        tvDate.text = today
    }

    override fun onResume() {
        super.onResume()
        loadTodaysOutfit()
    }

    private fun requestLocationAndFetchWeather() {
        if (locationHelper.hasLocationPermission()) {
            fetchWeather()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchWeather() {
        Log.d(TAG, "fetchWeather() called")

        lifecycleScope.launch {
            try {
                val (latitude, longitude) = locationHelper.getCurrentLocation()

                Log.d(TAG, "Using coordinates: $latitude, $longitude")

                val result = weatherRepository.getWeeklyForecast(
                    latitude = latitude,
                    longitude = longitude,
                    locationName = "Your Location"
                )

                result.onSuccess { weatherList ->
                    Log.d(TAG, "SUCCESS! Received ${weatherList.size} days")
                    weatherAdapter = WeatherAdapter(weatherList)
                    rvWeeklyWeather.adapter = weatherAdapter

                    // Update today's weather info
                    if (weatherList.isNotEmpty()) {
                        val todayWeather = weatherList[0]
                        tvWeatherLabel.text = todayWeather.condition
                        tvTemperature.text = "${todayWeather.minTemp}° - ${todayWeather.maxTemp}°"
                    }
                }.onFailure { error ->
                    Log.e(TAG, "FAILURE! ${error.message}", error)
                    Toast.makeText(requireContext(), "Failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "EXCEPTION: ${e.message}", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTodaysOutfit() {
        lifecycleScope.launch {
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // Try to get outfit scheduled for today
                val result = apiService.getOutfitForDate(today)
                result.onSuccess { outfit ->
                    if (outfit != null) {
                        Log.d(TAG, "Found outfit for today: ${outfit.name}")
                        displayScheduledOutfit(outfit)
                    } else {
                        Log.d(TAG, "No outfit scheduled for today")
                        // Show default outfit image
                        ivScheduledOutfit.setImageResource(R.drawable.default_outfit)
                        // Make it clickable to navigate to calendar
                        ivScheduledOutfit.setOnClickListener {
                            findNavController().navigate(R.id.action_home_to_calendar)
                        }
                    }
                }.onFailure { error ->
                    // API error (404 or other)
                    Log.w(TAG, "No outfit found for today or API error: ${error.message}")
                    ivScheduledOutfit.setImageResource(R.drawable.default_outfit)
                    // Make it clickable to navigate to calendar
                    ivScheduledOutfit.setOnClickListener {
                        findNavController().navigate(R.id.action_home_to_calendar)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading today's outfit: ${e.message}", e)
                ivScheduledOutfit.setImageResource(R.drawable.default_outfit)
                // Make it clickable to navigate to calendar
                ivScheduledOutfit.setOnClickListener {
                    findNavController().navigate(R.id.action_home_to_calendar)
                }
            }
        }
    }

    private fun displayScheduledOutfit(outfit: ApiService.OutfitDetail) {
        val savedImagePath = getSavedOutfitImagePath(outfit.outfitId)

        if (savedImagePath.isNotEmpty()) {
            Glide.with(requireContext())
                .load(savedImagePath)
                .placeholder(R.drawable.default_outfit)
                .error(R.drawable.default_outfit)
                .into(ivScheduledOutfit)
        } else {
            // If no saved image, show default but log that outfit exists
            Log.d(TAG, "Outfit exists but no saved image found")
            ivScheduledOutfit.setImageResource(R.drawable.default_outfit)
        }

        // Make outfit clickable to view details
        ivScheduledOutfit.setOnClickListener {
            val bundle = Bundle().apply {
                putInt("outfitId", outfit.outfitId)
            }
            findNavController().navigate(R.id.action_home_to_outfit_detail, bundle)
        }
    }

    private fun getSavedOutfitImagePath(outfitId: Int): String {
        val file = File(requireContext().filesDir, "outfit_$outfitId.png")
        return if (file.exists()) file.absolutePath else ""
    }
}