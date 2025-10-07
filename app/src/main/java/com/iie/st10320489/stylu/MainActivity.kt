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
import android.widget.Button
import androidx.activity.OnBackPressedCallback


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val topLevelDestinations = setOf(
        R.id.navigation_home,
        R.id.navigation_profile
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!DirectSupabaseAuth.isLoggedIn()) {
            redirectToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_back)

        navController = findNavController(R.id.nav_host_fragment_activity_main)

        val appBarConfig = AppBarConfiguration(topLevelDestinations)
        setupActionBarWithNavController(navController, appBarConfig)

        binding.bottomNavigationView.setupWithNavController(navController)

        binding.fab.setImageResource(R.drawable.ic_tshirt)
        binding.fab.setOnClickListener { switchToWardrobeMenu() }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            supportActionBar?.title = destination.label

            if (destination.id == R.id.navigation_home) {
                binding.toolbar.visibility = View.GONE
            } else {
                binding.toolbar.visibility = View.VISIBLE
            }

            supportActionBar?.setDisplayHomeAsUpEnabled(destination.id !in topLevelDestinations)
            binding.toolbar.navigationIcon = if (destination.id !in topLevelDestinations)
                ContextCompat.getDrawable(this, R.drawable.ic_back)
            else null
        }

        switchToDefaultMenu()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navigateBackToHome()) {
                    finish()
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
        return if (navController.currentDestination?.id !in topLevelDestinations) {
            navController.navigate(R.id.navigation_home)
            switchToDefaultMenu()
            true
        } else {
            false
        }
    }

    private fun navigateBackToHome(): Boolean {
        val currentId = navController.currentDestination?.id
        return if (currentId != null && currentId !in topLevelDestinations) {
            navController.navigate(R.id.navigation_home)
            switchToDefaultMenu()
            true
        } else {
            false
        }
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
                Toast.makeText(this, "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
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