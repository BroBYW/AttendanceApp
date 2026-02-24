package com.example.attendanceapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Utility class for fetching the device's current GPS location.
 * Inspired by GPSLogger's approach: uses FusedLocationProvider with
 * high accuracy, falls back to last known location if needed.
 */
class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null

    /**
     * Callback interface for location results.
     */
    interface OnLocationResultListener {
        fun onLocationReceived(location: Location)
        fun onLocationError(message: String)
    }

    /**
     * Check if location permissions are granted.
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get the current location. First tries to get a fresh fix,
     * falls back to last known location if unavailable.
     */
    fun getCurrentLocation(listener: OnLocationResultListener) {
        if (!hasLocationPermission()) {
            listener.onLocationError("Location permission not granted")
            return
        }

        // Always request a fresh location fix for accuracy
        requestFreshLocation(listener)
    }

    /**
     * Request a fresh high-accuracy location fix.
     */
    private fun requestFreshLocation(listener: OnLocationResultListener) {
        if (!hasLocationPermission()) {
            listener.onLocationError("Location permission not granted")
            return
        }

        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1000L
            ).setMaxUpdates(1)
                .setWaitForAccurateLocation(true)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation
                    if (location != null) {
                        listener.onLocationReceived(location)
                    } else {
                        listener.onLocationError("Unable to determine location")
                    }
                    stopLocationUpdates()
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            listener.onLocationError("Location permission denied")
        }
    }

    /**
     * Stop receiving location updates. Call this when done.
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }
}
