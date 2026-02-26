package com.example.attendanceapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.attendanceapp.data.AppPreferences
import com.example.attendanceapp.service.LocationTrackingService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if user was clocked in before the reboot
            if (AppPreferences.isTrackingActive(context)) {
                val serviceIntent = Intent(context, LocationTrackingService::class.java)
                serviceIntent.action = LocationTrackingService.ACTION_START
                
                // Start foreground service directly on boot
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
