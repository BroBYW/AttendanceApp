package com.example.attendanceapp.data

import android.content.Context

object AppPreferences {
    private const val PREFS_NAME = "AttendanceAppPrefs"
    private const val KEY_TRACKING_ACTIVE = "is_tracking_active"

    fun setTrackingActive(context: Context, active: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TRACKING_ACTIVE, active).apply()
    }

    fun isTrackingActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TRACKING_ACTIVE, false)
    }
}
