package com.example.attendanceapp

/**
 * Data class representing a single attendance record.
 * Used for displaying history on the main screen.
 */
data class AttendanceRecord(
    val date: String,         // e.g. "24 Feb 2026"
    val time: String,         // e.g. "06:45 AM"
    val status: Status,       // ON_TIME, LATE, ABSENT
    val location: String      // e.g. "Lat: 3.1234, Lng: 101.6543"
) {
    enum class Status {
        ON_TIME, LATE, ABSENT
    }
}
