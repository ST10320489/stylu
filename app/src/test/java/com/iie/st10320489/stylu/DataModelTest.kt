package com.iie.st10320489.stylu

import com.iie.st10320489.stylu.network.UserProfile
import com.iie.st10320489.stylu.network.SystemSettings
import org.junit.Test
import org.junit.Assert.*


class DataModelTest {

    @Test
    fun testUserProfileCreation() {
        val profile = UserProfile(
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "1234567890",
            email = "john@example.com",
            language = "en",
            temperatureUnit = "C",
            defaultReminderTime = "07:00",
            weatherSensitivity = "normal",
            notifyWeather = true,
            notifyOutfitReminders = true
        )

        assertEquals("John", profile.firstName)
        assertEquals("Doe", profile.lastName)
        assertEquals("john@example.com", profile.email)
        assertTrue("Weather notifications should be enabled", profile.notifyWeather == true)
    }

    @Test
    fun testSystemSettingsDefaults() {
        val settings = SystemSettings(
            language = "en",
            temperatureUnit = "C",
            defaultReminderTime = "07:00",
            weatherSensitivity = "normal",
            notifyWeather = true,
            notifyOutfitReminders = true
        )

        assertEquals("Default language should be English", "en", settings.language)
        assertEquals("Default temperature unit should be Celsius", "C", settings.temperatureUnit)
        assertTrue("Notifications should be enabled by default", settings.notifyWeather)
    }

    @Test
    fun testSystemSettingsUpdate() {
        val settings = SystemSettings(
            language = "en",
            temperatureUnit = "C",
            defaultReminderTime = "07:00",
            weatherSensitivity = "normal",
            notifyWeather = true,
            notifyOutfitReminders = true
        )

        // Simulate update
        val updatedSettings = settings.copy(
            temperatureUnit = "F",
            notifyWeather = false
        )

        assertEquals("Temperature unit should be updated to Fahrenheit", "F", updatedSettings.temperatureUnit)
        assertFalse("Weather notifications should be disabled", updatedSettings.notifyWeather)
    }
}
