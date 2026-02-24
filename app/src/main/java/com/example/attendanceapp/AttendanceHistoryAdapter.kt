package com.example.attendanceapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class AttendanceHistoryAdapter(
    private val records: List<AttendanceRecord>
) : RecyclerView.Adapter<AttendanceHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivStatusIcon: ImageView = view.findViewById(R.id.ivStatusIcon)
        val tvDate: TextView = view.findViewById(R.id.tvHistoryDate)
        val tvLocation: TextView = view.findViewById(R.id.tvHistoryLocation)
        val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
        val tvStatus: TextView = view.findViewById(R.id.tvHistoryStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        val context = holder.itemView.context

        holder.tvDate.text = record.date
        holder.tvTime.text = record.time
        holder.tvLocation.text = record.location

        when (record.status) {
            AttendanceRecord.Status.ON_TIME -> {
                holder.tvStatus.text = "On Time"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.success))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
            }
            AttendanceRecord.Status.LATE -> {
                holder.tvStatus.text = "Late"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.warning))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_warning)
            }
            AttendanceRecord.Status.ABSENT -> {
                holder.tvStatus.text = "Absent"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.error))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_cancel)
            }
        }
    }

    override fun getItemCount() = records.size
}
