package com.example.attendanceapp.data.network.dto

data class ClockInRequest(
    val clockInType: String,
    val latitude: Double,
    val longitude: Double,
    val officeAreaId: Long?,
    val reason: String?,
    val documentUrl: String?,
    val notes: String?,
    val clientEventId: String? = null,
    val clientTimestamp: String? = null,
    val queuedOffline: Boolean? = null
)
