package com.example.attendanceapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.attendanceapp.data.GpsLogEntity

class GpsLogAdapter(private var logs: List<GpsLogEntity>) :
    RecyclerView.Adapter<GpsLogAdapter.GpsLogViewHolder>() {

    class GpsLogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCoordinates: TextView = view.findViewById(R.id.tvCoordinates)
        val tvAccuracy: TextView = view.findViewById(R.id.tvAccuracy)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GpsLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gps_log, parent, false)
        return GpsLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: GpsLogViewHolder, position: Int) {
        val log = logs[position]
        holder.tvCoordinates.text = "Lat: ${log.latitude}, Lng: ${log.longitude}"
        holder.tvAccuracy.text = "Accuracy: ${log.accuracy ?: 0.0}m"
        holder.tvTimestamp.text = log.timestamp
    }

    override fun getItemCount() = logs.size

    fun updateData(newLogs: List<GpsLogEntity>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}
