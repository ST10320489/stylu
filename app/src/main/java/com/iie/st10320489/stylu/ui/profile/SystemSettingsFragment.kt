package com.iie.st10320489.stylu.ui.profile

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.work.WorkInfo
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController

import com.iie.st10320489.stylu.MainActivity
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.network.SystemSettings
import com.iie.st10320489.stylu.utils.LanguageManager
import com.iie.st10320489.stylu.utils.NotificationPreferences
import com.iie.st10320489.stylu.utils.WorkManagerScheduler
import kotlinx.coroutines.launch
import androidx.work.WorkManager
import com.iie.st10320489.stylu.utils.WeatherNotificationDebugHelper
import java.util.concurrent.TimeUnit

class SystemSettingsFragment : Fragment() {

    private lateinit var spLanguage: Spinner
    private lateinit var spTemperatureUnit: Spinner
    private lateinit var tvReminderTime: TextView
    private lateinit var spWeatherSensitivity: Spinner
    private lateinit var cbNotifyWeather: CheckBox
    private lateinit var cbNotifyOutfitReminders: CheckBox
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private lateinit var apiService: ApiService
    private lateinit var notificationPrefs: NotificationPreferences
    private lateinit var workManagerScheduler: WorkManagerScheduler


    private lateinit var btnTestWeatherNotification: Button
    private lateinit var btnCheckAuth: Button
    private lateinit var btnCheckWorker: Button


