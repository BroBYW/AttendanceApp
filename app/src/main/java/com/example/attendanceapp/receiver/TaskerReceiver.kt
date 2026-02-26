package com.example.attendanceapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.attendanceapp.service.LocationTrackingService

class TaskerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.attendanceapp.TASKER_COMMAND") {
            val serviceIntent = Intent(context, LocationTrackingService::class.java)

            // Start, Stop, or Force Log
            if (intent.hasExtra("immediatestart")) {
                serviceIntent.action = LocationTrackingService.ACTION_START
                startServiceSafely(context, serviceIntent)
            } else if (intent.hasExtra("immediatestop")) {
                serviceIntent.action = LocationTrackingService.ACTION_STOP
                context.startService(serviceIntent)
            } else if (intent.hasExtra("force_log")) {
                serviceIntent.action = LocationTrackingService.ACTION_FORCE_LOG
                startServiceSafely(context, serviceIntent)
            }
        }
    }

    private fun startServiceSafely(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
