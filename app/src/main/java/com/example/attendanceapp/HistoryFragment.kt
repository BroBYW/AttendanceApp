package com.example.attendanceapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendanceapp.data.network.RetrofitClient
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    private lateinit var historyAdapter: AttendanceHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val swipeRefresh = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshHistory)
        val rvFullHistory = view.findViewById<RecyclerView>(R.id.rvFullHistory)
        val layoutHistoryEmpty = view.findViewById<LinearLayout>(R.id.layoutHistoryEmpty)

        rvFullHistory.layoutManager = LinearLayoutManager(requireContext())
        historyAdapter = AttendanceHistoryAdapter(emptyList())
        rvFullHistory.adapter = historyAdapter
        
        swipeRefresh.setColorSchemeResources(R.color.secondary)
        swipeRefresh.setOnRefreshListener {
            fetchAttendanceHistory(rvFullHistory, layoutHistoryEmpty, swipeRefresh)
        }

        fetchAttendanceHistory(rvFullHistory, layoutHistoryEmpty, swipeRefresh)
    }

    private fun fetchAttendanceHistory(rvFullHistory: RecyclerView, layoutHistoryEmpty: LinearLayout, swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout) {
        if (view == null) return
        swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val safeContext = context ?: run {
                swipeRefresh.isRefreshing = false
                return@launch
            }
            try {
                val apiService = RetrofitClient.getApiService(safeContext)
                val response = apiService.getMyAttendance()

                if (response.isSuccessful && response.body()?.success == true) {
                    // Assuming Page response object has "content" array
                    // You might need an intermediate DTO for Page<AttendanceResponse> if Gson fails mapping
                    // For now, attempting to extract the data list:
                    val jsonElement = com.google.gson.Gson().toJsonTree(response.body()?.data)
                    
                    if (jsonElement.isJsonObject && jsonElement.asJsonObject.has("content")) {
                        val recordsArray = jsonElement.asJsonObject.get("content").asJsonArray
                        val type = object : com.google.gson.reflect.TypeToken<List<com.example.attendanceapp.data.network.dto.AttendanceResponse>>() {}.type
                        val records: List<com.example.attendanceapp.data.network.dto.AttendanceResponse> = com.google.gson.Gson().fromJson(recordsArray, type)

                        if (records.isEmpty()) {
                            rvFullHistory.visibility = View.GONE
                            layoutHistoryEmpty.visibility = View.VISIBLE
                        } else {
                            rvFullHistory.visibility = View.VISIBLE
                            layoutHistoryEmpty.visibility = View.GONE
                            rvFullHistory.adapter = AttendanceHistoryAdapter(records)
                        }
                    } else {
                        Toast.makeText(safeContext, "Unexpected response format", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(safeContext, "Failed to load history: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(safeContext, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                if (view != null) {
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }
}
