package com.iie.st10320489.stylu.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionHelper(private val activity: AppCompatActivity) {

    companion object {
        private const val TAG = "PermissionHelper"
        private const val PREFS_NAME = "stylu_permissions"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_LOCATION_ASKED = "location_asked"
        private const val KEY_NOTIFICATION_ASKED = "notification_asked"
    }

    private val sharedPrefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)


    private var locationLauncher: ActivityResultLauncher<Array<String>>? = null
    private var notificationLauncher: ActivityResultLauncher<String>? = null

    private var onLocationGranted: (() -> Unit)? = null
    private var onLocationDenied: (() -> Unit)? = null
    private var onNotificationGranted: (() -> Unit)? = null
    private var onNotificationDenied: (() -> Unit)? = null

    /**
     * Register permission launchers - MUST be called in onCreate() before onStart()
     */
    fun registerLaunchers(
        onLocationResult: (Boolean) -> Unit = {},
        onNotificationResult: (Boolean) -> Unit = {}
    ) {
        // Location permission launcher
        locationLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.values.any { it }
            Log.d(TAG, "Location permission result: $granted")

            if (granted) {
                onLocationGranted?.invoke()
                onLocationResult(true)
            } else {
                onLocationDenied?.invoke()
                onLocationResult(false)
            }

            markLocationAsked()
        }

        // Notification permission launcher (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher = activity.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                Log.d(TAG, "Notification permission result: $granted")

                if (granted) {
                    onNotificationGranted?.invoke()
                    onNotificationResult(true)
                } else {
                    onNotificationDenied?.invoke()
                    onNotificationResult(false)
                }

                markNotificationAsked()
            }
        }
    }

    /**
     * Check if this is the first app launch
     */
    fun isFirstLaunch(): Boolean {
        return sharedPrefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    /**
     * Mark first launch as complete
     */
    fun markFirstLaunchComplete() {
        sharedPrefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    /**
     * Check if we've already asked for location permission
     */
    fun hasAskedForLocation(): Boolean {
        return sharedPrefs.getBoolean(KEY_LOCATION_ASKED, false)
    }

    /**
     * Mark that we've asked for location
     */
    private fun markLocationAsked() {
        sharedPrefs.edit().putBoolean(KEY_LOCATION_ASKED, true).apply()
    }

    /**
     * Check if we've already asked for notification permission
     */
    fun hasAskedForNotification(): Boolean {
        return sharedPrefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
    }

    /**
     * Mark that we've asked for notification
     */
    private fun markNotificationAsked() {
        sharedPrefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
    }

    /**
     * Check if location permission is granted
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Notifications don't need permission below Android 13
        }
    }

    /**
     * Request location permission with rationale
     */
    fun requestLocationPermission(
        onGranted: () -> Unit = {},
        onDenied: () -> Unit = {}
    ) {
        onLocationGranted = onGranted
        onLocationDenied = onDenied

        // Check if already granted
        if (hasLocationPermission()) {
            Log.d(TAG, "Location permission already granted")
            onGranted()
            return
        }

        // Show rationale if needed
        if (activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            showLocationRationale {
                launchLocationPermissionRequest()
            }
        } else {
            launchLocationPermissionRequest()
        }
    }

    /**
     * Request notification permission (Android 13+)
     */
    fun requestNotificationPermission(
        onGranted: () -> Unit = {},
        onDenied: () -> Unit = {}
    ) {
        onNotificationGranted = onGranted
        onNotificationDenied = onDenied

        // Check Android version
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Notification permission not needed on Android < 13")
            onGranted()
            return
        }

        // Check if already granted
        if (hasNotificationPermission()) {
            Log.d(TAG, "Notification permission already granted")
            onGranted()
            return
        }

        // Show rationale
        showNotificationRationale {
            launchNotificationPermissionRequest()
        }
    }

    /**
     * Request ALL permissions needed for first launch
     */
    fun requestFirstLaunchPermissions(
        onComplete: () -> Unit = {}
    ) {
        Log.d(TAG, "Requesting first launch permissions...")

        // Request location first
        requestLocationPermission(
            onGranted = {
                Log.d(TAG, "Location granted, now requesting notifications...")
                // Then request notifications
                requestNotificationPermission(
                    onGranted = {
                        Log.d(TAG, "All permissions granted")
                        markFirstLaunchComplete()
                        onComplete()
                    },
                    onDenied = {
                        Log.d(TAG, "Notification denied, but continuing...")
                        markFirstLaunchComplete()
                        onComplete()
                    }
                )
            },
            onDenied = {
                Log.d(TAG, "Location denied, but continuing...")
                // Still request notifications even if location denied
                requestNotificationPermission(
                    onGranted = {
                        Log.d(TAG, "Notification granted")
                        markFirstLaunchComplete()
                        onComplete()
                    },
                    onDenied = {
                        Log.d(TAG, "Both permissions denied")
                        markFirstLaunchComplete()
                        onComplete()
                    }
                )
            }
        )
    }

    private fun launchLocationPermissionRequest() {
        locationLauncher?.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun launchNotificationPermissionRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showLocationRationale(onProceed: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Location Permission Needed")
            .setMessage("Stylu needs access to your location to show you local weather and personalized outfit recommendations based on your area's climate.")
            .setPositiveButton("Allow") { _, _ ->
                onProceed()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
                onLocationDenied?.invoke()
                markLocationAsked()
            }
            .setCancelable(false)
            .show()
    }

    private fun showNotificationRationale(onProceed: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Enable Notifications")
            .setMessage("Stay updated with:\n• New fashion trends\n• Outfit recommendations\n• Weather alerts\n• Style tips")
            .setPositiveButton("Enable") { _, _ ->
                onProceed()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
                onNotificationDenied?.invoke()
                markNotificationAsked()
            }
            .setCancelable(false)
            .show()
    }
}