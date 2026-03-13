package com.example.attendanceapp.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.attendanceapp.data.AppDatabase
import com.example.attendanceapp.data.AttendanceSyncEventEntity
import com.example.attendanceapp.data.network.RetrofitClient
import com.example.attendanceapp.data.network.dto.ClockInRequest
import com.example.attendanceapp.data.network.dto.ClockOutRequest
import com.example.attendanceapp.utils.SessionManager
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class AttendanceSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "AttendanceSyncWorker"
        const val WORK_NAME_PERIODIC = "attendance_sync_periodic"
        const val WORK_NAME_ONESHOT = "attendance_sync_oneshot"
        private val RETAIN_SYNCED_MILLIS = TimeUnit.DAYS.toMillis(30)
    }

    private val gson = Gson()

    override suspend fun doWork(): Result {
        val sessionManager = SessionManager(applicationContext)
        val userId = sessionManager.getUserId()
        val token = sessionManager.fetchAuthToken()

        if (userId == -1L || token.isNullOrBlank()) {
            Log.w(TAG, "No logged-in user/token. Skip attendance sync.")
            return Result.success()
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val pendingEvents = db.attendanceSyncEventDao().getPendingEventsForUser(userId)
        db.attendanceSyncEventDao().deleteSyncedBefore(System.currentTimeMillis() - RETAIN_SYNCED_MILLIS)
        if (pendingEvents.isEmpty()) {
            return Result.success()
        }

        val apiService = RetrofitClient.getApiService(applicationContext)

        for (event in pendingEvents) {
            when (val syncResult = syncSingleEvent(event, apiService)) {
                is EventSyncResult.Success -> {
                    db.attendanceSyncEventDao().markAsSynced(event.id)
                    AttendanceOfflineQueue.cleanupPersistedFile(event.photoPath)
                    AttendanceOfflineQueue.cleanupPersistedFile(event.documentPath)
                }

                is EventSyncResult.Drop -> {
                    db.attendanceSyncEventDao().markAsDropped(event.id, syncResult.reason)
                    AttendanceOfflineQueue.cleanupPersistedFile(event.photoPath)
                    AttendanceOfflineQueue.cleanupPersistedFile(event.documentPath)
                }

                is EventSyncResult.Retry -> {
                    db.attendanceSyncEventDao().markAttemptFailed(event.id, syncResult.reason)
                    Log.w(TAG, "Retrying later for event ${event.id}: ${syncResult.reason}")
                    return Result.retry()
                }
            }
        }

        return Result.success()
    }

    private suspend fun syncSingleEvent(
        event: AttendanceSyncEventEntity,
        apiService: com.example.attendanceapp.data.network.ApiService
    ): EventSyncResult {
        return try {
            when (event.eventType) {
                AttendanceOfflineQueue.EVENT_TYPE_CLOCK_IN -> syncClockIn(event, apiService)
                AttendanceOfflineQueue.EVENT_TYPE_CLOCK_OUT -> syncClockOut(event, apiService)
                else -> EventSyncResult.Drop("Unknown event type: ${event.eventType}")
            }
        } catch (io: IOException) {
            EventSyncResult.Retry(io.message ?: "Network error")
        } catch (e: Exception) {
            EventSyncResult.Retry(e.message ?: "Unexpected sync error")
        }
    }

    private suspend fun syncClockIn(
        event: AttendanceSyncEventEntity,
        apiService: com.example.attendanceapp.data.network.ApiService
    ): EventSyncResult {
        val requestObj = gson.fromJson(event.requestJson, ClockInRequest::class.java)
        val normalizedRequest = requestObj.copy(
            clientTimestamp = normalizeTimestamp(requestObj.clientTimestamp)
        )
        val dataPart = gson.toJson(normalizedRequest)
            .toRequestBody("application/json".toMediaTypeOrNull())

        val selfiePart = buildFilePart("selfie", event.photoPath, "image/jpeg")
        val docPart = buildFilePart("document", event.documentPath, "*/*")

        val response = apiService.clockIn(dataPart, selfiePart, docPart)
        return classifyResponse(response)
    }

    private suspend fun syncClockOut(
        event: AttendanceSyncEventEntity,
        apiService: com.example.attendanceapp.data.network.ApiService
    ): EventSyncResult {
        val requestObj = gson.fromJson(event.requestJson, ClockOutRequest::class.java)
        val normalizedRequest = requestObj.copy(
            clientTimestamp = normalizeTimestamp(requestObj.clientTimestamp)
        )
        val dataPart = gson.toJson(normalizedRequest)
            .toRequestBody("application/json".toMediaTypeOrNull())

        val response = apiService.clockOut(dataPart, null)
        return classifyResponse(response)
    }

    private fun buildFilePart(partName: String, path: String?, contentType: String): MultipartBody.Part? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        val requestBody = file.asRequestBody(contentType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, file.name, requestBody)
    }

    private fun classifyResponse(response: Response<*>): EventSyncResult {
        if (response.isSuccessful) {
            return EventSyncResult.Success
        }

        val code = response.code()
        val errorMessage = parseErrorMessage(response) ?: response.message()

        if (code == 401 || code == 403) {
            return EventSyncResult.Retry(errorMessage)
        }

        // Permanent request errors should not block the queue forever.
        if (code in 400..499 && code != 408 && code != 429) {
            return EventSyncResult.Drop(errorMessage)
        }

        return EventSyncResult.Retry(errorMessage)
    }

    private fun parseErrorMessage(response: Response<*>): String? {
        return try {
            val raw = response.errorBody()?.string() ?: return null
            JSONObject(raw).optString("message", raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeTimestamp(raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        return runCatching {
            LocalDateTime.parse(raw)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }.getOrDefault(raw.substringBefore('.'))
    }

    private sealed interface EventSyncResult {
        object Success : EventSyncResult
        data class Retry(val reason: String) : EventSyncResult
        data class Drop(val reason: String) : EventSyncResult
    }
}
