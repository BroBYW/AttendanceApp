package com.example.attendanceapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistoryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvFullHistory = view.findViewById<RecyclerView>(R.id.rvFullHistory)
        val layoutHistoryEmpty = view.findViewById<LinearLayout>(R.id.layoutHistoryEmpty)

        rvFullHistory.layoutManager = LinearLayoutManager(requireContext())

        val allRecords = listOf(
            AttendanceRecord("24 Feb 2026", "06:45 AM", AttendanceRecord.Status.ON_TIME, "Lat: 3.1390, Lng: 101.6869"),
            AttendanceRecord("23 Feb 2026", "06:52 AM", AttendanceRecord.Status.ON_TIME, "Lat: 3.1390, Lng: 101.6869"),
            AttendanceRecord("22 Feb 2026", "07:15 AM", AttendanceRecord.Status.LATE, "Lat: 3.1391, Lng: 101.6870"),
            AttendanceRecord("21 Feb 2026", "06:30 AM", AttendanceRecord.Status.ON_TIME, "Lat: 3.1389, Lng: 101.6868"),
            AttendanceRecord("20 Feb 2026", "—", AttendanceRecord.Status.ABSENT, "No location recorded"),
            AttendanceRecord("19 Feb 2026", "06:40 AM", AttendanceRecord.Status.ON_TIME, "Lat: 3.1390, Lng: 101.6869"),
            AttendanceRecord("18 Feb 2026", "06:55 AM", AttendanceRecord.Status.ON_TIME, "Lat: 3.1390, Lng: 101.6869"),
            AttendanceRecord("17 Feb 2026", "07:20 AM", AttendanceRecord.Status.LATE, "Lat: 3.1392, Lng: 101.6871"),
            AttendanceRecord("16 Feb 2026", "—", AttendanceRecord.Status.ABSENT, "No location recorded"),
            AttendanceRecord("15 Feb 2026", "06:35 AM", AttendanceRecord.Status.ON_TIME, "Lat: 3.1390, Lng: 101.6869")
        )

        if (allRecords.isEmpty()) {
            rvFullHistory.visibility = View.GONE
            layoutHistoryEmpty.visibility = View.VISIBLE
        } else {
            rvFullHistory.visibility = View.VISIBLE
            layoutHistoryEmpty.visibility = View.GONE
            rvFullHistory.adapter = AttendanceHistoryAdapter(allRecords)
        }
    }
}
