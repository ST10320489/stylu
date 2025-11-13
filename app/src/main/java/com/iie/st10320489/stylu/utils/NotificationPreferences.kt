package com.iie.st10320489.stylu.utils

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

class NotificationPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("stylu_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WEATHER_NOTIFICATIONS_ENABLED = "weather_notifications_enabled"
        private const val KEY_REMINDER_TIME = "reminder_time"
        private const val KEY_LAST_NOTIFICATION_DATE = "last_notification_date"

        const val DEFAULT_REMINDER_TIME = "07:00"
    }

    fun isWeatherNotificationEnabled(): Boolean {
        return prefs.getBoolean(KEY_WEATHER_NOTIFICATIONS_ENABLED, true)
    }

    fun setWeatherNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEATHER_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getReminderTime(): String {
        return prefs.getString(KEY_REMINDER_TIME, DEFAULT_REMINDER_TIME) ?: DEFAULT_REMINDER_TIME
    }

    fun setReminderTime(time: String) {
        prefs.edit().putString(KEY_REMINDER_TIME, time).apply()
    }

    fun getLastNotificationDate(): String? {
        return prefs.getString(KEY_LAST_NOTIFICATION_DATE, null)
    }

    fun setLastNotificationDate(date: String) {
        prefs.edit().putString(KEY_LAST_NOTIFICATION_DATE, date).apply()
    }

    fun wasNotificationSentToday(): Boolean {
        val lastDate = getLastNotificationDate() ?: return false
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return lastDate == today
    }

    fun markNotificationSentToday() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        setLastNotificationDate(today)
    }
}