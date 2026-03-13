package com.example.attendanceapp.data.network.dto

data class ClockOutRequest(
    val latitude: Double,
    val longitude: Double,
    val clientEventId: String? = null,
    val clientTimestamp: String? = null,
    val queuedOffline: Boolean? = null
)
