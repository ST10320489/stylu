package com.iie.st10320489.stylu

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.iie.st10320489.stylu.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Get references from binding
        val navView: BottomNavigationView = binding.bottomNavigationView
        val fab: FloatingActionButton = binding.fab

        // ✅ Setup NavController
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        // ✅ Mark destinations that are top-level (no back arrow in toolbar)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_profile,
                R.id.navigation_wardrobe
            )
        )

        // Optional: if you still want ActionBar titles/back navigation
        setupActionBarWithNavController(navController, appBarConfiguration)

        // ✅ Connect BottomNavView with NavController
        navView.setupWithNavController(navController)

        // ✅ FAB Navigation (acts like a 3rd menu item)
        fab.setOnClickListener {
            binding.bottomNavigationView.selectedItemId = R.id.navigation_wardrobe
        }
    }

    // Optional: handle up navigation if you want back button support
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}