package com.iie.st10320489.stylu

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var sessionManager: SessionManager
    private lateinit var permissionHelper: PermissionHelper

    private val topLevelDestinations = setOf(
        R.id.navigation_home,
        R.id.navigation_profile
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // ✅ Initialize PermissionHelper
        permissionHelper = PermissionHelper(this)

        // Only redirect if truly not authenticated (no session at all)
        if (!sessionManager.isAuthenticated()) {
            Log.d("MainActivity", "No active session, redirecting to login")
            redirectToLogin()
            return
        }

        // ✅ Register permission launchers BEFORE any requests
        permissionHelper.registerLaunchers(
            onLocationResult = { granted ->
                if (granted) {
                    Log.d("MainActivity", "✅ Location permission granted")
                } else {
                    Log.d("MainActivity", "❌ Location permission denied")
                    Toast.makeText(this, "Weather will use default location", Toast.LENGTH_SHORT).show()
                }
            },
            onNotificationResult = { granted ->
                if (granted) {
                    Log.d("MainActivity", "✅ Notification permission granted")
                } else {
                    Log.d("MainActivity", "❌ Notification permission denied")
                }
            }
        )

        // Subscribe to Firebase topic
        FirebaseMessaging.getInstance().subscribeToTopic("fashion")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("MainActivity", "Subscribed to fashion topic")
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

        // ✅ Request permissions on first launch
        requestPermissionsIfNeeded()
    }

    /**
     * ✅ Request permissions if this is first launch
     */
    private fun requestPermissionsIfNeeded() {
        if (permissionHelper.isFirstLaunch()) {
            Log.d("MainActivity", "🎉 First launch detected - requesting permissions")

            // Small delay to let UI settle
            binding.root.postDelayed({
                permissionHelper.requestFirstLaunchPermissions {
                    Log.d("MainActivity", "✅ First launch permission flow complete")
                }
            }, 1000) // 1 second delay
        } else {
            Log.d("MainActivity", "Not first launch, skipping permission request")

            // Log current permission status
            Log.d("MainActivity", "Location: ${permissionHelper.hasLocationPermission()}")
            Log.d("MainActivity", "Notifications: ${permissionHelper.hasNotificationPermission()}")
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
}