package com.example.attendanceapp.data.network.dto

data class UserResponse(
    val id: Long,
    val username: String,
    val employeeId: String,
    val name: String,
    val department: String?,
    val role: String,
    val status: String,
    val createdAt: String,
    val assignedOfficeAreaIds: List<Long>?
)
