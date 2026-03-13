package com.example.attendanceapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.attendanceapp.utils.SessionManager
import com.example.attendanceapp.data.network.RetrofitClient
import com.example.attendanceapp.data.AppPreferences
import com.example.attendanceapp.service.LocationTrackingService
import com.example.attendanceapp.R
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.Response

class HomeFragment : Fragment() {

    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTimer: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val handler = Handler(Looper.getMainLooper())
    private val malaysiaTimeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur")

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvClock = view.findViewById(R.id.tvClock)
        tvDate = view.findViewById(R.id.tvDate)
        tvTimer = view.findViewById(R.id.tvTimer)

        // Time-based greeting
        val tvGreeting = view.findViewById<TextView>(R.id.tvGreeting)
        val username = activity?.intent?.getStringExtra("username") ?: "User"
        tvGreeting.text = getTimeBasedGreeting(username)

        // Logout button
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        val btnClockIn = view.findViewById<MaterialButton>(R.id.btnClockIn)
        val btnClockOut = view.findViewById<MaterialButton>(R.id.btnClockOut)

        btnClockIn.setOnClickListener {
            handleClockIn()
        }

        btnClockOut.setOnClickListener {
            handleClockOut()
        }

        // Pull-to-refresh
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeResources(R.color.secondary)
        swipeRefresh.setOnRefreshListener {
            refreshData()
        }