    companion object {
        private const val TAG = "SystemSettingsFragment"
    }

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
    private val sensitivityCodes = arrayOf("low", "normal", "high")
    private var languageChanged = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_system_settings, container, false)

        apiService = ApiService(requireContext())
        notificationPrefs = NotificationPreferences(requireContext())
        workManagerScheduler = WorkManagerScheduler(requireContext())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupSpinners()
        setupClicks()
        loadCurrentSystemSettings()
    }

    private fun initializeViews(view: View) {
        spLanguage = view.findViewById(R.id.spLanguage)
        spTemperatureUnit = view.findViewById(R.id.spTemperatureUnit)
        tvReminderTime = view.findViewById(R.id.tvReminderTime)
        spWeatherSensitivity = view.findViewById(R.id.spWeatherSensitivity)
        cbNotifyWeather = view.findViewById(R.id.cbNotifyWeather)
        cbNotifyOutfitReminders = view.findViewById(R.id.cbNotifyOutfitReminders)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)


        //test
        btnTestWeatherNotification = view.findViewById(R.id.btnTestWeatherNotification)
        btnCheckAuth = view.findViewById(R.id.btnCheckAuth)
        btnCheckWorker = view.findViewById(R.id.btnCheckWorker)
    }

    private fun setupSpinners() {
        val languageNames = languageCodes.map {
            LanguageManager.getLanguageDisplayName(requireContext(), it)
        }

        spLanguage.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            languageNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val currentLanguage = LanguageManager.getLanguage(requireContext())
        val currentIndex = languageCodes.indexOf(currentLanguage)
        if (currentIndex >= 0) {
            spLanguage.setSelection(currentIndex)
        }

        spTemperatureUnit.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            arrayOf(getString(R.string.celsius), getString(R.string.fahrenheit))
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spWeatherSensitivity.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            arrayOf(
                getString(R.string.sensitivity_low),
                getString(R.string.sensitivity_normal),
                getString(R.string.sensitivity_high)
            )
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        setDefaults()
    }

    private fun setDefaults() {
        spTemperatureUnit.setSelection(0)
        spWeatherSensitivity.setSelection(1)

        // Load saved time FIRST
        val savedTime = notificationPrefs.getReminderTime()
        tvReminderTime.text = savedTime
        Log.d(TAG, "Loaded saved time: $savedTime")

        // Then load saved checkbox state
        val isWeatherEnabled = notificationPrefs.isWeatherNotificationEnabled()
        cbNotifyWeather.isChecked = isWeatherEnabled
        cbNotifyOutfitReminders.isChecked = true

        Log.d(TAG, "Loaded weather enabled state: $isWeatherEnabled")

        // Enable/disable time picker based on checkbox
        updateTimePickerState()
    }

    private fun setupClicks() {
        // Time picker click listener
        tvReminderTime.setOnClickListener {
            if (!cbNotifyWeather.isChecked) {
                Toast.makeText(requireContext(), "Enable weather notifications first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentTime = tvReminderTime.text.toString().split(":")
            val hour = currentTime[0].toInt()
            val minute = currentTime[1].toInt()

            TimePickerDialog(requireContext(), { _, h, m ->
                val formatted = String.format("%02d:%02d", h, m)
                tvReminderTime.text = formatted
                Log.d(TAG, "Time selected: $formatted")
            }, hour, minute, true).show()
        }

        // Checkbox listener - update time picker enabled state
        cbNotifyWeather.setOnCheckedChangeListener { _, isChecked ->
            updateTimePickerState()
            Log.d(TAG, "Weather notifications checkbox changed: $isChecked")
        }

        btnSave.setOnClickListener { saveSystemSettings() }
        btnCancel.setOnClickListener { findNavController().popBackStack() }
        setupTestButtons()
    }

   
    private fun setupTestButtons() {
        // ========== Test Weather Notification ==========
        btnTestWeatherNotification.setOnClickListener {
            lifecycleScope.launch {
                try {
                    Log.d("TEST", "=== TEST WEATHER NOTIFICATION ===")

                    // Clear the "sent today" flag so notification can be sent
                    val prefs = requireContext().getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
                    prefs.edit().remove("last_notification_date").apply()
                    Log.d("TEST", "Cleared 'sent today' flag")

                    // Trigger test notification (5 second delay)
                    WeatherNotificationDebugHelper.triggerTestNotification(requireContext())

                    Toast.makeText(
                        requireContext(),
                        "☀️ Test notification in 5 seconds!\n\nWatch for:\n• System notification\n• NotificationsFragment update\n• Logcat output",
                        Toast.LENGTH_LONG
                    ).show()

                    Log.d("TEST", "Test scheduled - watch for WeatherNotificationWorker logs")

                } catch (e: Exception) {
                    Log.e("TEST", "Error triggering test: ${e.message}", e)
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ========== Check Auth Status ==========
        btnCheckAuth.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)
            val accessToken = prefs.getString("access_token", null)
            val userId = prefs.getString("user_id", null)

            Log.d("TEST", "=== AUTH STATUS ===")
            Log.d("TEST", "Access Token: ${if (accessToken != null) "✅ Present (${accessToken.length} chars)" else "❌ Missing"}")
            Log.d("TEST", "User ID: ${userId ?: "❌ Missing"}")

            val isUUID = userId?.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) == true
            Log.d("TEST", "Is UUID format: $isUUID")

            val status = buildString {
                appendLine("Access Token: ${if (accessToken != null) "✅ Found" else "❌ Missing"}")
                appendLine("User ID: ${userId ?: "❌ Missing"}")
                appendLine("Format: ${if (isUUID) "✅ UUID" else "❌ Not UUID"}")
            }

            AlertDialog.Builder(requireContext())
                .setTitle("🔐 Authentication Status")
                .setMessage(status)
                .setPositiveButton("OK", null)
                .show()

            Toast.makeText(requireContext(), "Check Logcat for details", Toast.LENGTH_SHORT).show()
        }

        // ========== Check Worker Status (FIXED - works with old WorkManager) ==========
        btnCheckWorker.setOnClickListener {
            lifecycleScope.launch {
                try {
                    Log.d("TEST", "=== WORKER STATUS ===")

                    val notificationPrefs = NotificationPreferences(requireContext())
                    val isEnabled = notificationPrefs.isWeatherNotificationEnabled()
                    val reminderTime = notificationPrefs.getReminderTime()

                    Log.d("TEST", "Notifications enabled: $isEnabled")
                    Log.d("TEST", "Reminder time: $reminderTime")

                    val workManager = WorkManager.getInstance(requireContext())
                    val workInfos = workManager.getWorkInfosForUniqueWork("weather_notification_work").get()

                    val status = buildString {
                        appendLine("⚙️ Worker Status\n")
                        appendLine("Notifications Enabled: ${if (isEnabled) "✅ Yes" else "❌ No"}")
                        appendLine("Scheduled Time: $reminderTime\n")

                        if (workInfos.isEmpty()) {
                            appendLine("Work Status: ❌ Not Scheduled")
                            appendLine("\n💡 Enable weather notifications in settings to schedule work")
                            Log.d("TEST", "❌ No work scheduled")
                        } else {
                            workInfos.forEachIndexed { index, workInfo ->
                                if (index > 0) appendLine("\n---\n")

                                appendLine("Work #${index + 1}")
                                appendLine("State: ${workInfo.state}")
                                appendLine("Run Attempts: ${workInfo.runAttemptCount}")

                                Log.d("TEST", "Work #${index + 1}: ${workInfo.state}")

                                when (workInfo.state) {
                                    WorkInfo.State.ENQUEUED -> {
                                        appendLine("Status: ✅ Scheduled")
                                        appendLine("Will run at next scheduled time")
                                        Log.d("TEST", "Status: ENQUEUED (scheduled)")
                                    }
                                    WorkInfo.State.RUNNING -> {
                                        appendLine("Status: 🔄 Currently Running")
                                        Log.d("TEST", "Status: RUNNING")
                                    }
                                    WorkInfo.State.SUCCEEDED -> {
                                        appendLine("Status: ✅ Last Run Succeeded")
                                        Log.d("TEST", "Status: SUCCEEDED")
                                    }
                                    WorkInfo.State.FAILED -> {
                                        appendLine("Status: ❌ Last Run Failed")
                                        Log.d("TEST", "Status: FAILED")
                                    }
                                    WorkInfo.State.CANCELLED -> {
                                        appendLine("Status: ⚠️ Cancelled")
                                        Log.d("TEST", "Status: CANCELLED")
                                    }
                                    else -> {
                                        appendLine("Status: Unknown")
                                    }
                                }
                            }
                        }
                    }

                    AlertDialog.Builder(requireContext())
                        .setTitle("Worker Status")
                        .setMessage(status.toString())
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .setNeutralButton("Reschedule Now") { _, _ ->
                            if (isEnabled) {
                                val scheduler = WorkManagerScheduler(requireContext())
                                scheduler.scheduleWeatherNotification(reminderTime)
                                Toast.makeText(requireContext(), "✅ Work rescheduled", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(requireContext(), "❌ Enable notifications first", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .show()

                    Log.d("TEST", "=== END WORKER CHECK ===")

                } catch (e: Exception) {
                    Log.e("TEST", "Error checking worker: ${e.message}", e)
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateTimePickerState() {
        val isEnabled = cbNotifyWeather.isChecked
        tvReminderTime.isEnabled = isEnabled
        tvReminderTime.alpha = if (isEnabled) 1.0f else 0.5f
    }

    private fun loadCurrentSystemSettings() {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val result = apiService.getCurrentSystemSettings()

                result.onSuccess { settings ->
                    populateSettingsFields(settings)
                    Toast.makeText(context, getString(R.string.settings_loaded), Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Log.w(TAG, "Failed to load API settings: ${error.message}")
                    Toast.makeText(context, getString(R.string.failed_load_settings, error.message), Toast.LENGTH_SHORT).show()
                    // Keep using local preferences on API failure
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading settings", e)
                Toast.makeText(context, getString(R.string.error_loading_settings, e.message), Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun populateSettingsFields(settings: SystemSettings) {
        val languageIndex = languageCodes.indexOf(settings.language)
        if (languageIndex >= 0) {
            spLanguage.setSelection(languageIndex)
        }

        val tempIndex = temperatureCodes.indexOf(settings.temperatureUnit)
        if (tempIndex >= 0) {
            spTemperatureUnit.setSelection(tempIndex)
        }

        // Only update time if it's different from what's saved locally
        val currentSavedTime = notificationPrefs.getReminderTime()
        if (settings.defaultReminderTime != currentSavedTime) {
            tvReminderTime.text = settings.defaultReminderTime
            notificationPrefs.setReminderTime(settings.defaultReminderTime)
            Log.d(TAG, "Updated time from API: ${settings.defaultReminderTime}")
        } else {
            Log.d(TAG, "Keeping existing local time: $currentSavedTime")
        }

        val sensitivityIndex = sensitivityCodes.indexOf(settings.weatherSensitivity)
        if (sensitivityIndex >= 0) {
            spWeatherSensitivity.setSelection(sensitivityIndex)
        }

        // Only update checkbox if different from local preference
        val currentEnabled = notificationPrefs.isWeatherNotificationEnabled()
        if (settings.notifyWeather != currentEnabled) {
            cbNotifyWeather.isChecked = settings.notifyWeather
            notificationPrefs.setWeatherNotificationEnabled(settings.notifyWeather)
            Log.d(TAG, "Updated weather enabled from API: ${settings.notifyWeather}")
        } else {
            Log.d(TAG, "Keeping existing enabled state: $currentEnabled")
        }

        cbNotifyOutfitReminders.isChecked = settings.notifyOutfitReminders

        updateTimePickerState()
    }

    private fun saveSystemSettings() {
        lifecycleScope.launch {
            showLoading(true)
            try {
                Log.d(TAG, "=== SAVING SETTINGS ===")

                val selectedLanguageCode = languageCodes[spLanguage.selectedItemPosition]
                val currentLanguageCode = LanguageManager.getLanguage(requireContext())

                if (selectedLanguageCode != currentLanguageCode) {
                    languageChanged = true
                    LanguageManager.setLanguage(requireContext(), selectedLanguageCode)
                }

                val selectedTempUnit = temperatureCodes[spTemperatureUnit.selectedItemPosition]
                val selectedSensitivity = sensitivityCodes[spWeatherSensitivity.selectedItemPosition]
                val reminderTime = tvReminderTime.text.toString()
                val weatherEnabled = cbNotifyWeather.isChecked

                Log.d(TAG, "Weather enabled: $weatherEnabled")
                Log.d(TAG, "Reminder time: $reminderTime")

                // Validate time format
                if (reminderTime.isEmpty() || !reminderTime.contains(":")) {
                    Toast.makeText(context, "Invalid time format", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Save notification preferences FIRST
                notificationPrefs.setWeatherNotificationEnabled(weatherEnabled)
                notificationPrefs.setReminderTime(reminderTime)
                Log.d(TAG, "Preferences saved to SharedPreferences")

                // Verify saved values
                val verifyEnabled = notificationPrefs.isWeatherNotificationEnabled()
                val verifyTime = notificationPrefs.getReminderTime()
                Log.d(TAG, "Verified saved values - Enabled: $verifyEnabled, Time: $verifyTime")

                // Handle WorkManager scheduling
                if (weatherEnabled) {
                    Log.d(TAG, "Requesting notification permission...")
                    checkAndRequestNotificationPermission()

                    Log.d(TAG, "Scheduling weather notification for $reminderTime")
                    workManagerScheduler.scheduleWeatherNotification(reminderTime)

                    // Verify scheduling worked
                    val isScheduled = workManagerScheduler.isWorkScheduled()
                    Log.d(TAG, "Work scheduled verification: $isScheduled")

                    Toast.makeText(context, "Weather notifications scheduled for $reminderTime", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "Cancelling weather notifications")
                    workManagerScheduler.cancelWeatherNotification()
                    Toast.makeText(context, "Weather notifications disabled", Toast.LENGTH_SHORT).show()
                }

                // Update API settings
                val settings = SystemSettings(
                    language = selectedLanguageCode,
                    temperatureUnit = selectedTempUnit,
                    defaultReminderTime = reminderTime,
                    weatherSensitivity = selectedSensitivity,
                    notifyWeather = weatherEnabled,
                    notifyOutfitReminders = cbNotifyOutfitReminders.isChecked
                )

                Log.d(TAG, "Updating API settings...")
                apiService.updateSystemSettings(settings).onSuccess { message ->
                    Log.d(TAG, "API settings updated successfully")
                    if (languageChanged) {
                        showLanguageChangeDialog()
                    } else {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Failed to update API settings: ${error.message}", error)
                    Toast.makeText(context, getString(R.string.failed_update_settings, error.message), Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error saving settings", e)
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(requireContext(), "Notification permission granted", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Notification permission granted")
        } else {
            Toast.makeText(requireContext(), "Notification permission denied. You won't receive weather updates.", Toast.LENGTH_LONG).show()
            Log.w(TAG, "Notification permission denied")
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Notification Permission")
                        .setMessage("This app needs notification permission to send you daily weather updates.")
                        .setPositiveButton("OK") { _, _ ->
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            Log.d(TAG, "POST_NOTIFICATIONS not needed for API < 33")
        }
    }

    private fun showLanguageChangeDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.language))
            .setMessage(getString(R.string.language_changed))
            .setPositiveButton("OK") { _, _ ->
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
        spWeatherSensitivity.isEnabled = !isLoading
        cbNotifyWeather.isEnabled = !isLoading
        cbNotifyOutfitReminders.isEnabled = !isLoading

        // Keep time picker state based on checkbox
        if (!isLoading) {
            updateTimePickerState()
        }

        if (isLoading) {
            btnSave.text = getString(R.string.saving)
        } else {
            btnSave.text = getString(R.string.save_changes)
        }
    }
}