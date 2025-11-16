package com.iie.st10320489.stylu.ui.home

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
    private lateinit var calendarRepository: CalendarRepository

    // Views
    private lateinit var ivScheduledOutfit: ImageView
    private lateinit var tvDate: TextView
    private lateinit var tvWeatherLabel: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var calendarBtn: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout // ✅ NEW

    // Track if this is the first load
    private var isFirstLoad = true

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
                fetchWeatherAndOutfit()
            }
            else -> {
                Log.w(TAG, "Location permission denied, using default location")
                Toast.makeText(
                    requireContext(),
                    "Location permission denied. Using Pretoria as default.",
                    Toast.LENGTH_LONG
                ).show()
                fetchWeatherAndOutfit()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize views
        rvWeeklyWeather = view.findViewById(R.id.rvWeeklyWeather)
        ivScheduledOutfit = view.findViewById(R.id.ivScheduledOutfit)
        tvDate = view.findViewById(R.id.tvDate)
        tvWeatherLabel = view.findViewById(R.id.tvWeatherLabel)
        tvTemperature = view.findViewById(R.id.tvTemperature)
        calendarBtn = view.findViewById(R.id.calendarBtn)
        progressBar = view.findViewById(R.id.progressBar) // ✅ NEW
        swipeRefresh = view.findViewById(R.id.swipeRefresh) // ✅ NEW

        // Setup RecyclerView
        rvWeeklyWeather.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        locationHelper = LocationHelper(requireContext())
        calendarRepository = CalendarRepository(requireContext())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSwipeRefresh() // ✅ NEW
        requestLocationAndFetchWeather()

        val notifButton = view.findViewById<ImageButton>(R.id.ivNotification)
        notifButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_notifications)
        }

        calendarBtn.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_calendar)
        }

        updateCurrentDate()
    }

    // ✅ NEW: Setup SwipeRefreshLayout
    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            isFirstLoad = false
            fetchWeatherAndOutfit(forceRefresh = true)
        }

        swipeRefresh.setColorSchemeResources(
            R.color.purple_primary,
            R.color.orange_secondary
        )
    }

    override fun onResume() {
        super.onResume()
        updateCurrentDate()
        // Don't show "first load" message on resume
        isFirstLoad = false
        fetchWeatherAndOutfit(forceRefresh = false)
    }

    private fun requestLocationAndFetchWeather() {
        if (locationHelper.hasLocationPermission()) {
            fetchWeatherAndOutfit()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /**
     * ✅ NEW: Combined loading method for weather and outfit
     * Loads both in parallel for better performance
     */
    private fun fetchWeatherAndOutfit(forceRefresh: Boolean = false) {
        lifecycleScope.launch {
            try {
                // Show loading message only on first load
                if (isFirstLoad && !forceRefresh) {
                    showLoadingWithMessage(
                        show = true,
                        message = "Loading your home screen...\n\n" +
                                "First load may take up to 60 seconds if the server is starting up."
                    )
                } else {
                    showLoading(true)
                }

                // Load weather and outfit in parallel
                val weatherJob = launch { fetchWeather() }
                val outfitJob = launch { loadTodaysOutfit() }

                // Wait for both to complete
                weatherJob.join()
                outfitJob.join()

                // Mark first load as complete
                isFirstLoad = false

            } catch (e: Exception) {
                Log.e(TAG, "Error loading home screen", e)
                handleLoadError(e)
            } finally {
                showLoading(false)
                showLoadingWithMessage(show = false, message = "")
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private suspend fun fetchWeather() {
        Log.d(TAG, "fetchWeather() called")

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
                Log.e(TAG, "Weather FAILURE! ${error.message}", error)
                handleWeatherError(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Weather EXCEPTION: ${e.message}", e)
            handleWeatherError(e)
        }
    }

    private suspend fun loadTodaysOutfit() {
        try {
            Log.d(TAG, "🏠 Loading today's outfit...")

            // Calculate today's date range
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

            // Use CalendarRepository
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
                Log.w(TAG, "⚠️Failed to load today's outfit: ${error.message}")
                handleOutfitError(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading today's outfit: ${e.message}", e)
            showNoOutfitScheduled()
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

        // Add visual indicator that there's a scheduled outfit
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

    // ✅ NEW: Better error handling
    private fun handleLoadError(error: Throwable) {
        val errorMessage = when {
            error.message?.contains("timed out", ignoreCase = true) == true -> {
                "Server is starting up. This can take up to 60 seconds on first request.\n\n" +
                        "Please try again in a moment."
            }
            error.message?.contains("starting up", ignoreCase = true) == true -> {
                error.message ?: "Server is starting..."
            }
            error.message?.contains("authentication", ignoreCase = true) == true -> {
                "Session expired. Please log in again."
            }
            else -> "Error loading home screen: ${error.message}"
        }

        if (isAdded && context != null) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleWeatherError(error: Throwable) {
        val errorMessage = when {
            error.message?.contains("location", ignoreCase = true) == true -> {
                "Unable to get location. Using default weather."
            }
            error.message?.contains("timed out", ignoreCase = true) == true -> {
                "Weather service timeout. Using cached data."
            }
            else -> "Weather unavailable: ${error.message}"
        }

        Log.w(TAG, errorMessage)
        // Don't show toast for weather errors - not critical
    }

    private fun handleOutfitError(error: Throwable) {
        val errorMessage = when {
            error.message?.contains("cached data", ignoreCase = true) == true -> {
                "📱 Offline mode - showing cached outfit"
            }
            error.message?.contains("connect", ignoreCase = true) == true -> {
                "📱 No internet - showing cached outfit"
            }
            else -> null // Don't show message for missing outfit
        }

        if (errorMessage != null) {
            Log.w(TAG, errorMessage)
        }

        // Always show "no outfit" state on error
        showNoOutfitScheduled()
    }

    // ✅ NEW: Loading with message support - hides content while loading
    private fun showLoadingWithMessage(show: Boolean, message: String) {
        try {
            if (show) {
                progressBar.visibility = View.VISIBLE
                swipeRefresh.visibility = View.GONE // ✅ Hide content while loading
                if (message.isNotEmpty() && isAdded && context != null) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            } else {
                progressBar.visibility = View.GONE
                swipeRefresh.visibility = View.VISIBLE // ✅ Show content when done
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in showLoadingWithMessage: ${e.message}")
        }
    }

    private fun showLoading(show: Boolean) {
        try {
            if (show) {
                progressBar.visibility = View.VISIBLE
                // Don't hide content for quick refreshes
            } else {
                progressBar.visibility = View.GONE
                swipeRefresh.visibility = View.VISIBLE // ✅ Ensure content is visible
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in showLoading: ${e.message}")
        }
    }
}