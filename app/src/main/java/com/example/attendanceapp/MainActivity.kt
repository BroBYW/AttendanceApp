package com.example.attendanceapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val malaysiaTimeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur")

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)

        // User profile greeting
        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val username = intent.getStringExtra("username") ?: "User"
        tvGreeting.text = "Hi, $username 👋"

        // Logout button
        val btnLogout = findViewById<MaterialButton>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        val btnStartCheckIn = findViewById<MaterialButton>(R.id.btnStartCheckIn)
        btnStartCheckIn.setOnClickListener {
            startActivity(Intent(this, ScanQrActivity::class.java))
        }

        // Set up attendance history
        setupAttendanceHistory()

        // Start the live clock
        updateClock()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Logout") { _, _ ->
                // Navigate back to login
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun setupAttendanceHistory() {
        val rvHistory = findViewById<RecyclerView>(R.id.rvAttendanceHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.isNestedScrollingEnabled = false

        // Sample data (replace with real data from backend later)
        val sampleRecords = listOf(
            AttendanceRecord(
                date = "24 Feb 2026",
                time = "06:45 AM",
                status = AttendanceRecord.Status.ON_TIME,
                location = "Lat: 3.1390, Lng: 101.6869"
            ),
            AttendanceRecord(
                date = "23 Feb 2026",
                time = "06:52 AM",
                status = AttendanceRecord.Status.ON_TIME,
                location = "Lat: 3.1390, Lng: 101.6869"
            ),
            AttendanceRecord(
                date = "22 Feb 2026",
                time = "07:15 AM",
                status = AttendanceRecord.Status.LATE,
                location = "Lat: 3.1391, Lng: 101.6870"
            ),
            AttendanceRecord(
                date = "21 Feb 2026",
                time = "06:30 AM",
                status = AttendanceRecord.Status.ON_TIME,
                location = "Lat: 3.1389, Lng: 101.6868"
            ),
            AttendanceRecord(
                date = "20 Feb 2026",
                time = "—",
                status = AttendanceRecord.Status.ABSENT,
                location = "No location recorded"
            )
        )

        rvHistory.adapter = AttendanceHistoryAdapter(sampleRecords)
    }

    private fun updateClock() {
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
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
    }
}