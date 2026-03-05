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

class GpsLogFragment : Fragment() {

    private lateinit var rvGpsLogs: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var adapter: GpsLogAdapter
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

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

        rvGpsLogs.layoutManager = LinearLayoutManager(requireContext())
        adapter = GpsLogAdapter(emptyList())
        rvGpsLogs.adapter = adapter

        swipeRefresh.setColorSchemeResources(R.color.secondary)
        swipeRefresh.setOnRefreshListener {
            val checkedIds = chipGroupFilter.checkedChipIds
            val daysFilter = if (checkedIds.isEmpty()) 3 else {
                when (checkedIds.first()) {
                    R.id.chip3Days -> 3
                    R.id.chip7Days -> 7
                    R.id.chip30Days -> 30
                    R.id.chipAll -> null
                    else -> 3
                }
            }
            loadGpsLogs(daysFilter)
        }

        setupFilter()
        loadGpsLogs(3) // Default to last 3 days
    }

    private fun setupFilter() {
        chipGroupFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            when (checkedIds.first()) {
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
                    calendar.add(Calendar.DAY_OF_YEAR, -daysToFilter)
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
