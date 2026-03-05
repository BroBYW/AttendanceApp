package com.example.attendanceapp.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREF_NAME = "attendance_session"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_EMPLOYEE_ID = "employee_id"
        const val KEY_ROLE = "role"
        const val KEY_OFFICE_AREA_IDS = "office_area_ids"
    }

    fun saveAuthToken(token: String) {
        val editor = prefs.edit()
        editor.putString(KEY_AUTH_TOKEN, token)
        editor.apply()
    }

    fun fetchAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }
    
    fun saveRefreshToken(token: String) {
        val editor = prefs.edit()
        editor.putString(KEY_REFRESH_TOKEN, token)
        editor.apply()
    }

    fun fetchRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun saveUserDetails(id: Long, name: String, employeeId: String, role: String, officeAreaIds: List<Long>?) {
        val editor = prefs.edit()
        editor.putLong(KEY_USER_ID, id)
        editor.putString(KEY_USER_NAME, name)
        editor.putString(KEY_EMPLOYEE_ID, employeeId)
        editor.putString(KEY_ROLE, role)
        if (!officeAreaIds.isNullOrEmpty()) {
            editor.putString(KEY_OFFICE_AREA_IDS, officeAreaIds.joinToString(","))
        } else {
            editor.remove(KEY_OFFICE_AREA_IDS)
        }
        editor.apply()
    }

    /** Update only the assigned office area IDs (used for refresh) */
    fun saveOfficeAreaIds(officeAreaIds: List<Long>?) {
        val editor = prefs.edit()
        if (!officeAreaIds.isNullOrEmpty()) {
            editor.putString(KEY_OFFICE_AREA_IDS, officeAreaIds.joinToString(","))
        } else {
            editor.remove(KEY_OFFICE_AREA_IDS)
        }
        editor.apply()
    }

    fun getUserId(): Long {
        return prefs.getLong(KEY_USER_ID, -1)
    }
    
    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    /** Get all assigned office area IDs */
    fun getOfficeAreaIds(): List<Long> {
        val csv = prefs.getString(KEY_OFFICE_AREA_IDS, null) ?: return emptyList()
        return csv.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    /** Get the first assigned office area ID (backward compatible) */
    fun getOfficeAreaId(): Long? {
        return getOfficeAreaIds().firstOrNull()
    }

    fun clearSession() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }
}
