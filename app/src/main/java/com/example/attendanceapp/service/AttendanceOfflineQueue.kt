package com.example.attendanceapp.service

import android.content.Context
import android.net.Uri
import com.example.attendanceapp.data.AppDatabase
import com.example.attendanceapp.data.AttendanceSyncEventEntity
import com.example.attendanceapp.data.network.dto.ClockInRequest
import com.example.attendanceapp.data.network.dto.ClockOutRequest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.util.UUID

object AttendanceOfflineQueue {
    const val EVENT_TYPE_CLOCK_IN = "CLOCK_IN"
    const val EVENT_TYPE_CLOCK_OUT = "CLOCK_OUT"

    private val gson = Gson()

    suspend fun enqueueClockIn(
        context: Context,
        userId: Long,
        request: ClockInRequest,
        photoUri: String?,
        documentPath: String?
    ): Long = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val persistedPhotoPath = persistPhoto(context, photoUri)
        val persistedDocumentPath = persistDocument(context, documentPath)

        val event = AttendanceSyncEventEntity(
            userId = userId,
            eventType = EVENT_TYPE_CLOCK_IN,
            clientEventId = request.clientEventId ?: UUID.randomUUID().toString(),
            requestJson = gson.toJson(request.copy(queuedOffline = true)),
            photoPath = persistedPhotoPath,
            documentPath = persistedDocumentPath
        )

        val rowId = db.attendanceSyncEventDao().insertEvent(event)
        AttendanceSyncScheduler.schedulePeriodic(context)
        AttendanceSyncScheduler.enqueueOneShot(context)
        rowId
    }

    suspend fun recordSyncedClockIn(
        context: Context,
        userId: Long,
        request: ClockInRequest
    ): Long = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val event = AttendanceSyncEventEntity(
            userId = userId,
            eventType = EVENT_TYPE_CLOCK_IN,
            clientEventId = request.clientEventId ?: UUID.randomUUID().toString(),
            requestJson = gson.toJson(request.copy(queuedOffline = false)),
            synced = true
        )
        db.attendanceSyncEventDao().insertEvent(event)
    }

    suspend fun enqueueClockOut(
        context: Context,
        userId: Long,
        request: ClockOutRequest
    ): Long = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val event = AttendanceSyncEventEntity(
            userId = userId,
            eventType = EVENT_TYPE_CLOCK_OUT,
            clientEventId = request.clientEventId ?: UUID.randomUUID().toString(),
            requestJson = gson.toJson(request.copy(queuedOffline = true))
        )

        val rowId = db.attendanceSyncEventDao().insertEvent(event)
        AttendanceSyncScheduler.schedulePeriodic(context)
        AttendanceSyncScheduler.enqueueOneShot(context)
        rowId
    }

    suspend fun recordSyncedClockOut(
        context: Context,
        userId: Long,
        request: ClockOutRequest
    ): Long = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val event = AttendanceSyncEventEntity(
            userId = userId,
            eventType = EVENT_TYPE_CLOCK_OUT,
            clientEventId = request.clientEventId ?: UUID.randomUUID().toString(),
            requestJson = gson.toJson(request.copy(queuedOffline = false)),
            synced = true
        )
        db.attendanceSyncEventDao().insertEvent(event)
    }

    fun cleanupPersistedFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    private fun persistPhoto(context: Context, uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return copyUriToInternalStorage(context, Uri.parse(uriString), "clockin_photo")
    }

    private fun persistDocument(context: Context, sourcePath: String?): String? {
        if (sourcePath.isNullOrBlank()) return null
        return runCatching {
            val source = File(sourcePath)
            if (!source.exists()) return@runCatching null
            val extension = source.extension.takeIf { it.isNotBlank() } ?: "bin"
            val target = createOfflineFile(context, "late_doc", extension)
            source.inputStream().use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            target.absolutePath
        }.getOrNull()
    }

    private fun copyUriToInternalStorage(context: Context, uri: Uri, prefix: String): String? {
        return runCatching<String?> {
            val extension = when (uri.scheme) {
                "content" -> {
                    context.contentResolver.getType(uri)
                        ?.substringAfterLast('/')
                        ?.takeIf { it.isNotBlank() }
                        ?: "jpg"
                }
                else -> {
                    val fromPath = uri.path?.substringAfterLast('.', "")
                    fromPath?.takeIf { it.isNotBlank() } ?: "jpg"
                }
            }

            val target = createOfflineFile(context, prefix, extension)
            val inputStream = context.contentResolver.openInputStream(uri) ?: run {
                val rawPath = uri.path
                if (rawPath.isNullOrBlank()) {
                    return@runCatching null
                }
                val sourceFile = File(rawPath)
                if (!sourceFile.exists()) {
                    return@runCatching null
                }
                FileInputStream(sourceFile)
            }

            inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            target.absolutePath
        }.getOrNull()
    }

    private fun createOfflineFile(context: Context, prefix: String, extension: String): File {
        val directory = File(context.filesDir, "offline_attendance_media").apply {
            if (!exists()) mkdirs()
        }
        return File.createTempFile("${prefix}_", ".${extension}", directory)
    }
}
