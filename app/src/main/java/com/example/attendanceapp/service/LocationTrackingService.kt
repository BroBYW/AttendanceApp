package com.example.attendanceapp.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.attendanceapp.R
import com.example.attendanceapp.data.AppDatabase
import com.example.attendanceapp.data.AppPreferences
import com.example.attendanceapp.data.GpsLogEntity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import com.example.attendanceapp.data.network.RetrofitClient
import com.example.attendanceapp.data.network.dto.GpsLogDto
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class LocationTrackingService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    
    // AlarmManager components for exactly hourly wakes
    private lateinit var alarmManager: AlarmManager
    private lateinit var alarmIntent: PendingIntent
    
    // Auto clock out PendingIntent
    private lateinit var autoClockOutIntent: PendingIntent
    
    // Network connectivity callback for instant sync on reconnect
    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("LocationTrackingService", "Network available — triggering GPS sync")
            scope.launch {
                val userId = com.example.attendanceapp.utils.SessionManager(applicationContext).getUserId()
                if (userId != -1L) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    syncLogsWithServer(db, userId)
                }
            }
        }
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_FORCE_LOG = "ACTION_FORCE_LOG"
        const val ACTION_ALARM_WAKE = "ACTION_ALARM_WAKE"
        const val ACTION_AUTO_CLOCK_OUT = "ACTION_AUTO_CLOCK_OUT"
        const val EXTRA_REMARK = "EXTRA_REMARK"
        
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "LocationTrackerChannel"
        const val INTERVAL_MILLIS = 30 * 60 * 1000L // 30 minutes
    }

    override fun onCreate() {
        super.onCreate()
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_ALARM_WAKE
        }
        alarmIntent = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val autoIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_AUTO_CLOCK_OUT
        }
        autoClockOutIntent = PendingIntent.getService(
            this,
            3,
            autoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_START -> {
                    startTracking()
                }
                ACTION_STOP -> {
                    stopTracking()
                }
                ACTION_FORCE_LOG, ACTION_ALARM_WAKE -> {
                    val remark = intent.getStringExtra(EXTRA_REMARK)
                    logLocationAndUpdateNotification(remark)
                    if (intent.action == ACTION_ALARM_WAKE) {
                        scheduleNextAlarm()
                    }
                }
                ACTION_AUTO_CLOCK_OUT -> {
                    performAutoClockOut()
                }
            }
        }
        return START_STICKY
    }

    private fun startTracking() {
        AppPreferences.setTrackingActive(applicationContext, true)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting GPS Logger..."))
        
        // Take immediate log
        logLocationAndUpdateNotification(null)
        
        // Schedule periodic alarm for GPS capture
        scheduleNextAlarm()
        
        // Schedule auto clock out at 11:59 PM
        scheduleAutoClockOutAlarm()
        
        // Schedule periodic WorkManager sync with network constraint (backup)
        schedulePeriodicSync()
        
        // Register network callback for instant sync on reconnect
        registerNetworkCallback()
    }

    private fun stopTracking() {
        AppPreferences.setTrackingActive(applicationContext, false)
        alarmManager.cancel(alarmIntent)
        alarmManager.cancel(autoClockOutIntent)
        WorkManager.getInstance(applicationContext).cancelUniqueWork(GpsSyncWorker.WORK_NAME_PERIODIC)
        unregisterNetworkCallback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun scheduleNextAlarm() {
        // Schedule exact alarm for doze-resistant 1-hour ticks
        val triggerTime = SystemClock.elapsedRealtime() + INTERVAL_MILLIS
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, alarmIntent)
            } else {
                // Fallback to inexact alarm if permission is missing
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, alarmIntent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, alarmIntent)
        } else {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, alarmIntent)
        }
    }

    private fun scheduleAutoClockOutAlarm() {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        // If it's already past 23:59, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, autoClockOutIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, autoClockOutIntent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, autoClockOutIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, autoClockOutIntent)
        }
    }

    private fun logLocationAndUpdateNotification(remark: String? = null) {
        scope.launch {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return@launch
            }

            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
                val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                val locationTask = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                val location: Location? = Tasks.await(locationTask)

                if (location != null) {
                    val userId = com.example.attendanceapp.utils.SessionManager(applicationContext).getUserId()
                    if (userId == -1L) return@launch // Don't log if no user

                    // Update Database
                    val db = AppDatabase.getDatabase(applicationContext)
                    db.gpsLogDao().insertLog(
                        GpsLogEntity(
                            userId = userId,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                            accuracy = location.accuracy,
                            remark = remark,
                            synced = false
                        )
                    )
                    
                    // Attempt to sync logs with the server immediately
                    // If this fails (no network), the WorkManager will retry when online
                    syncLogsWithServer(db, userId)

                    // Update Notification
                    val msg = "Lat: ${location.latitude}, Lng: ${location.longitude}"
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, createNotification(msg))
                } else {
                    val userId = com.example.attendanceapp.utils.SessionManager(applicationContext).getUserId()
                    if (userId == -1L) return@launch // Don't log if no user

                    // Fallback to force database creation if emulator has no GPS fix yet
                    val db = AppDatabase.getDatabase(applicationContext)
                    db.gpsLogDao().insertLog(
                        GpsLogEntity(
                            userId = userId,
                            latitude = 0.0,
                            longitude = 0.0,
                            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                            accuracy = 0.0f,
                            remark = remark,
                            synced = false
                        )
                    )
                    
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, createNotification("Searching for GPS... (Logged 0.0)"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun syncLogsWithServer(db: AppDatabase, userId: Long) {
        syncMutex.withLock {
            try {
                // 1. Fetch all unsynced logs directly from the local database
                val unsyncedLogs = db.gpsLogDao().getUnsyncedLogsForUser(userId)
                
                if (unsyncedLogs.isEmpty()) return

                // 2. Map Entity to DTO
                val logDtos = unsyncedLogs.map { entity ->
                    GpsLogDto(
                        latitude = entity.latitude,
                        longitude = entity.longitude,
                        timestamp = entity.timestamp.replace(" ", "T"),
                        accuracy = entity.accuracy?.toDouble(),
                        remark = entity.remark,
                        synced = true // Signal that we are syncing these
                    )
                }

                // 3. Make Retrofit Network Call
                val apiService = RetrofitClient.getApiService(applicationContext)
                val response = apiService.submitGpsLogs(logDtos)

                // 4. Update the syncing status using the optimized DAO method
                if (response.isSuccessful && response.body()?.success == true) {
                    val syncedIds = unsyncedLogs.map { it.id }
                    db.gpsLogDao().markAsSynced(syncedIds)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Network failed — enqueue a one-shot sync that fires when back online
                enqueueOneshotSync()
            }
        }
    }

    private fun performAutoClockOut() {
        scope.launch {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return@launch
            }

            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
                val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                val locationTask = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                val location: Location? = Tasks.await(locationTask)

                val lat = location?.latitude ?: 0.0
                val lng = location?.longitude ?: 0.0
                val accuracy = location?.accuracy ?: 0.0f

                val apiService = RetrofitClient.getApiService(applicationContext)
                val requestObj = com.example.attendanceapp.data.network.dto.ClockOutRequest(
                    latitude = lat,
                    longitude = lng
                )
                val jsonString = com.google.gson.Gson().toJson(requestObj)
                val mediaType = "application/json".toMediaTypeOrNull()
                val dataPart = jsonString.toRequestBody(mediaType)

                val response = apiService.clockOut(dataPart, null)

                if (response.isSuccessful && response.body()?.success == true) {
                    val userId = com.example.attendanceapp.utils.SessionManager(applicationContext).getUserId()
                    if (userId != -1L) {
                        val db = AppDatabase.getDatabase(applicationContext)
                        db.gpsLogDao().insertLog(
                            GpsLogEntity(
                                userId = userId,
                                latitude = lat,
                                longitude = lng,
                                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                                accuracy = accuracy,
                                synced = false
                            )
                        )
                        syncLogsWithServer(db, userId)
                    }

                    // Stop tracking and clear preferences
                    AppPreferences.clearClockInTime(applicationContext)
                    withContext(Dispatchers.Main) {
                        stopTracking()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Let it stay on if network failed, or stop anyway? Usually we might stop and let them know next morning.
                // For now, let's wait until it works, or we can just stop it and lose their clock out event temporarily.
            }
        }
    }

    /**
     * Schedules a periodic sync worker that runs every hour when network is available.
     * This catches any logs that accumulated during offline periods.
     */
    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<GpsSyncWorker>(
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            GpsSyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    /**
     * Enqueues a one-shot sync that fires as soon as network connectivity returns.
     */
    private fun enqueueOneshotSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<GpsSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            GpsSyncWorker.WORK_NAME_ONESHOT,
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    /**
     * Registers a network callback that triggers instant sync when connectivity returns.
     */
    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            Log.d("LocationTrackingService", "Network callback registered")
        } catch (e: Exception) {
            Log.e("LocationTrackingService", "Failed to register network callback: ${e.message}")
        }
    }

    /**
     * Unregisters the network callback.
     */
    private fun unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d("LocationTrackingService", "Network callback unregistered")
        } catch (e: Exception) {
            Log.e("LocationTrackingService", "Failed to unregister network callback: ${e.message}")
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Tracker Active")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW 
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
