package com.example.attendanceapp.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.attendanceapp.data.AppDatabase
import com.example.attendanceapp.data.network.RetrofitClient
import com.example.attendanceapp.data.network.dto.GpsLogDto
import com.example.attendanceapp.utils.SessionManager

/**
 * WorkManager worker that syncs locally-stored GPS logs to the backend.
 * Scheduled with a CONNECTED network constraint so it automatically fires
 * when the device regains connectivity after being offline.
 */
class GpsSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "GpsSyncWorker"
        const val WORK_NAME_PERIODIC = "gps_sync_periodic"
        const val WORK_NAME_ONESHOT = "gps_sync_oneshot"
    }

    override suspend fun doWork(): Result {
        val sessionManager = SessionManager(applicationContext)
        val userId = sessionManager.getUserId()

        if (userId == -1L) {
            Log.w(TAG, "No user logged in, skipping sync")
            return Result.success()
        }

        val token = sessionManager.fetchAuthToken()
        if (token.isNullOrEmpty()) {
            Log.w(TAG, "No auth token, skipping sync")
            return Result.success()
        }

        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val unsyncedLogs = db.gpsLogDao().getUnsyncedLogsForUser(userId)

            if (unsyncedLogs.isEmpty()) {
                Log.d(TAG, "No unsynced logs to send")
                return Result.success()
            }

            Log.d(TAG, "Syncing ${unsyncedLogs.size} GPS logs...")

            val logDtos = unsyncedLogs.map { entity ->
                GpsLogDto(
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    timestamp = entity.timestamp.replace(" ", "T"),
                    accuracy = entity.accuracy?.toDouble(),
                    synced = true
                )
            }

            val apiService = RetrofitClient.getApiService(applicationContext)
            val response = apiService.submitGpsLogs(logDtos)

            if (response.isSuccessful && response.body()?.success == true) {
                val syncedIds = unsyncedLogs.map { it.id }
                db.gpsLogDao().markAsSynced(syncedIds)
                Log.d(TAG, "Successfully synced ${syncedIds.size} logs")

                // Auto-cleanup: Delete synced logs older than 30 days
                db.gpsLogDao().deleteOldSyncedLogs()
                Log.d(TAG, "Cleanup completed for synced logs older than 30 days")
                
                Result.success()
            } else {
                Log.w(TAG, "Server rejected sync: ${response.message()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed, will retry: ${e.message}")
            Result.retry()
        }
    }
}
