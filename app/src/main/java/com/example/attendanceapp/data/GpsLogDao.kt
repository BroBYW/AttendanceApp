package com.example.attendanceapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GpsLogDao {
    @Insert
    fun insertLog(log: GpsLogEntity)

    @Query("SELECT * FROM gps_logs ORDER BY timestamp DESC")
    fun getAllLogs(): List<GpsLogEntity>

    @Query("SELECT * FROM gps_logs WHERE timestamp >= :startDate ORDER BY timestamp DESC")
    fun getLogsFromDate(startDate: String): List<GpsLogEntity>

    @Query("SELECT * FROM gps_logs WHERE synced = 0")
    fun getUnsyncedLogs(): List<GpsLogEntity>

    @Query("UPDATE gps_logs SET synced = 1 WHERE id IN (:ids)")
    fun markAsSynced(ids: List<Long>)
}