        updateClock()
    }

    private fun handleClockIn() {
        val calendar = Calendar.getInstance(malaysiaTimeZone)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        val timeFloat = hour + (minute / 60.0f)

        if (timeFloat < 6.0f) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Too Early")
                .setMessage("Clock in only starts at 6:00 AM. Please try again later.")
                .setPositiveButton("OK", null)
                .show()
        } else if (timeFloat <= 7.0f) {
            // Normal Clock In
            Toast.makeText(requireContext(), "Proceeding to normal clock in", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), CapturePhotoActivity::class.java).apply {
                putExtra("clock_type", "normal")
            }
            startActivity(intent)
        } else {
            // Late Clock In
            Toast.makeText(requireContext(), "Opening late submission form", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), LateSubmissionActivity::class.java))
        }
    }

    private fun handleClockOut() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clock Out")
            .setMessage("Are you sure you want to clock out? Location tracking will stop.")
            .setPositiveButton("Clock Out") { _, _ ->
                performClockOut()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performClockOut() {
        val locationHelper = LocationHelper(requireContext())
        if (!locationHelper.hasLocationPermission()) {
            Toast.makeText(context, "Location permission required to clock out", Toast.LENGTH_SHORT).show()
            return
        }

        swipeRefresh.isRefreshing = true

        locationHelper.getCurrentLocation(object : LocationHelper.OnLocationResultListener {
            override fun onLocationReceived(location: android.location.Location) {
                submitClockOutData(location.latitude, location.longitude, location.accuracy)
            }

            override fun onLocationError(message: String) {
                swipeRefresh.isRefreshing = false
                Toast.makeText(context, "Failed to get location: $message", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun submitClockOutData(lat: Double, lng: Double, accuracy: Float) {
        if (view == null || !isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            val safeContext = context ?: run {
                swipeRefresh.isRefreshing = false
                return@launch
            }
            val sessionManager = SessionManager(safeContext)
            val requestObj = com.example.attendanceapp.data.network.dto.ClockOutRequest(
                latitude = lat,
                longitude = lng,
                clientEventId = UUID.randomUUID().toString(),
                clientTimestamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                queuedOffline = false
            )
            try {
                val apiService = RetrofitClient.getApiService(safeContext)
                val jsonString = com.google.gson.Gson().toJson(requestObj)
                val mediaType = "application/json".toMediaTypeOrNull()
                val dataPart = jsonString.toRequestBody(mediaType)
                
                val response = apiService.clockOut(dataPart, null)

                if (response.isSuccessful && response.body()?.success == true) {
                    if (sessionManager.getUserId() != -1L) {
                        com.example.attendanceapp.service.AttendanceOfflineQueue.recordSyncedClockOut(
                            context = safeContext,
                            userId = sessionManager.getUserId(),
                            request = requestObj.copy(queuedOffline = false)
                        )
                    }
                    finalizeClockOutFlow(lat, lng, accuracy, "Clocked Out Successfully")
                } else {
                    if (isRetriableHttpFailure(response.code()) && sessionManager.getUserId() != -1L) {
                        val queued = com.example.attendanceapp.service.AttendanceOfflineQueue.enqueueClockOut(
                            context = safeContext,
                            userId = sessionManager.getUserId(),
                            request = requestObj.copy(queuedOffline = true)
                        )
                        if (queued != -1L) {
                            finalizeClockOutFlow(
                                lat,
                                lng,
                                accuracy,
                                "Clock-out saved offline. It will sync when network is back."
                            )
                        } else {
                            Toast.makeText(safeContext, "Clock out failed", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(safeContext, extractErrorMessage(response), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (sessionManager.getUserId() != -1L) {
                    val queued = com.example.attendanceapp.service.AttendanceOfflineQueue.enqueueClockOut(
                        context = safeContext,
                        userId = sessionManager.getUserId(),
                        request = requestObj.copy(queuedOffline = true)
                    )
                    if (queued != -1L) {
                        finalizeClockOutFlow(
                            lat,
                            lng,
                            accuracy,
                            "Offline mode: clock-out saved and will auto-sync."
                        )
                    } else {
                        Toast.makeText(safeContext, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(safeContext, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private suspend fun finalizeClockOutFlow(lat: Double, lng: Double, accuracy: Float, message: String) {
        val safeContext = context ?: return
        withContext(Dispatchers.IO) {
            val userId = SessionManager(safeContext).getUserId()
            if (userId != -1L) {
                val db = com.example.attendanceapp.data.AppDatabase.getDatabase(safeContext)
                db.gpsLogDao().insertLog(
                    com.example.attendanceapp.data.GpsLogEntity(
                        userId = userId,
                        latitude = lat,
                        longitude = lng,
                        timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                        accuracy = accuracy,
                        synced = false
                    )
                )

                try {
                    val apiService = RetrofitClient.getApiService(safeContext)
                    val unsyncedLogs = db.gpsLogDao().getUnsyncedLogsForUser(userId)
                    if (unsyncedLogs.isNotEmpty()) {
                        val logDtos = unsyncedLogs.map { entity ->
                            com.example.attendanceapp.data.network.dto.GpsLogDto(
                                latitude = entity.latitude,
                                longitude = entity.longitude,
                                timestamp = entity.timestamp.replace(" ", "T"),
                                accuracy = entity.accuracy?.toDouble(),
                                remark = entity.remark,
                                synced = true
                            )
                        }
                        val syncResponse = apiService.submitGpsLogs(logDtos)
                        if (syncResponse.isSuccessful && syncResponse.body()?.success == true) {
                            db.gpsLogDao().markAsSynced(unsyncedLogs.map { it.id })
                        }
                    }
                } catch (_: Exception) {
                    // GPS sync will retry later.
                }
            }
        }

        if (!isAdded) return
        val liveContext = context ?: return
        val serviceIntent = Intent(liveContext, LocationTrackingService::class.java)
        serviceIntent.action = LocationTrackingService.ACTION_STOP
        AppPreferences.clearClockInTime(liveContext)
        liveContext.startService(serviceIntent)
        AppPreferences.setTrackingActive(liveContext, false)

        Toast.makeText(liveContext, message, Toast.LENGTH_LONG).show()
        checkTrackingState()
        refreshData()
    }

    private fun extractErrorMessage(response: Response<*>): String {
        return try {
            val raw = response.errorBody()?.string()
            if (raw.isNullOrBlank()) {
                "Clock out failed: ${response.message()}"
            } else {
                org.json.JSONObject(raw).optString("message", "Clock out failed")
            }
        } catch (_: Exception) {
            "Clock out failed: ${response.message()}"
        }
    }

    private fun isRetriableHttpFailure(code: Int): Boolean {
        return code >= 500 || code == 408 || code == 429
    }

    private fun checkTrackingState() {
        val btnClockIn = view?.findViewById<MaterialButton>(R.id.btnClockIn)
        val btnClockOut = view?.findViewById<MaterialButton>(R.id.btnClockOut)
        
        if (btnClockIn != null && btnClockOut != null) {
            val isTracking = AppPreferences.isTrackingActive(requireContext())
            
            if (isTracking) {
                // If clock-in time wasn't saved yet, set it now
                if (AppPreferences.getClockInTime(requireContext()) == -1L) {
                    AppPreferences.setClockInTime(requireContext(), System.currentTimeMillis())
                }

                // Clocked In currently
                btnClockIn.visibility = View.GONE
                
                btnClockOut.visibility = View.VISIBLE
                btnClockOut.isEnabled = true
                btnClockOut.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.error))
                btnClockOut.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_error))

                tvTimer.visibility = View.VISIBLE
            } else {
                // Not Clocked In
                btnClockIn.visibility = View.VISIBLE
                btnClockIn.isEnabled = true
                btnClockIn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.success))
                btnClockIn.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
                
                btnClockOut.visibility = View.GONE
                tvTimer.visibility = View.GONE
            }
        }
    }

    private fun getTimeBasedGreeting(username: String): String {
        val calendar = Calendar.getInstance(malaysiaTimeZone)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 5..11 -> "Good Morning, $username ☀️"
            hour in 12..17 -> "Good Afternoon, $username 🌤️"
            else -> "Good Evening, $username 🌙"
        }
    }

    private fun refreshData() {
        if (view == null) return
        swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService(requireContext())
                
                // Fetch profile to update assigned areas concurrently with attendance
                val profileDeferred = async { apiService.getMyProfile() }
                val attendanceDeferred = async { apiService.getMyAttendance() }
                
                val profileRes = profileDeferred.await()
                val attendanceRes = attendanceDeferred.await()

                var isSuccess = false

                if (profileRes.isSuccessful && profileRes.body()?.success == true) {
                    profileRes.body()?.data?.let { user ->
                        SessionManager(requireContext()).saveOfficeAreaIds(user.assignedOfficeAreaIds)
                    }
                    isSuccess = true
                }

                if (attendanceRes.isSuccessful && attendanceRes.body()?.success == true) {
                    isSuccess = true
                }

                if (isSuccess) {
                    Toast.makeText(context, "Data refreshed", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Refresh failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showLogoutDialog() {
        val safeContext = context ?: return
        MaterialAlertDialogBuilder(safeContext)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Logout") { _, _ ->
                // Clear session data
                com.example.attendanceapp.utils.SessionManager(safeContext).clearSession()

                // Explicitly halt the background GPS tracking service upon logout to prevent zombie logs
                val serviceIntent = Intent(safeContext, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_STOP
                }
                safeContext.startService(serviceIntent)
                AppPreferences.setTrackingActive(safeContext, false)
                AppPreferences.clearClockInTime(safeContext)

                // Launch LoginActivity and clear the stack safely
                val intent = Intent(safeContext, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                activity?.finish()
            }
            .show()
    }



    private fun updateClock() {
        if (!isAdded) return
        val now = Date()
        val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH)
        timeFormat.timeZone = malaysiaTimeZone
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH)
        dateFormat.timeZone = malaysiaTimeZone
        tvClock.text = timeFormat.format(now)
        tvDate.text = "${dateFormat.format(now)} • MYT"

        // Update working duration timer if tracking is active
        if (AppPreferences.isTrackingActive(requireContext())) {
            val clockInTime = AppPreferences.getClockInTime(requireContext())
            if (clockInTime != -1L) {
                val durationMillis = System.currentTimeMillis() - clockInTime
                val seconds = (durationMillis / 1000) % 60
                val minutes = (durationMillis / (1000 * 60)) % 60
                val hours = (durationMillis / (1000 * 60 * 60))
                tvTimer.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockRunnable)
        checkTrackingState()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
    }
}
