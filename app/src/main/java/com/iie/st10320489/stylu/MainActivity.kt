package com.iie.st10320489.stylu

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.iie.st10320489.stylu.databinding.ActivityMainBinding
import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import com.iie.st10320489.stylu.ui.auth.LoginActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // Top-level destinations (no back button)
    private val topLevelDestinations = setOf(
        R.id.navigation_home,
        R.id.navigation_profile,
        R.id.navigation_wardrobe
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is authenticated
        if (!DirectSupabaseAuth.isLoggedIn()) {
            redirectToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_back)

        // Setup NavController
        navController = findNavController(R.id.nav_host_fragment_activity_main)

        // Setup AppBar with top-level destinations
        val appBarConfig = AppBarConfiguration(topLevelDestinations)
        setupActionBarWithNavController(navController, appBarConfig)

        // Setup BottomNavigationView
        binding.bottomNavigationView.setupWithNavController(navController)

        // Setup FAB default click = switch to wardrobe
        binding.fab.setImageResource(R.drawable.ic_tshirt)
        binding.fab.setOnClickListener { switchToWardrobeMenu() }

        // Display user info
        displayUserInfo()

        // Listen for navigation changes
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Update bottom nav selection safely
            binding.bottomNavigationView.menu.findItem(destination.id)?.isChecked = true

            if (destination.id == R.id.navigation_home) {
                // Hide toolbar on Home
                binding.toolbar.visibility = View.GONE
            } else {
                // Show toolbar on other pages
                binding.toolbar.visibility = View.VISIBLE
            }

            // Show custom back button only for non-top-level destinations
            if (destination.id !in topLevelDestinations) {
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_back)
            } else {
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
                binding.toolbar.navigationIcon = null
            }
        }

        // Setup default menu
        switchToDefaultMenu()
    }

    private fun displayUserInfo() {
        val user = DirectSupabaseAuth.getCurrentUser()
        user?.let {
            Toast.makeText(this, "Welcome ${it.email}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Handle custom back button
    override fun onSupportNavigateUp(): Boolean {
        return if (navController.currentDestination?.id !in topLevelDestinations) {
            // Always go back to Home when back button is pressed
            navController.navigate(R.id.navigation_home)

            // Reset bottom nav and FAB to default
            switchToDefaultMenu()

            true
        } else {
            false
        }
    }

    /** Switch FAB and bottom nav to default (home/profile) */
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

    /** Switch FAB and bottom nav to wardrobe menu */
    private fun switchToWardrobeMenu() {
        val navView = binding.bottomNavigationView
        val fab = binding.fab

        navView.menu.clear()
        navView.inflateMenu(R.menu.bottom_nav_menu_wardrobe)

        fab.setImageResource(R.drawable.ic_add)
        fab.setOnClickListener { showFabDropdown() }

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

        // Navigate to wardrobe fragment if not already there
        if (navController.currentDestination?.id != R.id.navigation_wardrobe) {
            navController.navigate(R.id.navigation_wardrobe)
        }
    }

    private fun showFabDropdown() {
        val popupMenu = PopupMenu(this, binding.fab)
        popupMenu.menuInflater.inflate(R.menu.fab_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_add_item -> { /* TODO: open Add Item */ true }
                R.id.action_add_outfit -> { /* TODO: open Add Outfit */ true }
                else -> false
            }
        }
        popupMenu.show()
    }

//    override fun onCreateOptionsMenu(menu: Menu): Boolean {
//        menuInflater.inflate(R.menu.main, menu)
//        return true
//    }
//
//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        return when (item.itemId) {
//            R.id.action_logout -> {
//                logout()
//                true
//            }
//            else -> super.onOptionsItemSelected(item)
//        }
//    }

    private fun logout() {
        lifecycleScope.launch {
            try {
                DirectSupabaseAuth.signOut()
                redirectToLogin()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Logout failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}