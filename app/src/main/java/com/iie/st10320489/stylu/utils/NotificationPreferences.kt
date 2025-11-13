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
        private const val KEY_LAST_NOTIFICATION_TIME = "last_notification_time"  // ✅ NEW

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

    /**
     * ✅ FIXED: When time changes, clear the last notification tracking
     */
    fun setReminderTime(time: String) {
        val oldTime = getReminderTime()
        prefs.edit().putString(KEY_REMINDER_TIME, time).apply()

        // ✅ If time changed, allow notification to be sent again today
        if (oldTime != time) {
            clearLastNotificationTracking()
        }
    }

    fun getLastNotificationDate(): String? {
        return prefs.getString(KEY_LAST_NOTIFICATION_DATE, null)
    }

    fun setLastNotificationDate(date: String) {
        prefs.edit().putString(KEY_LAST_NOTIFICATION_DATE, date).apply()
    }

    /**
     * ✅ NEW: Track what time we last sent notification
     */
    fun getLastNotificationTime(): String? {
        return prefs.getString(KEY_LAST_NOTIFICATION_TIME, null)
    }

    /**
     * ✅ NEW: Save both date AND time
     */
    fun setLastNotificationDateTime(date: String, time: String) {
        prefs.edit()
            .putString(KEY_LAST_NOTIFICATION_DATE, date)
            .putString(KEY_LAST_NOTIFICATION_TIME, time)
            .apply()
    }

    /**
     * ✅ FIXED: Check if notification was sent at the CURRENT scheduled time
     */
    fun wasNotificationSentForCurrentTime(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentScheduledTime = getReminderTime()

        val lastDate = getLastNotificationDate()
        val lastTime = getLastNotificationTime()

        // If not sent today, definitely should send
        if (lastDate != today) {
            return false
        }

        // If sent today but at a different time, should send again
        if (lastTime != currentScheduledTime) {
            return false
        }

        // Sent today at the current scheduled time - don't send again
        return true
    }

    /**
     * ✅ NEW: Mark notification sent at current scheduled time
     */
    fun markNotificationSentNow() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = getReminderTime()
        setLastNotificationDateTime(today, currentTime)
    }


    fun clearLastNotificationTracking() {
        prefs.edit()
            .remove(KEY_LAST_NOTIFICATION_DATE)
            .remove(KEY_LAST_NOTIFICATION_TIME)
            .apply()
    }

    // ⚠️ DEPRECATED: Use wasNotificationSentForCurrentTime() instead
    @Deprecated("Use wasNotificationSentForCurrentTime() instead")
    fun wasNotificationSentToday(): Boolean {
        val lastDate = getLastNotificationDate() ?: return false
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return lastDate == today
    }

    // ⚠️ DEPRECATED: Use markNotificationSentNow() instead
    @Deprecated("Use markNotificationSentNow() instead")
    fun markNotificationSentToday() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        setLastNotificationDate(today)
    }
}