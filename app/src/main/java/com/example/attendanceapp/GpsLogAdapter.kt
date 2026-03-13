package com.example.attendanceapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.attendanceapp.data.GpsLogEntity
import com.example.attendanceapp.data.network.dto.GpsLogClassifyResponse

class GpsLogAdapter(private var logs: List<GpsLogUiItem>) :
    RecyclerView.Adapter<GpsLogAdapter.GpsLogViewHolder>() {

    class GpsLogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCoordinates: TextView = view.findViewById(R.id.tvCoordinates)
        val tvAccuracy: TextView = view.findViewById(R.id.tvAccuracy)
        val tvAreaStatus: TextView = view.findViewById(R.id.tvAreaStatus)
        val tvLocationName: TextView = view.findViewById(R.id.tvLocationName)
        val tvRemark: TextView = view.findViewById(R.id.tvRemark)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GpsLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gps_log, parent, false)
        return GpsLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: GpsLogViewHolder, position: Int) {
        val item = logs[position]
        val log = item.log
        holder.tvCoordinates.text = "Lat: ${log.latitude}, Lng: ${log.longitude}"
        holder.tvAccuracy.text = "Accuracy: ${log.accuracy ?: 0.0}m"
        holder.tvTimestamp.text = log.timestamp

        val statusLabel = toStatusLabel(item.classification?.areaStatus)
        if (statusLabel != null) {
            holder.tvAreaStatus.visibility = View.VISIBLE
            holder.tvAreaStatus.text = "Status: $statusLabel"
            val colorRes = when (item.classification?.areaStatus?.uppercase()) {
                "NORMAL" -> R.color.success
                "OUTSTATION" -> R.color.primary
                "OUTSIDE" -> R.color.error
                else -> R.color.on_surface
            }
            holder.tvAreaStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, colorRes))
        } else {
            holder.tvAreaStatus.visibility = View.GONE
        }

        val locationName = item.classification?.locationName?.takeIf { it.isNotBlank() }
        if (locationName != null) {
            holder.tvLocationName.visibility = View.VISIBLE
            holder.tvLocationName.text = "Location: $locationName"
        } else {
            holder.tvLocationName.visibility = View.GONE
        }

        if (!log.remark.isNullOrBlank()) {
            holder.tvRemark.visibility = View.VISIBLE
            holder.tvRemark.text = "Note: ${log.remark}"
        } else {
            holder.tvRemark.visibility = View.GONE
        }
    }

    override fun getItemCount() = logs.size

    fun updateData(newLogs: List<GpsLogUiItem>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    private fun toStatusLabel(raw: String?): String? {
        return when (raw?.uppercase()) {
            "NORMAL" -> "Normal"
            "OUTSTATION" -> "Outstation"
            "OUTSIDE" -> "Outside Working Area"
            else -> null
        }
    }
}

data class GpsLogUiItem(
    val log: GpsLogEntity,
    val classification: GpsLogClassifyResponse?
)
