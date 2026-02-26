package com.example.attendanceapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gps_logs")
data class GpsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val accuracy: Float?,
    val synced: Boolean = false
)
