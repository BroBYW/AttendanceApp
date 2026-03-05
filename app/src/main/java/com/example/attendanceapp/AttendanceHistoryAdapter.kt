package com.example.attendanceapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

import com.example.attendanceapp.data.network.dto.AttendanceResponse
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AttendanceHistoryAdapter(
    private val records: List<AttendanceResponse>
) : RecyclerView.Adapter<AttendanceHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivStatusIcon: ImageView = view.findViewById(R.id.ivStatusIcon)
        val tvDate: TextView = view.findViewById(R.id.tvHistoryDate)
        val tvLocation: TextView = view.findViewById(R.id.tvHistoryLocation)
        val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
        val tvStatus: TextView = view.findViewById(R.id.tvHistoryStatus)
        val tvRejectionNote: TextView = view.findViewById(R.id.tvRejectionNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        val context = holder.itemView.context

        val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        
        try {
            val parsedDate = apiDateFormat.parse(record.attendanceDate)
            holder.tvDate.text = parsedDate?.let { displayDateFormat.format(it) } ?: record.attendanceDate
        } catch (e: Exception) {
            holder.tvDate.text = record.attendanceDate
        }

        if (record.clockInTime != null) {
            val apiTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
            val displayTimeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
            try {
                val parsedTime = apiTimeFormat.parse(record.clockInTime)
                holder.tvTime.text = parsedTime?.let { displayTimeFormat.format(it) } ?: record.clockInTime
            } catch (e: Exception) {
                holder.tvTime.text = record.clockInTime
            }
        } else {
            holder.tvTime.text = "—"
        }

        if (record.clockInLat != null && record.clockInLng != null) {
            holder.tvLocation.text = String.format("Lat: %.4f, Lng: %.4f", record.clockInLat, record.clockInLng)
        } else {
            holder.tvLocation.text = "No location recorded"
        }

        // Status display matching backend AttendanceStatus enum values
        when (record.status) {
            "AUTO_APPROVED" -> {
                holder.tvStatus.text = "Approved"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.success))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
            }
            "APPROVED" -> {
                holder.tvStatus.text = "Approved"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.success))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
            }
            "PENDING" -> {
                holder.tvStatus.text = "Pending"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.warning))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_warning)
            }
            "REJECTED" -> {
                holder.tvStatus.text = "Rejected"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.error))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_cancel)
            }
            else -> {
                holder.tvStatus.text = record.status
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.on_surface))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
            }
        }

        // Show note only for REJECTED records
        if (record.status == "REJECTED" && !record.notes.isNullOrBlank()) {
            holder.tvRejectionNote.visibility = View.VISIBLE
            holder.tvRejectionNote.text = buildString {
                append("Rejection Note: ")
                append(record.notes)
                if (!record.reviewedByName.isNullOrBlank()) {
                    append(" — ")
                    append(record.reviewedByName)
                }
            }
        } else {
            holder.tvRejectionNote.visibility = View.GONE
        }
    }

    override fun getItemCount() = records.size
}
