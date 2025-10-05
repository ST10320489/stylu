package com.iie.st10320489.stylu.ui.home

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.repository.WeatherRepository
import com.iie.st10320489.stylu.utils.LocationHelper
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var weatherAdapter: WeatherAdapter
    private lateinit var rvWeeklyWeather: RecyclerView
    private val weatherRepository = WeatherRepository()
    private lateinit var locationHelper: LocationHelper

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
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestLocationAndFetchWeather()
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
}