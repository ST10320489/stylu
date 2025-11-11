package com.iie.st10320489.stylu.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val TAG = "LocationHelper"

        const val DEFAULT_LAT = -25.7479
        const val DEFAULT_LON = 28.2293
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission")
    suspend fun getCurrentLocation(): Pair<Double, Double> = suspendCoroutine { continuation ->
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted, using default location")
            continuation.resume(Pair(DEFAULT_LAT, DEFAULT_LON))
            return@suspendCoroutine
        }

        try {
            val cancellationToken = CancellationTokenSource()

            // Permission is checked above, safe to call
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d(TAG, "Got location: ${location.latitude}, ${location.longitude}")
                    continuation.resume(Pair(location.latitude, location.longitude))
                } else {
                    Log.w(TAG, "Location was null, using default")
                    continuation.resume(Pair(DEFAULT_LAT, DEFAULT_LON))
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to get location: ${exception.message}", exception)
                continuation.resume(Pair(DEFAULT_LAT, DEFAULT_LON))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}", e)
            continuation.resume(Pair(DEFAULT_LAT, DEFAULT_LON))
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting location: ${e.message}", e)
            continuation.resume(Pair(DEFAULT_LAT, DEFAULT_LON))
        }
    }
}