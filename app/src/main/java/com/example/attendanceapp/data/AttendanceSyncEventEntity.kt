package com.example.attendanceapp.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_sync_events",
    indices = [
        Index(value = ["userId", "synced", "createdAt"]),
        Index(value = ["clientEventId"], unique = true)
    ]
)
data class AttendanceSyncEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val eventType: String,
    val clientEventId: String,
    val requestJson: String,
    val photoPath: String? = null,
    val documentPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
    val retryCount: Int = 0,
    val lastError: String? = null
)
