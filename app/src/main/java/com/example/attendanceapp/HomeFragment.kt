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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
    private lateinit var rvHistory: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
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
        rvHistory = view.findViewById(R.id.rvAttendanceHistory)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)

        // Time-based greeting
        val tvGreeting = view.findViewById<TextView>(R.id.tvGreeting)
        val username = activity?.intent?.getStringExtra("username") ?: "User"
        tvGreeting.text = getTimeBasedGreeting(username)

        // Logout button
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        val btnStartCheckIn = view.findViewById<MaterialButton>(R.id.btnStartCheckIn)
        btnStartCheckIn.setOnClickListener {
            startActivity(Intent(requireContext(), ScanQrActivity::class.java))
        }

        // Pull-to-refresh
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeResources(R.color.secondary)
        swipeRefresh.setOnRefreshListener {
            refreshData()
        }

        setupAttendanceHistory()
        updateClock()
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
            setupAttendanceHistory()
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

    private fun setupAttendanceHistory() {
        if (!isAdded) return
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.isNestedScrollingEnabled = false

        val sampleRecords = listOf(
            AttendanceRecord("24 Feb 2026", "06:45 AM", AttendanceRecord.Status.ON_TIME, "Lat: 3.1390, Lng: 101.6869"),
            AttendanceRecord("23 Feb 2026", "06:52 AM", AttendanceRecord.Status.ON_TIME, "Lat: 3.1390, Lng: 101.6869"),
            AttendanceRecord("22 Feb 2026", "07:15 AM", AttendanceRecord.Status.LATE, "Lat: 3.1391, Lng: 101.6870"),
            AttendanceRecord("21 Feb 2026", "06:30 AM", AttendanceRecord.Status.ON_TIME, "Lat: 3.1389, Lng: 101.6868"),
            AttendanceRecord("20 Feb 2026", "—", AttendanceRecord.Status.ABSENT, "No location recorded")
        )

        if (sampleRecords.isEmpty()) {
            rvHistory.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            rvHistory.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
            rvHistory.adapter = AttendanceHistoryAdapter(sampleRecords)
        }
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
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
    }
}
