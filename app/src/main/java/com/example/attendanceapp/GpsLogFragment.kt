package com.example.attendanceapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendanceapp.data.AppDatabase
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.example.attendanceapp.data.AppPreferences
import com.example.attendanceapp.service.LocationTrackingService
import com.google.android.material.floatingactionbutton.FloatingActionButton

class GpsLogFragment : Fragment() {

    private lateinit var rvGpsLogs: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var adapter: GpsLogAdapter
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var fabAddLog: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gps_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipeRefreshGpsLog)
        rvGpsLogs = view.findViewById(R.id.rvGpsLogs)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        chipGroupFilter = view.findViewById(R.id.chipGroupFilter)
        fabAddLog = view.findViewById(R.id.fabAddLog)

        rvGpsLogs.layoutManager = LinearLayoutManager(requireContext())
        adapter = GpsLogAdapter(emptyList())
        rvGpsLogs.adapter = adapter

        swipeRefresh.setColorSchemeResources(R.color.secondary)
        swipeRefresh.setOnRefreshListener {
            val checkedIds = chipGroupFilter.checkedChipIds
            val daysFilter = if (checkedIds.isEmpty()) 0 else {
                when (checkedIds.first()) {
                    R.id.chipToday -> 0
                    R.id.chip3Days -> 3
                    R.id.chip7Days -> 7
                    R.id.chip30Days -> 30
                    R.id.chipAll -> null
                    else -> 0
                }
            }
            loadGpsLogs(daysFilter)
        }

        setupFilter()
        setupFab()
        loadGpsLogs(0) // Default to today
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh FAB visibility based on tracking state whenever we return
        if (::fabAddLog.isInitialized) {
            fabAddLog.visibility = if (AppPreferences.isTrackingActive(requireContext())) View.VISIBLE else View.GONE
        }
    }
    
    private fun setupFab() {
        fabAddLog.visibility = if (AppPreferences.isTrackingActive(requireContext())) View.VISIBLE else View.GONE
        
        fabAddLog.setOnClickListener {
            val editText = EditText(requireContext()).apply {
                hint = "E.g., Meeting with client"
            }
            
            AlertDialog.Builder(requireContext())
                .setTitle("Add Location Log")
                .setMessage("Enter an optional remark for this manual GPS record")
                .setView(editText)
                .setPositiveButton("Submit") { dialog, _ ->
                    val remark = editText.text.toString().takeIf { it.isNotBlank() }
                    
                    val intent = Intent(requireContext(), LocationTrackingService::class.java).apply {
                        action = LocationTrackingService.ACTION_FORCE_LOG
                        putExtra(LocationTrackingService.EXTRA_REMARK, remark)
                    }
                    requireContext().startService(intent)
                    dialog.dismiss()
                    
                    // Show immediate feedback and refresh list briefly
                    swipeRefresh.isRefreshing = true
                    view?.postDelayed({
                        val checkedIds = chipGroupFilter.checkedChipIds
                        val daysFilter = if (checkedIds.isEmpty() || checkedIds.first() == R.id.chipToday) 0 else null // simplify refresh
                        loadGpsLogs(daysFilter)
                    }, 2000)
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupFilter() {
        chipGroupFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            when (checkedIds.first()) {
                R.id.chipToday -> loadGpsLogs(0)
                R.id.chip3Days -> loadGpsLogs(3)
                R.id.chip7Days -> loadGpsLogs(7)
                R.id.chip30Days -> loadGpsLogs(30)
                R.id.chipAll -> loadGpsLogs(null)
            }
        }
    }

    private fun loadGpsLogs(daysToFilter: Int?) {
        if (view == null) return
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val userId = com.example.attendanceapp.utils.SessionManager(requireContext()).getUserId()

            val logs = withContext(Dispatchers.IO) {
                if (daysToFilter != null) {
                    val calendar = Calendar.getInstance()
                    
                    if (daysToFilter == 0) {
                        // For "Today", set the time to the very start of the day (00:00:00)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                    } else {
                        // For past days, subtract the entire day offset
                        calendar.add(Calendar.DAY_OF_YEAR, -daysToFilter)
                    }
                    
                    val startDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(calendar.time)
                    db.gpsLogDao().getLogsFromDateForUser(userId, startDateStr)
                } else {
                    db.gpsLogDao().getAllLogsForUser(userId)
                }
            }

            if (logs.isEmpty()) {
                rvGpsLogs.visibility = View.GONE
                layoutEmptyState.visibility = View.VISIBLE
            } else {
                rvGpsLogs.visibility = View.VISIBLE
                layoutEmptyState.visibility = View.GONE
                adapter.updateData(logs)
            }
            swipeRefresh.isRefreshing = false
        }
    }
}
