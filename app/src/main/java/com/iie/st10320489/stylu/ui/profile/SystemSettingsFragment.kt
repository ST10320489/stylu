package com.iie.st10320489.stylu.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.iie.st10320489.stylu.MainActivity
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.network.SystemSettings
import com.iie.st10320489.stylu.utils.LanguageManager
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

    // Dropdown options - NOW ONLY 3 LANGUAGES
    private val languageCodes = arrayOf(
        LanguageManager.LANGUAGE_ENGLISH,
        LanguageManager.LANGUAGE_AFRIKAANS,
        LanguageManager.LANGUAGE_XHOSA,
        LanguageManager.LANGUAGE_ZULU,
        LanguageManager.LANGUAGE_TSWANA,
        LanguageManager.LANGUAGE_NDEBELE,
        LanguageManager.LANGUAGE_FRENCH,
        LanguageManager.LANGUAGE_ITALIAN,
        LanguageManager.LANGUAGE_SPANISH,
        LanguageManager.LANGUAGE_VENDA,


    )

    private val temperatureCodes = arrayOf("C", "F")
    private val reminderTimes = arrayOf("06:00", "06:30", "07:00", "07:30", "08:00", "08:30", "09:00", "09:30", "10:00")
    private val sensitivityCodes = arrayOf("low", "normal", "high")

    private var languageChanged = false

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
        // Language Spinner - Get language names from strings
        val languageNames = languageCodes.map { code ->
            LanguageManager.getLanguageDisplayName(requireContext(), code)
        }
        val languageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languageNames)
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spLanguage.adapter = languageAdapter

        // Set current language as selected
        val currentLanguage = LanguageManager.getLanguage(requireContext())
        val currentIndex = languageCodes.indexOf(currentLanguage)
        if (currentIndex >= 0) {
            spLanguage.setSelection(currentIndex)
        }

        // Temperature Unit Spinner
        val temperatureUnits = arrayOf(
            getString(R.string.celsius),
            getString(R.string.fahrenheit)
        )
        val temperatureAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, temperatureUnits)
        temperatureAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spTemperatureUnit.adapter = temperatureAdapter

        // Reminder Time Spinner
        val reminderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, reminderTimes)
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spReminderTime.adapter = reminderAdapter

        // Weather Sensitivity Spinner
        val weatherSensitivities = arrayOf(
            getString(R.string.sensitivity_low),
            getString(R.string.sensitivity_normal),
            getString(R.string.sensitivity_high)
        )
        val sensitivityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, weatherSensitivities)
        sensitivityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spWeatherSensitivity.adapter = sensitivityAdapter

        // Set default values
        setDefaultValues()
    }

    private fun setDefaultValues() {
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
                    Toast.makeText(context, getString(R.string.settings_loaded), Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, getString(R.string.failed_load_settings, error.message), Toast.LENGTH_SHORT).show()
                    // Keep defaults if loading fails
                }

            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.error_loading_settings, e.message), Toast.LENGTH_SHORT).show()
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

                val newLanguageCode = languageCodes[selectedLanguageIndex]
                val currentLanguageCode = LanguageManager.getLanguage(requireContext())

                // Check if language changed
                if (newLanguageCode != currentLanguageCode) {
                    languageChanged = true
                    LanguageManager.setLanguage(requireContext(), newLanguageCode)
                }

                // Create settings object
                val settings = SystemSettings(
                    language = newLanguageCode,
                    temperatureUnit = temperatureCodes[selectedTempIndex],
                    defaultReminderTime = reminderTimes[selectedTimeIndex],
                    weatherSensitivity = sensitivityCodes[selectedSensitivityIndex],
                    notifyWeather = cbNotifyWeather.isChecked,
                    notifyOutfitReminders = cbNotifyOutfitReminders.isChecked
                )

                // Update settings through API
                val result = apiService.updateSystemSettings(settings)

                result.onSuccess { message ->
                    if (languageChanged) {
                        showLanguageChangeDialog()
                    } else {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                }.onFailure { error ->
                    Toast.makeText(context, getString(R.string.failed_update_settings, error.message), Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLanguageChangeDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.language))
            .setMessage(getString(R.string.language_changed))
            .setPositiveButton("OK") { _, _ ->
                // Restart the app to apply language changes
                restartApp()
            }
            .setCancelable(false)
            .show()
    }

    private fun restartApp() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
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
            btnSave.text = getString(R.string.saving)
        } else {
            btnSave.text = getString(R.string.save_changes)
        }
    }
}