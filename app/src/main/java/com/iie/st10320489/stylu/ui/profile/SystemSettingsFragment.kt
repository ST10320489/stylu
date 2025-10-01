package com.iie.st10320489.stylu.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.DirectSupabaseAuth
import com.iie.st10320489.stylu.network.SupabaseProfileService
import kotlinx.coroutines.launch

class SystemSettingsFragment : Fragment() {

    private lateinit var spLanguage: Spinner
    private lateinit var spTemperatureUnit: Spinner
    private lateinit var spReminderTime: Spinner
    private lateinit var spWeatherSensitivity: Spinner
    private lateinit var cbNotifyWeather: CheckBox
    private lateinit var cbNotifyOutfitReminders: CheckBox
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    // Dropdown options
    private val languages = arrayOf("English", "Afrikaans", "Zulu", "Xhosa", "French", "Spanish")
    private val languageCodes = arrayOf("en", "af", "zu", "xh", "fr", "es")
    private val temperatureUnits = arrayOf("Celsius (°C)", "Fahrenheit (°F)")
    private val temperatureCodes = arrayOf("C", "F")
    private val reminderTimes = arrayOf("06:00", "06:30", "07:00", "07:30", "08:00", "08:30", "09:00", "09:30", "10:00")
    private val weatherSensitivities = arrayOf("Low", "Normal", "High")
    private val sensitivityCodes = arrayOf("low", "normal", "high")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_system_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupSpinners()
        setupClickListeners()
        loadCurrentSystemSettings()
    }

    private fun initializeViews(view: View) {
        spLanguage = view.findViewById(R.id.spLanguage)
        spTemperatureUnit = view.findViewById(R.id.spTemperatureUnit)
        spReminderTime = view.findViewById(R.id.spReminderTime)
        spWeatherSensitivity = view.findViewById(R.id.spWeatherSensitivity)
        cbNotifyWeather = view.findViewById(R.id.cbNotifyWeather)
        cbNotifyOutfitReminders = view.findViewById(R.id.cbNotifyOutfitReminders)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)
    }

    private fun setupSpinners() {
        // Language Spinner
        val languageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spLanguage.adapter = languageAdapter

        // Temperature Unit Spinner
        val temperatureAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, temperatureUnits)
        temperatureAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spTemperatureUnit.adapter = temperatureAdapter

        // Reminder Time Spinner
        val reminderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, reminderTimes)
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spReminderTime.adapter = reminderAdapter

        // Weather Sensitivity Spinner
        val sensitivityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, weatherSensitivities)
        sensitivityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spWeatherSensitivity.adapter = sensitivityAdapter

        // Set default values
        setDefaultValues()
    }

    private fun setDefaultValues() {
        spLanguage.setSelection(0) // English
        spTemperatureUnit.setSelection(0) // Celsius
        spReminderTime.setSelection(2) // 07:00
        spWeatherSensitivity.setSelection(1) // Normal
        cbNotifyWeather.isChecked = true
        cbNotifyOutfitReminders.isChecked = true
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            saveSystemSettings()
        }

        btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun loadCurrentSystemSettings() {
        lifecycleScope.launch {
            try {
                // Check if user is logged in
                if (!DirectSupabaseAuth.isLoggedIn()) {
                    Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                    return@launch
                }

                val accessToken = DirectSupabaseAuth.getCurrentAccessToken()
                if (accessToken == null) {
                    Toast.makeText(context, "Session expired", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Fetch profile which contains system settings
                val result = SupabaseProfileService.getCurrentProfile(accessToken)
                result.onSuccess { profile ->
                    populateSettingsFields(profile)
                }.onFailure { error ->
                    Toast.makeText(context, "Failed to load settings: ${error.message}", Toast.LENGTH_SHORT).show()
                    // Keep defaults if loading fails
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Error loading settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populateSettingsFields(profile: com.iie.st10320489.stylu.network.SupabaseUserProfile) {
        // Set language selection
        profile.language?.let { lang ->
            val languageIndex = languageCodes.indexOf(lang)
            if (languageIndex >= 0) {
                spLanguage.setSelection(languageIndex)
            }
        }

        // Set temperature unit selection
        profile.temperatureUnit?.let { unit ->
            val tempIndex = temperatureCodes.indexOf(unit)
            if (tempIndex >= 0) {
                spTemperatureUnit.setSelection(tempIndex)
            }
        }

        // Set reminder time selection
        profile.defaultReminderTime?.let { time ->
            val timeIndex = reminderTimes.indexOf(time)
            if (timeIndex >= 0) {
                spReminderTime.setSelection(timeIndex)
            }
        }

        // Set weather sensitivity selection
        profile.weatherSensitivity?.let { sensitivity ->
            val sensitivityIndex = sensitivityCodes.indexOf(sensitivity)
            if (sensitivityIndex >= 0) {
                spWeatherSensitivity.setSelection(sensitivityIndex)
            }
        }

        // Set notification checkboxes
        profile.notifyWeather?.let { cbNotifyWeather.isChecked = it }
        profile.notifyOutfitReminders?.let { cbNotifyOutfitReminders.isChecked = it }
    }

    private fun saveSystemSettings() {
        lifecycleScope.launch {
            try {
                val accessToken = DirectSupabaseAuth.getCurrentAccessToken()
                if (accessToken == null) {
                    Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Show loading state
                btnSave.isEnabled = false
                btnSave.text = "Saving..."

                // Get selected values
                val selectedLanguageIndex = spLanguage.selectedItemPosition
                val selectedTempIndex = spTemperatureUnit.selectedItemPosition
                val selectedTimeIndex = spReminderTime.selectedItemPosition
                val selectedSensitivityIndex = spWeatherSensitivity.selectedItemPosition

                // Update settings in Supabase
                val result = SupabaseProfileService.updateSystemSettings(
                    accessToken = accessToken,
                    language = languageCodes[selectedLanguageIndex],
                    temperatureUnit = temperatureCodes[selectedTempIndex],
                    defaultReminderTime = reminderTimes[selectedTimeIndex],
                    weatherSensitivity = sensitivityCodes[selectedSensitivityIndex],
                    notifyWeather = cbNotifyWeather.isChecked,
                    notifyOutfitReminders = cbNotifyOutfitReminders.isChecked
                )

                result.onSuccess { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }.onFailure { error ->
                    Toast.makeText(context, "Failed to update settings: ${error.message}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // Reset button state
                btnSave.isEnabled = true
                btnSave.text = "Save Changes"
            }
        }
    }
}