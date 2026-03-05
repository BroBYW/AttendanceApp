package com.example.attendanceapp.data

import android.content.Context

object AppPreferences {
    private const val PREFS_NAME = "AttendanceAppPrefs"
    private const val KEY_TRACKING_ACTIVE = "is_tracking_active"
    private const val KEY_CLOCK_IN_TIME = "clock_in_time"

    fun setTrackingActive(context: Context, active: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TRACKING_ACTIVE, active).apply()
    }

    fun isTrackingActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TRACKING_ACTIVE, false)
    }

    fun setClockInTime(context: Context, timeMillis: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_CLOCK_IN_TIME, timeMillis).apply()
    }

    fun getClockInTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_CLOCK_IN_TIME, -1L)
    }

    fun clearClockInTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CLOCK_IN_TIME).apply()
    }
}
