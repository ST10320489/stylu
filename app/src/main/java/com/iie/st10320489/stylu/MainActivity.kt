package com.iie.st10320489.stylu

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.iie.st10320489.stylu.auth.SessionManager
import com.iie.st10320489.stylu.databinding.ActivityMainBinding
import com.iie.st10320489.stylu.service.MyFirebaseMessagingService
import com.iie.st10320489.stylu.ui.auth.LoginActivity
import com.iie.st10320489.stylu.utils.LanguageManager
import com.iie.st10320489.stylu.utils.PermissionHelper
import kotlinx.coroutines.launch
import com.google.firebase.messaging.FirebaseMessaging
import com.iie.st10320489.stylu.utils.WorkManagerScheduler
import com.iie.st10320489.stylu.utils.NotificationPreferences
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var sessionManager: SessionManager
    private lateinit var permissionHelper: PermissionHelper

    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    private val topLevelDestinations = setOf(
        R.id.navigation_home,
        R.id.navigation_profile
    )

    companion object {
        private const val TAG = "MainActivity"
        private const val PREF_CRASH_COUNT = "crash_count"
        private const val MAX_CRASH_COUNT = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        // Set up global exception handler FIRST
        setupGlobalExceptionHandler()

        sessionManager = SessionManager(this)

        if (!sessionManager.isAuthenticated()) {
            Log.d(TAG, "Not authenticated - redirecting to login")
            redirectToLogin()
            return
        }

        Log.d(TAG, "User authenticated: ${sessionManager.getCurrentUserEmail()}")

        // Initialize PermissionHelper
        permissionHelper = PermissionHelper(this)

        // Register permission launchers BEFORE any requests
        permissionHelper.registerLaunchers(
            onLocationResult = { granted ->
                if (granted) {
                    Log.d(TAG, "Location permission granted")
                } else {
                    Log.d(TAG, "Location permission denied")
                    Toast.makeText(this, "Weather will use default location", Toast.LENGTH_SHORT).show()
                }
            },
            onNotificationResult = { granted ->
                if (granted) {
                    Log.d(TAG, "Notification permission granted")
                } else {
                    Log.d(TAG, "Notification permission denied")
                }
            }
        )

        // Subscribe to Firebase topic
        FirebaseMessaging.getInstance().subscribeToTopic("fashion")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Subscribed to fashion topic")
                }
            }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        navController = findNavController(R.id.nav_host_fragment_activity_main)

        val appBarConfig = AppBarConfiguration(topLevelDestinations)
        setupActionBarWithNavController(navController, appBarConfig)
        binding.bottomNavigationView.setupWithNavController(navController)

        binding.fab.setImageResource(R.drawable.ic_tshirt)
        binding.fab.setOnClickListener { switchToWardrobeMenu() }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            supportActionBar?.title = destination.label

            binding.toolbar.visibility = if (destination.id == R.id.navigation_home) {
                View.GONE
            } else {
                View.VISIBLE
            }

            val showBackButton = destination.id !in topLevelDestinations
            supportActionBar?.setDisplayHomeAsUpEnabled(showBackButton)

            binding.toolbar.navigationIcon = if (showBackButton) {
                ContextCompat.getDrawable(this, R.drawable.ic_back)
            } else {
                null
            }

            if (destination.id in topLevelDestinations) {
                switchToDefaultMenu()
            }
        }

        switchToDefaultMenu()
        setupBackButtonHandling()
        initializeWeatherNotifications()

        // Request permissions on first launch
        requestPermissionsIfNeeded()

        // Reset crash count on successful launch
        resetCrashCount()
    }

    /**
     * Global exception handler to catch unexpected crashes
     */
    private fun setupGlobalExceptionHandler() {
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "💥 UNCAUGHT EXCEPTION", throwable)

            handleCrash(throwable)

            // Call default handler to let system handle it
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Handle crash gracefully
     */
    private fun handleCrash(throwable: Throwable) {
        try {
            val crashCount = incrementCrashCount()

            Log.e(TAG, "Crash count: $crashCount")
            Log.e(TAG, "Error type: ${throwable.javaClass.simpleName}")
            Log.e(TAG, "Error message: ${throwable.message}")

            // If crashing too many times, clear session and go to login
            if (crashCount >= MAX_CRASH_COUNT) {
                Log.e(TAG, "Too many crashes - clearing session")
                runOnUiThread {
                    sessionManager.clearSession()
                    redirectToLogin()
                }
            } else {
                // Show error dialog on UI thread
                runOnUiThread {
                    showCrashDialog(throwable)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in crash handler", e)
        }
    }

    /**
     * Show crash dialog to user
     */
    private fun showCrashDialog(throwable: Throwable) {
        try {
            val errorType = when {
                throwable.message?.contains("Session expired") == true -> "Session Expired"
                throwable.message?.contains("Authentication") == true -> "Authentication Error"
                throwable.message?.contains("Network") == true -> "Network Error"
                throwable is IllegalStateException -> "App State Error"
                else -> "Unexpected Error"
            }

            AlertDialog.Builder(this)
                .setTitle(errorType)
                .setMessage("The app encountered an error. Would you like to restart or go to login?")
                .setCancelable(false)
                .setPositiveButton("Restart") { _, _ ->
                    restartApp()
                }
                .setNegativeButton("Login") { _, _ ->
                    sessionManager.clearSession()
                    redirectToLogin()
                }
                .setNeutralButton("Exit") { _, _ ->
                    finish()
                    exitProcess(0)
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show crash dialog", e)
            // Fallback - just restart
            restartApp()
        }
    }

    /**
     * Restart the app
     */
    private fun restartApp() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
            exitProcess(0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart app", e)
            finish()
        }
    }

    /**
     * Track crash count
     */
    private fun incrementCrashCount(): Int {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val count = prefs.getInt(PREF_CRASH_COUNT, 0) + 1
        prefs.edit().putInt(PREF_CRASH_COUNT, count).apply()
        return count
    }

    /**
     * Reset crash count on successful launch
     */
    private fun resetCrashCount() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt(PREF_CRASH_COUNT, 0).apply()
    }

    override fun onResume() {
        super.onResume()

        // Validate session on resume
        validateSession()
    }

    /**
     * Validate session is still valid
     */
    private fun validateSession() {
        if (!sessionManager.isAuthenticated()) {
            Log.w(TAG, "Session expired while app was in background")
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
            redirectToLogin()
        }
    }

    /**
     * Request permissions if this is first launch
     */
    private fun requestPermissionsIfNeeded() {
        if (permissionHelper.isFirstLaunch()) {
            Log.d(TAG, "First launch detected - requesting permissions")

            binding.root.postDelayed({
                permissionHelper.requestFirstLaunchPermissions {
                    Log.d(TAG, "First launch permission flow complete")
                }
            }, 1000)
        } else {
            Log.d(TAG, "Not first launch, skipping permission request")
            Log.d(TAG, "Location: ${permissionHelper.hasLocationPermission()}")
            Log.d(TAG, "Notifications: ${permissionHelper.hasNotificationPermission()}")
        }
    }

    private fun initializeWeatherNotifications() {
        val notificationPrefs = NotificationPreferences(this)
        val workManagerScheduler = WorkManagerScheduler(this)

        if (notificationPrefs.isWeatherNotificationEnabled()) {
            if (!workManagerScheduler.isWorkScheduled()) {
                val reminderTime = notificationPrefs.getReminderTime()
                workManagerScheduler.scheduleWeatherNotification(reminderTime)
                Log.d(TAG, "Weather notifications rescheduled at startup")
            }
        }
    }

    private fun setupBackButtonHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentDest = navController.currentDestination?.id

                when {
                    currentDest in topLevelDestinations -> {
                        finish()
                    }
                    else -> {
                        val popped = navController.navigateUp()

                        if (navController.currentDestination?.id in topLevelDestinations) {
                            switchToDefaultMenu()
                        }

                        if (!popped) {
                            finish()
                        }
                    }
                }
            }
        })
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        return when {
            navController.currentDestination?.id !in topLevelDestinations -> {
                val popped = navController.navigateUp()

                if (navController.currentDestination?.id in topLevelDestinations) {
                    switchToDefaultMenu()
                }

                popped
            }
            else -> false
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private fun switchToDefaultMenu() {
        val navView = binding.bottomNavigationView
        val fab = binding.fab

        navView.menu.clear()
        navView.inflateMenu(R.menu.bottom_nav_menu)

        fab.setImageResource(R.drawable.ic_tshirt)
        fab.setOnClickListener { switchToWardrobeMenu() }

        navView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> navController.navigate(R.id.navigation_home)
                R.id.navigation_profile -> navController.navigate(R.id.navigation_profile)
                R.id.navigation_wardrobe -> navController.navigate(R.id.navigation_wardrobe)
            }
            true
        }
    }

    private fun switchToWardrobeMenu() {
        val navView = binding.bottomNavigationView
        val fab = binding.fab

        navView.menu.clear()
        navView.inflateMenu(R.menu.bottom_nav_menu_wardrobe)

        fab.setImageResource(R.drawable.ic_add)
        fab.setOnClickListener { showFabPopup() }

        navView.setOnItemSelectedListener { menuItem ->
            val options = androidx.navigation.navOptions {
                launchSingleTop = true
                popUpTo(R.id.navigation_home) { inclusive = false }
            }

            when (menuItem.itemId) {
                R.id.navigation_my_items -> navController.navigate(R.id.navigation_item, null, options)
                R.id.navigation_my_outfits -> navController.navigate(R.id.navigation_wardrobe, null, options)
                else -> navController.navigate(menuItem.itemId)
            }
            true
        }

        if (navController.currentDestination?.id != R.id.navigation_wardrobe) {
            navController.navigate(R.id.navigation_wardrobe)
        }
    }

    private fun showFabPopup() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_fab_menu, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btn_create_outfit).setOnClickListener {
            val currentDestination = navController.currentDestination?.id

            try {
                when (currentDestination) {
                    R.id.navigation_wardrobe -> {
                        navController.navigate(R.id.action_navigation_wardrobe_to_createOutfitFragment)
                    }
                    R.id.navigation_item -> {
                        navController.navigate(R.id.action_items_to_createOutfit)
                    }
                    else -> {
                        navController.navigate(R.id.navigation_wardrobe)
                        navController.navigate(R.id.action_navigation_wardrobe_to_createOutfitFragment)
                    }
                }
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(this, "Navigation error", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialogView.findViewById<Button>(R.id.btn_add_item).setOnClickListener {
            navController.navigate(R.id.navigation_add_item)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("open_notifications", false) == true) {
            Log.d(TAG, "Opening notifications from notification tap")
            navController.navigate(R.id.navigation_notifications)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Restore default exception handler
        Thread.setDefaultUncaughtExceptionHandler(defaultExceptionHandler)
    }
}