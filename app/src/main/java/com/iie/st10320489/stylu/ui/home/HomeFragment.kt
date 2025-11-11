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
import com.iie.st10320489.stylu.repository.CalendarRepository
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
    private lateinit var calendarRepository: CalendarRepository // ✅ ADDED: CalendarRepository

    // Outfit views
    private lateinit var ivScheduledOutfit: ImageView
    private lateinit var tvDate: TextView
    private lateinit var tvWeatherLabel: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var calendarBtn: ImageView

    companion object {
        private const val TAG = "HomeFragment"
    }

    private fun updateCurrentDate() {
        val today = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()).format(Date())
        tvDate.text = today
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
        calendarRepository = CalendarRepository(requireContext()) // ✅ ADDED: Initialize CalendarRepository

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

        updateCurrentDate()
    }

    override fun onResume() {
        super.onResume()
        updateCurrentDate()
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
                Log.d(TAG, "🏠 Loading today's outfit...")

                // ✅ FIXED: Calculate today's date range
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endDate = calendar.time

                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                Log.d(TAG, "🗓️ Looking for outfit scheduled for: $dateStr")

                // ✅ FIXED: Use CalendarRepository instead of ApiService
                val result = calendarRepository.getScheduledOutfits(startDate, endDate)

                result.onSuccess { scheduledOutfits ->
                    Log.d(TAG, "📦 Found ${scheduledOutfits.size} scheduled outfits for today")

                    if (scheduledOutfits.isNotEmpty()) {
                        // Get the first (or most relevant) outfit for today
                        val todaysOutfit = scheduledOutfits.first()
                        Log.d(TAG, "👕 Today's outfit: ${todaysOutfit.outfit.name}")
                        displayScheduledOutfit(todaysOutfit.outfit)
                    } else {
                        Log.d(TAG, "No outfit scheduled for today")
                        showNoOutfitScheduled()
                    }
                }.onFailure { error ->
                    Log.w(TAG, "⚠Failed to load today's outfit: ${error.message}")
                    showNoOutfitScheduled()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading today's outfit: ${e.message}", e)
                showNoOutfitScheduled()
            }
        }
    }

    private fun displayScheduledOutfit(outfit: ApiService.OutfitDetail) {
        Log.d(TAG, "Displaying scheduled outfit: ${outfit.name}")

        val savedImagePath = getSavedOutfitImagePath(outfit.outfitId)

        if (savedImagePath.isNotEmpty()) {
            Log.d(TAG, "Loading saved outfit image: $savedImagePath")
            Glide.with(requireContext())
                .load(savedImagePath)
                .placeholder(R.drawable.default_outfit)
                .error(R.drawable.default_outfit)
                .into(ivScheduledOutfit)
        } else if (outfit.items.isNotEmpty()) {
            Log.d(TAG, "🖼️ No saved image, showing first item: ${outfit.items.first().imageUrl}")
            // Fallback: Show first item's image
            Glide.with(requireContext())
                .load(outfit.items.first().imageUrl)
                .placeholder(R.drawable.default_outfit)
                .error(R.drawable.default_outfit)
                .into(ivScheduledOutfit)
        } else {
            Log.d(TAG, "🖼️ No items in outfit, showing default image")
            ivScheduledOutfit.setImageResource(R.drawable.default_outfit)
        }

        // Make outfit clickable to view details
        ivScheduledOutfit.setOnClickListener {
            Log.d(TAG, "🔗 Navigating to outfit detail: ${outfit.outfitId}")
            val bundle = Bundle().apply {
                putInt("outfitId", outfit.outfitId)
            }
            findNavController().navigate(R.id.action_home_to_outfit_detail, bundle)
        }

        // ✅ NEW: Add visual indicator that there's a scheduled outfit
        ivScheduledOutfit.alpha = 1.0f // Full opacity when outfit is scheduled
    }

    private fun showNoOutfitScheduled() {
        Log.d(TAG, "📭 Showing 'no outfit scheduled' state")

        // Show default outfit image
        ivScheduledOutfit.setImageResource(R.drawable.default_outfit)
        ivScheduledOutfit.alpha = 0.7f // Slightly transparent to indicate no schedule

        // Make it clickable to navigate to calendar for scheduling
        ivScheduledOutfit.setOnClickListener {
            Log.d(TAG, "🗓️ Navigating to calendar to schedule outfit")
            Toast.makeText(
                requireContext(),
                "No outfit scheduled for today. Tap to schedule one!",
                Toast.LENGTH_SHORT
            ).show()
            findNavController().navigate(R.id.action_home_to_calendar)
        }
    }

    private fun getSavedOutfitImagePath(outfitId: Int): String {
        val file = File(requireContext().filesDir, "outfit_$outfitId.png")
        val exists = file.exists()
        Log.d(TAG, "🔍 Checking saved image for outfit $outfitId: exists=$exists, path=${file.absolutePath}")
        return if (exists) file.absolutePath else ""
    }
}