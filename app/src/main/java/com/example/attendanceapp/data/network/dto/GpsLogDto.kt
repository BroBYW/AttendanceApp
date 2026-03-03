package com.example.attendanceapp.data.network.dto

data class GpsLogDto(
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val accuracy: Double?,
    val synced: Boolean? = false
)
