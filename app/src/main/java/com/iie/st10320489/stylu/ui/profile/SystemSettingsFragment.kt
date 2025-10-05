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
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.network.SystemSettings
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

    private lateinit var apiService: ApiService

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
        val view = inflater.inflate(R.layout.fragment_system_settings, container, false)

        // Initialize API Service
        apiService = ApiService(requireContext())

        return view
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
                showLoading(true)

                // Fetch system settings from API
                val result = apiService.getCurrentSystemSettings()

                result.onSuccess { settings ->
                    populateSettingsFields(settings)
                    Toast.makeText(context, "Settings loaded successfully", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Failed to load settings: ${error.message}", Toast.LENGTH_SHORT).show()
                    // Keep defaults if loading fails
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Error loading settings: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun populateSettingsFields(settings: SystemSettings) {
        // Set language selection
        val languageIndex = languageCodes.indexOf(settings.language)
        if (languageIndex >= 0) {
            spLanguage.setSelection(languageIndex)
        }

        // Set temperature unit selection
        val tempIndex = temperatureCodes.indexOf(settings.temperatureUnit)
        if (tempIndex >= 0) {
            spTemperatureUnit.setSelection(tempIndex)
        }

        // Set reminder time selection
        val timeIndex = reminderTimes.indexOf(settings.defaultReminderTime)
        if (timeIndex >= 0) {
            spReminderTime.setSelection(timeIndex)
        }

        // Set weather sensitivity selection
        val sensitivityIndex = sensitivityCodes.indexOf(settings.weatherSensitivity)
        if (sensitivityIndex >= 0) {
            spWeatherSensitivity.setSelection(sensitivityIndex)
        }

        // Set notification checkboxes
        cbNotifyWeather.isChecked = settings.notifyWeather
        cbNotifyOutfitReminders.isChecked = settings.notifyOutfitReminders
    }

    private fun saveSystemSettings() {
        lifecycleScope.launch {
            try {
                showLoading(true)

                // Get selected values
                val selectedLanguageIndex = spLanguage.selectedItemPosition
                val selectedTempIndex = spTemperatureUnit.selectedItemPosition
                val selectedTimeIndex = spReminderTime.selectedItemPosition
                val selectedSensitivityIndex = spWeatherSensitivity.selectedItemPosition

                // Create settings object
                val settings = SystemSettings(
                    language = languageCodes[selectedLanguageIndex],
                    temperatureUnit = temperatureCodes[selectedTempIndex],
                    defaultReminderTime = reminderTimes[selectedTimeIndex],
                    weatherSensitivity = sensitivityCodes[selectedSensitivityIndex],
                    notifyWeather = cbNotifyWeather.isChecked,
                    notifyOutfitReminders = cbNotifyOutfitReminders.isChecked
                )

                // Update settings through API
                val result = apiService.updateSystemSettings(settings)

                result.onSuccess { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }.onFailure { error ->
                    Toast.makeText(context, "Failed to update settings: ${error.message}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        btnSave.isEnabled = !isLoading
        btnCancel.isEnabled = !isLoading
        spLanguage.isEnabled = !isLoading
        spTemperatureUnit.isEnabled = !isLoading
        spReminderTime.isEnabled = !isLoading
        spWeatherSensitivity.isEnabled = !isLoading
        cbNotifyWeather.isEnabled = !isLoading
        cbNotifyOutfitReminders.isEnabled = !isLoading

        if (isLoading) {
            btnSave.text = "Saving..."
        } else {
            btnSave.text = "Save Changes"
        }
    }
}