package com.example.attendanceapp.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.attendanceapp.R
import com.example.attendanceapp.data.AppDatabase
import com.example.attendanceapp.data.AppPreferences
import com.example.attendanceapp.data.GpsLogEntity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.*

class LocationTrackingService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    // AlarmManager components for exactly hourly wakes
    private lateinit var alarmManager: AlarmManager
    private lateinit var alarmIntent: PendingIntent

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_FORCE_LOG = "ACTION_FORCE_LOG"
        const val ACTION_ALARM_WAKE = "ACTION_ALARM_WAKE"
        
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "LocationTrackerChannel"
        const val INTERVAL_MILLIS = 60 * 60 * 1000L // 1 hour
    }

    override fun onCreate() {
        super.onCreate()
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_ALARM_WAKE
        }
        alarmIntent = PendingIntent.getService(
            this,
            0,
            intent,
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
                    logLocationAndUpdateNotification()
                    if (intent.action == ACTION_ALARM_WAKE) {
                        scheduleNextAlarm()
                    }
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
        logLocationAndUpdateNotification()
        
        // Schedule periodic
        scheduleNextAlarm()
    }

    private fun stopTracking() {
        AppPreferences.setTrackingActive(applicationContext, false)
        alarmManager.cancel(alarmIntent)
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

    private fun logLocationAndUpdateNotification() {
        scope.launch {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return@launch
            }

            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
                val locationTask = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                val location: Location? = Tasks.await(locationTask)

                if (location != null) {
                    // Update Database
                    val db = AppDatabase.getDatabase(applicationContext)
                    db.gpsLogDao().insertLog(
                        GpsLogEntity(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                            accuracy = location.accuracy
                        )
                    )
                    
                    // Update Notification
                    val msg = "Lat: ${location.latitude}, Lng: ${location.longitude}"
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, createNotification(msg))
                } else {
                    // Fallback to force database creation if emulator has no GPS fix yet
                    val db = AppDatabase.getDatabase(applicationContext)
                    db.gpsLogDao().insertLog(
                        GpsLogEntity(
                            latitude = 0.0,
                            longitude = 0.0,
                            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                            accuracy = 0.0f
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

    private fun createNotification(content: String): Notification {
        // Action Intents
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val forceIntent = Intent(this, LocationTrackingService::class.java).apply { action = ACTION_FORCE_LOG }
        val forcePendingIntent = PendingIntent.getService(this, 2, forceIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Tracker Active")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(R.mipmap.ic_launcher, "Stop", stopPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Force Log", forcePendingIntent)
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
