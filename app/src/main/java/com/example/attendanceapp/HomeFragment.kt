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
import androidx.fragment.app.Fragment

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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

class HomeFragment : Fragment() {

    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
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
                // Stop the location tracking service
                val serviceIntent = Intent(requireContext(), LocationTrackingService::class.java)
                serviceIntent.action = LocationTrackingService.ACTION_STOP
                requireContext().startService(serviceIntent)
                
                // Immediately update local preference to avoid UI lag
                AppPreferences.setTrackingActive(requireContext(), false)

                Toast.makeText(requireContext(), "Clocked Out Successfully", Toast.LENGTH_SHORT).show()
                // Update UI state
                checkTrackingState()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkTrackingState() {
        val btnClockIn = view?.findViewById<MaterialButton>(R.id.btnClockIn)
        val btnClockOut = view?.findViewById<MaterialButton>(R.id.btnClockOut)
        
        if (btnClockIn != null && btnClockOut != null) {
            val isTracking = AppPreferences.isTrackingActive(requireContext())
            
            if (isTracking) {
                // Clocked In currently
                btnClockIn.isEnabled = false
                btnClockIn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface))
                btnClockIn.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
                
                btnClockOut.isEnabled = true
                btnClockOut.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.error))
                btnClockOut.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_error))
            } else {
                // Not Clocked In
                btnClockIn.isEnabled = true
                btnClockIn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
                btnClockIn.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
                
                btnClockOut.isEnabled = false
                btnClockOut.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface))
                btnClockOut.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
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
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isAdded) return@postDelayed
            swipeRefresh.isRefreshing = false
        }, 1500)
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Logout") { _, _ ->
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
