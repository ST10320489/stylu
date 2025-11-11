package com.iie.st10320489.stylu.utils

import android.content.Context
import com.iie.st10320489.stylu.network.SystemSettings
import org.json.JSONObject

data class CachedProfile(
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val phoneNumber: String?
)

object ProfileCacheManager {
    private const val PREF_NAME = "profile_cache"
    private const val KEY_PROFILE = "cached_profile"
    private const val KEY_SYSTEM_SETTINGS = "cached_system_settings"

    fun saveProfile(context: Context, profile: CachedProfile) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("firstName", profile.firstName)
            put("lastName", profile.lastName)
            put("email", profile.email)
            put("phoneNumber", profile.phoneNumber)
        }
        prefs.edit().putString(KEY_PROFILE, json.toString()).apply()
    }

    fun getProfile(context: Context): CachedProfile? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PROFILE, null) ?: return null
        val json = JSONObject(jsonStr)
        return CachedProfile(
            json.optString("firstName", null),
            json.optString("lastName", null),
            json.optString("email", null),
            json.optString("phoneNumber", null)
        )
    }

    fun saveSystemSettings(context: Context, settings: SystemSettings) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("language", settings.language)
            put("temperatureUnit", settings.temperatureUnit)
            put("defaultReminderTime", settings.defaultReminderTime)
            put("weatherSensitivity", settings.weatherSensitivity)
            put("notifyWeather", settings.notifyWeather)
            put("notifyOutfitReminders", settings.notifyOutfitReminders)
        }
        prefs.edit().putString(KEY_SYSTEM_SETTINGS, json.toString()).apply()
    }

    fun getSystemSettings(context: Context): SystemSettings? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SYSTEM_SETTINGS, null) ?: return null
        val json = JSONObject(jsonStr)
        return SystemSettings(
            language = json.optString("language", "en"),
            temperatureUnit = json.optString("temperatureUnit", "C"),
            defaultReminderTime = json.optString("defaultReminderTime", "08:00"),
            weatherSensitivity = json.optString("weatherSensitivity", "normal"),
            notifyWeather = json.optBoolean("notifyWeather", true),
            notifyOutfitReminders = json.optBoolean("notifyOutfitReminders", true)
        )
    }
}
