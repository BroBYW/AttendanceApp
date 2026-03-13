package com.example.attendanceapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GpsLogDao {
    @Insert
    fun insertLog(log: GpsLogEntity)

    @Query("SELECT * FROM gps_logs WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllLogsForUser(userId: Long): List<GpsLogEntity>

    @Query("SELECT * FROM gps_logs WHERE userId = :userId AND timestamp >= :startDate ORDER BY timestamp DESC")
    fun getLogsFromDateForUser(userId: Long, startDate: String): List<GpsLogEntity>

    @Query("SELECT * FROM gps_logs WHERE synced = 0 AND userId = :userId")
    fun getUnsyncedLogsForUser(userId: Long): List<GpsLogEntity>

    @Query("SELECT * FROM gps_logs WHERE userId = :userId AND NOT (latitude = 0.0 AND longitude = 0.0) ORDER BY timestamp DESC LIMIT 1")
    fun getLatestValidLogForUser(userId: Long): GpsLogEntity?

    @Query("UPDATE gps_logs SET synced = 1 WHERE id IN (:ids)")
    fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM gps_logs")
    fun deleteAllLogs()

    @Query("DELETE FROM gps_logs WHERE synced = 1 AND timestamp < datetime('now', 'localtime', '-30 days')")
    fun deleteOldSyncedLogs()
}
