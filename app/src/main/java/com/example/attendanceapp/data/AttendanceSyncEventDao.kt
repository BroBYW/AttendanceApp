package com.example.attendanceapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AttendanceSyncEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEvent(event: AttendanceSyncEventEntity): Long

    @Query("SELECT * FROM attendance_sync_events WHERE userId = :userId AND synced = 0 ORDER BY createdAt ASC, id ASC")
    fun getPendingEventsForUser(userId: Long): List<AttendanceSyncEventEntity>

    @Query("UPDATE attendance_sync_events SET synced = 1, lastError = NULL WHERE id = :id")
    fun markAsSynced(id: Long)

    @Query("UPDATE attendance_sync_events SET synced = 1, lastError = :error WHERE id = :id")
    fun markAsDropped(id: Long, error: String?)

    @Query("UPDATE attendance_sync_events SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    fun markAttemptFailed(id: Long, error: String?)

    @Query("DELETE FROM attendance_sync_events WHERE synced = 1 AND createdAt < :beforeMillis")
    fun deleteSyncedBefore(beforeMillis: Long)
}
