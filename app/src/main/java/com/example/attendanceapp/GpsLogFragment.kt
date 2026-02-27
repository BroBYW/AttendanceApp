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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GpsLogFragment : Fragment() {

    private lateinit var rvGpsLogs: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var adapter: GpsLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gps_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvGpsLogs = view.findViewById(R.id.rvGpsLogs)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)

        rvGpsLogs.layoutManager = LinearLayoutManager(requireContext())
        adapter = GpsLogAdapter(emptyList())
        rvGpsLogs.adapter = adapter

        loadGpsLogs()
    }

    private fun loadGpsLogs() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val logs = withContext(Dispatchers.IO) {
                db.gpsLogDao().getAllLogs()
            }

            if (logs.isEmpty()) {
                rvGpsLogs.visibility = View.GONE
                layoutEmptyState.visibility = View.VISIBLE
            } else {
                rvGpsLogs.visibility = View.VISIBLE
                layoutEmptyState.visibility = View.GONE
                adapter.updateData(logs)
            }
        }
    }
}
