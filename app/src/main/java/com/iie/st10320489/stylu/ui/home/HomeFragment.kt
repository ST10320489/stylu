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
import com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit
import com.iie.st10320489.stylu.repository.CalendarRepository
import com.iie.st10320489.stylu.repository.WeatherRepository
import com.iie.st10320489.stylu.utils.LocationHelper
import kotlinx.coroutines.launch
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var weatherAdapter: WeatherAdapter
    private lateinit var rvWeeklyWeather: RecyclerView
    private val weatherRepository = WeatherRepository()
    private lateinit var locationHelper: LocationHelper
    private lateinit var calendarRepository: CalendarRepository

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

        locationHelper = LocationHelper(requireContext())
        calendarRepository = CalendarRepository(requireContext())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestLocationAndFetchWeather()
        setupCalendarButton(view)
        setupNotificationButton(view)
        loadTodayScheduledOutfit()
    }

    private fun setupCalendarButton(view: View) {
        view.findViewById<ImageButton>(R.id.calendarBtn)?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_home_to_calendar)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation error: ${e.message}", e)
                Toast.makeText(
                    requireContext(),
                    "Unable to open calendar",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupNotificationButton(view: View) {
        val notifButton = view.findViewById<ImageButton>(R.id.ivNotification)
        notifButton.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_home_to_notifications)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation error: ${e.message}", e)
                Toast.makeText(
                    requireContext(),
                    "Unable to open notifications",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
                // Get user's current location
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

    /**
     * Load today's scheduled outfit and display it in the card
     */
    private fun loadTodayScheduledOutfit() {
        lifecycleScope.launch {
            try {
                // Get today's date range (00:00 to 23:59)
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endDate = calendar.time

                Log.d(TAG, "Loading scheduled outfit for today: ${startDate}")

                val result = calendarRepository.getScheduledOutfits(startDate, endDate)

                result.onSuccess { outfits ->
                    if (outfits.isNotEmpty()) {
                        Log.d(TAG, "Found scheduled outfit: ${outfits.first().outfit.name}")
                        displayScheduledOutfit(outfits.first())
                    } else {
                        Log.d(TAG, "No scheduled outfit for today")
                        displayDefaultOutfit()
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Failed to load scheduled outfit: ${error.message}", error)
                    displayDefaultOutfit()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading scheduled outfit: ${e.message}", e)
                displayDefaultOutfit()
            }
        }
    }

    /**
     * Display the scheduled outfit in the home card
     */
    private fun displayScheduledOutfit(scheduledOutfit: ScheduledOutfit) {
        view?.let { fragmentView ->
            // Update outfit image
            fragmentView.findViewById<ImageView>(R.id.ivScheduledOutfit)?.let { imageView ->
                if (scheduledOutfit.outfit.items.isNotEmpty()) {
                    // Load the first item's image as outfit preview
                    val firstItemUrl = scheduledOutfit.outfit.items.first().imageUrl

                    Glide.with(this)
                        .load(firstItemUrl)
                        .placeholder(R.drawable.default_outfit)
                        .error(R.drawable.default_outfit)
                        .centerCrop()
                        .into(imageView)

                    Log.d(TAG, "Displaying outfit image: $firstItemUrl")
                } else {
                    imageView.setImageResource(R.drawable.default_outfit)
                }
            }

            // Update weather information if available
            scheduledOutfit.weatherForecast?.let { weather ->
                fragmentView.findViewById<TextView>(R.id.tvTemperature)?.text =
                    "${weather.minTemp}°C - ${weather.maxTemp}°C"

                fragmentView.findViewById<TextView>(R.id.tvPrecipitation)?.text =
                    "${weather.precipitation}%"

                Log.d(TAG, "Weather updated: ${weather.minTemp}°-${weather.maxTemp}°, ${weather.precipitation}%")
            }
        }
    }

    /**
     * Display default outfit when no outfit is scheduled
     */
    private fun displayDefaultOutfit() {
        view?.let { fragmentView ->
            fragmentView.findViewById<ImageView>(R.id.ivScheduledOutfit)?.let { imageView ->
                imageView.setImageResource(R.drawable.default_outfit)
            }

            // Reset to placeholder weather data
            fragmentView.findViewById<TextView>(R.id.tvTemperature)?.text =
                getString(R.string.temperature_range)

            fragmentView.findViewById<TextView>(R.id.tvPrecipitation)?.text =
                getString(R.string.precipitation_value)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh scheduled outfit when user returns to home
        loadTodayScheduledOutfit()
    }
}