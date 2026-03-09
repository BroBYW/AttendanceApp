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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.example.attendanceapp.utils.SessionManager
import com.example.attendanceapp.data.network.RetrofitClient
import com.example.attendanceapp.data.AppPreferences
import com.example.attendanceapp.service.LocationTrackingService
import com.example.attendanceapp.R
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.material.floatingactionbutton.FloatingActionButton

import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.MotionEvent

import org.json.JSONObject
import org.json.JSONArray
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class HomeFragment : Fragment() {

    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTimer: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var mapView: MapView
    private lateinit var tvLocationStatus: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var fabRecenter: FloatingActionButton
    private var userMarker: Marker? = null
    private var locationCallback: LocationCallback? = null

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
        // Initialize osmdroid configuration
        val ctx = requireActivity()
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = ctx.packageName
        
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvClock = view.findViewById(R.id.tvClock)
        tvDate = view.findViewById(R.id.tvDate)
        tvTimer = view.findViewById(R.id.tvTimer)
        mapView = view.findViewById(R.id.dashboardMapView)
        tvLocationStatus = view.findViewById(R.id.tvLocationStatus)
        fabRecenter = view.findViewById(R.id.fabRecenter)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        setupMap()
        
        fabRecenter.setOnClickListener {
            userMarker?.let { marker ->
                mapView.controller.animateTo(marker.position)
            }
        }


        // Time-based greeting
        val tvGreeting = view.findViewById<TextView>(R.id.tvGreeting)
        val username = activity?.intent?.getStringExtra("username") ?: "User"
        tvGreeting.text = getTimeBasedGreeting(username)

        // Logout button
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        val btnClockIn = view.findViewById<MaterialButton>(R.id.btnClockIn)
        val btnClockOut = view.findViewById<MaterialButton>(R.id.btnClockOut)

        btnClockIn.setOnClickListener {
            handleClockIn()
        }

        btnClockOut.setOnClickListener {
            handleClockOut()
        }

        // Pull-to-refresh
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeResources(R.color.secondary)
        swipeRefresh.setOnRefreshListener {
            refreshData()
        }

        updateClock()
    }

    private fun handleClockIn() {
        val calendar = Calendar.getInstance(malaysiaTimeZone)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        val timeFloat = hour + (minute / 60.0f)

        if (timeFloat < 6.0f) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Too Early")
                .setMessage("Clock in only starts at 6:00 AM. Please try again later.")
                .setPositiveButton("OK", null)
                .show()
        } else if (timeFloat <= 7.0f) {
            // Normal Clock In
            Toast.makeText(requireContext(), "Proceeding to normal clock in", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), CapturePhotoActivity::class.java).apply {
                putExtra("clock_type", "normal")
            }
            startActivity(intent)
        } else {
            // Late Clock In
            Toast.makeText(requireContext(), "Opening late submission form", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), LateSubmissionActivity::class.java))
        }
    }

    private fun handleClockOut() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clock Out")
            .setMessage("Are you sure you want to clock out? Location tracking will stop.")
            .setPositiveButton("Clock Out") { _, _ ->
                performClockOut()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performClockOut() {
        val locationHelper = LocationHelper(requireContext())
        if (!locationHelper.hasLocationPermission()) {
            Toast.makeText(context, "Location permission required to clock out", Toast.LENGTH_SHORT).show()
            return
        }

        swipeRefresh.isRefreshing = true

        locationHelper.getCurrentLocation(object : LocationHelper.OnLocationResultListener {
            override fun onLocationReceived(location: android.location.Location) {
                submitClockOutData(location.latitude, location.longitude, location.accuracy)
            }

            override fun onLocationError(message: String) {
                swipeRefresh.isRefreshing = false
                Toast.makeText(context, "Failed to get location: $message", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun submitClockOutData(lat: Double, lng: Double, accuracy: Float) {
        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService(requireContext())
                val requestObj = com.example.attendanceapp.data.network.dto.ClockOutRequest(
                    latitude = lat,
                    longitude = lng
                )
                val jsonString = com.google.gson.Gson().toJson(requestObj)
                val mediaType = "application/json".toMediaTypeOrNull()
                val dataPart = jsonString.toRequestBody(mediaType)
                
                val response = apiService.clockOut(dataPart, null)

                if (response.isSuccessful && response.body()?.success == true) {
                    // Record the final GPS log and sync it before stopping the service
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val userId = com.example.attendanceapp.utils.SessionManager(requireContext()).getUserId()
                        if (userId != -1L) {
                            val db = com.example.attendanceapp.data.AppDatabase.getDatabase(requireContext())
                            db.gpsLogDao().insertLog(
                                com.example.attendanceapp.data.GpsLogEntity(
                                    userId = userId,
                                    latitude = lat,
                                    longitude = lng,
                                    timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                                    accuracy = accuracy,
                                    synced = false
                                )
                            )
                            // Sync all unsynced logs (including this final one) to the backend
                            try {
                                val unsyncedLogs = db.gpsLogDao().getUnsyncedLogsForUser(userId)
                                if (unsyncedLogs.isNotEmpty()) {
                                    val logDtos = unsyncedLogs.map { entity ->
                                        com.example.attendanceapp.data.network.dto.GpsLogDto(
                                            latitude = entity.latitude,
                                            longitude = entity.longitude,
                                            timestamp = entity.timestamp.replace(" ", "T"),
                                            accuracy = entity.accuracy?.toDouble(),
                                            remark = entity.remark,
                                            synced = true
                                        )
                                    }
                                    val syncResponse = apiService.submitGpsLogs(logDtos)
                                    if (syncResponse.isSuccessful && syncResponse.body()?.success == true) {
                                        db.gpsLogDao().markAsSynced(unsyncedLogs.map { it.id })
                                    }
                                }
                            } catch (_: Exception) { /* sync will retry later */ }
                        }
                    }

                    // Stop the location tracking service
                    val serviceIntent = Intent(requireContext(), LocationTrackingService::class.java)
                    serviceIntent.action = LocationTrackingService.ACTION_STOP
                    // Clear clock-in time
                    AppPreferences.clearClockInTime(requireContext())
                    requireContext().startService(serviceIntent)
                    
                    AppPreferences.setTrackingActive(requireContext(), false)

                    Toast.makeText(requireContext(), "Clocked Out Successfully", Toast.LENGTH_SHORT).show()
                    checkTrackingState()
                    refreshData() // Refresh records to show new Clock Out state
                } else {
                    Toast.makeText(requireContext(), "Clock out failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun checkTrackingState() {
        val btnClockIn = view?.findViewById<MaterialButton>(R.id.btnClockIn)
        val btnClockOut = view?.findViewById<MaterialButton>(R.id.btnClockOut)
        
        if (btnClockIn != null && btnClockOut != null) {
            val isTracking = AppPreferences.isTrackingActive(requireContext())
            
            if (isTracking) {
                // If clock-in time wasn't saved yet, set it now
                if (AppPreferences.getClockInTime(requireContext()) == -1L) {
                    AppPreferences.setClockInTime(requireContext(), System.currentTimeMillis())
                }

                // Clocked In currently
                btnClockIn.visibility = View.GONE
                
                btnClockOut.visibility = View.VISIBLE
                btnClockOut.isEnabled = true
                btnClockOut.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.error))
                btnClockOut.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_error))

                tvTimer.visibility = View.VISIBLE
            } else {
                // Not Clocked In
                btnClockIn.visibility = View.VISIBLE
                btnClockIn.isEnabled = true
                btnClockIn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.success))
                btnClockIn.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
                
                btnClockOut.visibility = View.GONE
                tvTimer.visibility = View.GONE
            }
        }
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
        if (view == null) return
        swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService(requireContext())
                
                // Fetch profile to update assigned areas concurrently with attendance
                val profileDeferred = async { apiService.getMyProfile() }
                val attendanceDeferred = async { apiService.getMyAttendance() }
                
                val profileRes = profileDeferred.await()
                val attendanceRes = attendanceDeferred.await()

                var isSuccess = false

                if (profileRes.isSuccessful && profileRes.body()?.success == true) {
                    profileRes.body()?.data?.let { user ->
                        SessionManager(requireContext()).saveOfficeAreaIds(user.assignedOfficeAreaIds)
                        loadOfficeAreaPolygons() // re-fetch and re-draw polygons
                        
                        // Also trigger location check with new areas
                        SecurityException().let { // Check permission safely
                            try {
                                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                    if (loc != null) {
                                        checkLocationStatus(loc.latitude, loc.longitude)
                                    }
                                }
                            } catch (e: SecurityException) {
                                // Missing location permission
                            }
                        }
                    }
                    isSuccess = true
                }

                if (attendanceRes.isSuccessful && attendanceRes.body()?.success == true) {
                    isSuccess = true
                }

                if (isSuccess) {
                    Toast.makeText(context, "Data refreshed", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Refresh failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showLogoutDialog() {
        val safeContext = context ?: return
        MaterialAlertDialogBuilder(safeContext)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Logout") { _, _ ->
                // Clear session data
                com.example.attendanceapp.utils.SessionManager(safeContext).clearSession()

                // Explicitly halt the background GPS tracking service upon logout to prevent zombie logs
                val serviceIntent = Intent(safeContext, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_STOP
                }
                safeContext.startService(serviceIntent)
                AppPreferences.setTrackingActive(safeContext, false)
                AppPreferences.clearClockInTime(safeContext)

                // Launch LoginActivity and clear the stack safely
                val intent = Intent(safeContext, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                activity?.finish()
            }
            .show()
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

        // Update working duration timer if tracking is active
        if (AppPreferences.isTrackingActive(requireContext())) {
            val clockInTime = AppPreferences.getClockInTime(requireContext())
            if (clockInTime != -1L) {
                val durationMillis = System.currentTimeMillis() - clockInTime
                val seconds = (durationMillis / 1000) % 60
                val minutes = (durationMillis / (1000 * 60)) % 60
                val hours = (durationMillis / (1000 * 60 * 60))
                tvTimer.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            }
        }
    }
    
    private fun setupMap() {
        // CartoDB Positron tile source (Raster tiles looking like Positron)
        mapView.setTileSource(
            XYTileSource(
                "CartoDBPositron",
                0, 20, 256, ".png",
                arrayOf(
                    "https://a.basemaps.cartocdn.com/light_all/",
                    "https://b.basemaps.cartocdn.com/light_all/",
                    "https://c.basemaps.cartocdn.com/light_all/"
                )
            )
        )
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(16.0)
        
        // Prevent ScrollView from intercepting map touches
        mapView.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        // Default to Kuala Lumpur as initial view before fetching location
        mapView.controller.setCenter(GeoPoint(3.1390, 101.6869))
        
        loadOfficeAreaPolygons()
        startLocationUpdates()
    }
    
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            tvLocationStatus.text = "Location Permission Required"
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val currentGeoPoint = GeoPoint(location.latitude, location.longitude)
                    
                    if (userMarker == null) {
                        userMarker = Marker(mapView).apply {
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "You are here"
                            // Using Osmdroid's pre-built nice red/orange marker or ContextCompat drawable
                            icon = ContextCompat.getDrawable(requireContext(), org.osmdroid.library.R.drawable.osm_ic_center_map)
                            mapView.overlays.add(this)
                            mapView.controller.animateTo(currentGeoPoint)
                        }
                    } else {
                        userMarker?.position = currentGeoPoint
                    }
                    mapView.invalidate()
                    
                    checkLocationStatus(location.latitude, location.longitude)
                }
            }
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }
    
    private fun checkLocationStatus(lat: Double, lon: Double) {
        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sessionManager = SessionManager(requireContext())
                val assignedIds = sessionManager.getOfficeAreaIds()
                if (assignedIds.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        tvLocationStatus.text = "No Working Area Assigned"
                    }
                    return@launch
                }
                
                val apiService = RetrofitClient.getApiService(requireContext())
                var isInsideAny = false
                var insideAreaName = ""
                
                for (id in assignedIds) {
                    val response = apiService.checkLocation(lat, lon, id)
                    if (response.isSuccessful) {
                        val body = response.body()
                        val data = body?.data
                        if (data != null && data["insideGeofence"] == true) {
                            isInsideAny = true
                            insideAreaName = data["officeAreaName"] as? String ?: ""
                            break
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (isInsideAny) {
                        val displayName = if (insideAreaName.isNotEmpty()) "✓ Normal ($insideAreaName)" else "✓ Normal"
                        tvLocationStatus.text = displayName
                        tvLocationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                    } else {
                        tvLocationStatus.text = "❌ Outside Working Area"
                        tvLocationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    tvLocationStatus.text = "Status Unknown"
                }
            }
        }
    }

    private fun loadOfficeAreaPolygons() {
        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sessionManager = SessionManager(requireContext())
                val assignedIds = sessionManager.getOfficeAreaIds()
                if (assignedIds.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        tvLocationStatus.text = "No Working Area Assigned"
                        mapView.overlays.removeAll { it is Polygon }
                        mapView.invalidate()
                    }
                    return@launch
                }

                val apiService = RetrofitClient.getApiService(requireContext())
                
                // Fetch all GeoJSON concurrently
                val deferredResponses = assignedIds.map { id ->
                    async {
                        try {
                            id to apiService.getGeoJson(id)
                        } catch (e: Exception) {
                            Log.e("HomeFragmentMap", "Fetch failed for $id", e)
                            id to null
                        }
                    }
                }
                
                val results = deferredResponses.awaitAll()
                val allPolygonPoints = mutableListOf<List<GeoPoint>>()

                // Parse them
                for ((id, response) in results) {
                    if (response != null && response.isSuccessful) {
                        val geoJsonString = response.body()
                        if (geoJsonString != null) {
                            allPolygonPoints.addAll(parseGeoJsonToPoints(geoJsonString))
                        }
                    } else if (response != null) {
                        Log.e("HomeFragmentMap", "GeoJSON fetch for $id failed: code ${response.code()}")
                    }
                }

                // Render all at once on the main thread
                withContext(Dispatchers.Main) {
                    // Remove existing Polygons to avoid duplicates when refreshing
                    mapView.overlays.removeAll { it is Polygon }
                    
                    for (points in allPolygonPoints) {
                        val polygon = Polygon(mapView)
                        polygon.points = points
                        polygon.fillPaint.color = 0x330000FF // Translucent Blue
                        polygon.outlinePaint.color = 0xFF0000FF.toInt() // Solid Blue
                        polygon.outlinePaint.strokeWidth = 3f
                        polygon.infoWindow = null // Disable the default grey click popup
                        mapView.overlays.add(polygon)
                    }
                    mapView.invalidate()
                    Log.d("HomeFragmentMap", "Batch rendered ${allPolygonPoints.size} polygons")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseGeoJsonToPoints(geoJsonString: String): List<List<GeoPoint>> {
        val resultList = mutableListOf<List<GeoPoint>>()
        try {
            val jsonObject = JSONObject(geoJsonString)
            val type = if (jsonObject.has("type")) jsonObject.getString("type") else ""

            if (type == "FeatureCollection") {
                val features = jsonObject.getJSONArray("features")
                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    if (feature.has("geometry") && !feature.isNull("geometry")) {
                        parseGeometry(feature.getJSONObject("geometry"), resultList)
                    }
                }
            } else if (type == "Feature") {
                if (jsonObject.has("geometry") && !jsonObject.isNull("geometry")) {
                    parseGeometry(jsonObject.getJSONObject("geometry"), resultList)
                }
            } else {
                // Must be a Geometry (Polygon, MultiPolygon, GeometryCollection)
                parseGeometry(jsonObject, resultList)
            }
        } catch (e: Exception) {
            Log.e("HomeFragmentMap", "Error parsing GeoJSON", e)
            e.printStackTrace()
        }
        return resultList
    }

    private fun parseGeometry(geometry: JSONObject, resultList: MutableList<List<GeoPoint>>) {
        if (!geometry.has("type")) return
        val type = geometry.getString("type")
        
        if (type == "Polygon") {
            resultList.add(extractPolygonPoints(geometry.getJSONArray("coordinates")))
        } else if (type == "MultiPolygon") {
            val multiCoordinates = geometry.getJSONArray("coordinates")
            for (c in 0 until multiCoordinates.length()) {
                resultList.add(extractPolygonPoints(multiCoordinates.getJSONArray(c)))
            }
        } else if (type == "GeometryCollection") {
            val geometries = geometry.getJSONArray("geometries")
            for (g in 0 until geometries.length()) {
                val subGeom = geometries.getJSONObject(g)
                parseGeometry(subGeom, resultList)
            }
        }
    }

    private fun extractPolygonPoints(coordinates: JSONArray): List<GeoPoint> {
        val geoPoints = ArrayList<GeoPoint>()
        try {
            // Polygon has an outer ring and optional inner rings. We take the first array (outer ring).
            val outerRing = coordinates.getJSONArray(0)
            for (j in 0 until outerRing.length()) {
                val pointArray = outerRing.getJSONArray(j)
                val lon = pointArray.getDouble(0) // GeoJSON is Longitude, Latitude
                val lat = pointArray.getDouble(1)
                geoPoints.add(GeoPoint(lat, lon))
            }
        } catch (e: Exception) {
            Log.e("HomeFragmentMap", "Error extracting Polygon points", e)
        }
        return geoPoints
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        startLocationUpdates()
        handler.post(clockRunnable)
        checkTrackingState()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        stopLocationUpdates()
        handler.removeCallbacks(clockRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDetach()
    }
}
