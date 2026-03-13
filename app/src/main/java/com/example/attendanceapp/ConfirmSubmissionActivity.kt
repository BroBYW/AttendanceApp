package com.example.attendanceapp

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.animation.AnimationUtils
import android.os.Build
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response

class ConfirmSubmissionActivity : AppCompatActivity() {

    private var clockType: String? = null
    private var lateReason: String? = null
    private var attachmentPath: String? = null
    private var photoUri: String? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var currentAccuracy: Float? = null

    private var tempUploadFile: java.io.File? = null

    private lateinit var locationHelper: LocationHelper
    private lateinit var tvLocationValue: TextView
    private lateinit var tvLocationAccuracy: TextView
    private lateinit var progressLocation: ProgressBar
    private lateinit var btnSubmit: MaterialButton
    private lateinit var ivSuccessOverlay: ImageView
    private lateinit var mapView: MapView
    private lateinit var cardMap: MaterialCardView
    private var mapEnabled: Boolean = true

    // Permission request launcher
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            fetchLocation()
        } else {
            progressLocation.visibility = View.GONE
            tvLocationValue.text = "⛔ Location permission denied"
            tvLocationAccuracy.text = "Location is required to submit attendance"
            btnSubmit.isEnabled = false
            showLocationRequiredDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize osmdroid configuration
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_confirm_submission)

        // Initialize map
        mapView = findViewById(R.id.mapView)
        cardMap = findViewById(R.id.cardMap)
        mapEnabled = !isRunningOnEmulator()
        if (mapEnabled) {
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.controller.setZoom(17.0)
            mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            // Emulator SystemUI can ANR when heavy map rendering is active.
            cardMap.visibility = View.GONE
            mapView.setUseDataConnection(false)
            mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        // Initialize location helper
        locationHelper = LocationHelper(this)

        // Retrieve data from the previous activity
        clockType = intent.getStringExtra("clock_type")
        lateReason = intent.getStringExtra("late_reason")
        attachmentPath = intent.getStringExtra("attachment_path")
        photoUri = intent.getStringExtra("photo_uri")

        // Display check-in info
        val tvQrCodeValue = findViewById<TextView>(R.id.tvQrCodeValue)
        if (clockType == "late") {
            tvQrCodeValue.text = "Type: Late Clock In\nReason: ${lateReason ?: "N/A"}\nAttachment: ${if (attachmentPath != null) "Yes" else "No"}"
        } else {
            tvQrCodeValue.text = "Type: Normal Clock In"
        }

        // Display the captured photo
        val ivPhoto = findViewById<ImageView>(R.id.ivPhoto)
        photoUri?.let {
            ivPhoto.setImageURI(Uri.parse(it))
        }

        // Location views
        tvLocationValue = findViewById(R.id.tvLocationValue)
        tvLocationAccuracy = findViewById(R.id.tvLocationAccuracy)
        progressLocation = findViewById(R.id.progressLocation)

        // Lottie animation
        ivSuccessOverlay = findViewById(R.id.ivSuccessOverlay)

        // Submit button (starts disabled until location is fetched)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnSubmit.isEnabled = false
        btnSubmit.setOnClickListener {
            submitAttendance()
        }

        // Back to home button
        val btnBackToHome = findViewById<MaterialButton>(R.id.btnRetake)
        btnBackToHome.setOnClickListener {
            finish()
        }

        // Start fetching location
        if (locationHelper.hasLocationPermission()) {
            fetchLocation()
        } else {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            requestLocationPermission.launch(perms.toTypedArray())
        }
    }

    private fun fetchLocation() {
        progressLocation.visibility = View.VISIBLE
        tvLocationValue.text = "Fetching location..."
        tvLocationAccuracy.text = ""

        locationHelper.getCurrentLocation(object : LocationHelper.OnLocationResultListener {
            override fun onLocationReceived(location: Location) {
                currentLatitude = location.latitude
                currentLongitude = location.longitude
                currentAccuracy = location.accuracy

                progressLocation.visibility = View.GONE
                tvLocationValue.text = String.format(
                    "Lat: %.6f, Lng: %.6f",
                    location.latitude, location.longitude
                )
                tvLocationAccuracy.text = String.format(
                    "Accuracy: ±%.1f meters", location.accuracy
                )

                // Show map with marker (disabled on emulator for stability)
                if (mapEnabled) {
                    showLocationOnMap(location.latitude, location.longitude)
                }

                // Enable submit button now that location is ready
                btnSubmit.isEnabled = true
            }

            override fun onLocationError(message: String) {
                progressLocation.visibility = View.GONE
                tvLocationValue.text = "⛔ Location unavailable"
                tvLocationAccuracy.text = message
                btnSubmit.isEnabled = false
                showLocationRequiredDialog()
            }
        })
    }

    private fun submitAttendance() {
        if (currentLatitude == null || currentLongitude == null) {
            android.widget.Toast.makeText(this, "Location not ready", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmit.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val sessionManager = com.example.attendanceapp.utils.SessionManager(this@ConfirmSubmissionActivity)
            var requestObj: com.example.attendanceapp.data.network.dto.ClockInRequest? = null
            try {
                val apiService = com.example.attendanceapp.data.network.RetrofitClient.getApiService(this@ConfirmSubmissionActivity)

                // Refresh user profile to get latest assigned office area IDs
                var assignedOfficeId = sessionManager.getOfficeAreaId()
                var allAssignedIds = sessionManager.getOfficeAreaIds()
                try {
                    val profileResponse = apiService.getMyProfile()
                    if (profileResponse.isSuccessful && profileResponse.body()?.success == true) {
                        val freshIds = profileResponse.body()?.data?.assignedOfficeAreaIds
                        sessionManager.saveOfficeAreaIds(freshIds)
                        allAssignedIds = freshIds ?: emptyList()
                        assignedOfficeId = freshIds?.firstOrNull()
                    }
                } catch (e: Exception) {
                    // If refresh fails, continue with cached value
                    e.printStackTrace()
                }
                
                // If there are multiple areas, find the one the user is actually inside
                if (allAssignedIds.size > 1 && currentLatitude != null && currentLongitude != null) {
                    for (id in allAssignedIds) {
                        try {
                            val checkRes = apiService.checkLocation(currentLatitude!!, currentLongitude!!, id)
                            if (checkRes.isSuccessful && checkRes.body()?.data?.get("insideGeofence") == true) {
                                assignedOfficeId = id
                                break
                            }
                        } catch (e: Exception) {
                           // Ignore and try next
                        }
                    }
                }

                // Prepare JSON data part
                requestObj = com.example.attendanceapp.data.network.dto.ClockInRequest(
                    clockInType = clockType?.uppercase() ?: "NORMAL",
                    latitude = currentLatitude!!,
                    longitude = currentLongitude!!,
                    officeAreaId = assignedOfficeId,
                    reason = lateReason,
                    documentUrl = null,
                    notes = null,
                    clientEventId = UUID.randomUUID().toString(),
                    clientTimestamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    queuedOffline = false
                )
                val jsonString = com.google.gson.Gson().toJson(requestObj)
                val mediaType = "application/json".toMediaTypeOrNull()
                val dataPart = jsonString.toRequestBody(mediaType)

                // Prepare Photo Part
                var selfiePart: okhttp3.MultipartBody.Part? = null
                if (photoUri != null) {
                    val file = getCompressedImageFileFromUri(Uri.parse(photoUri!!))
                    if (file != null) {
                        tempUploadFile = file
                        val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        selfiePart = okhttp3.MultipartBody.Part.createFormData("selfie", file.name, reqFile)
                    }
                }

                // Prepare Document Part
                var docPart: okhttp3.MultipartBody.Part? = null
                if (attachmentPath != null) {
                    val file = java.io.File(attachmentPath!!)
                    if (file.exists()) {
                        val reqFile = file.asRequestBody("*/*".toMediaTypeOrNull())
                        docPart = okhttp3.MultipartBody.Part.createFormData("document", file.name, reqFile)
                    }
                }

                val response = apiService.clockIn(dataPart, selfiePart, docPart)

                if (response.isSuccessful && response.body()?.success == true) {
                    if (requestObj != null && sessionManager.getUserId() != -1L) {
                        com.example.attendanceapp.service.AttendanceOfflineQueue.recordSyncedClockIn(
                            context = this@ConfirmSubmissionActivity,
                            userId = sessionManager.getUserId(),
                            request = requestObj.copy(queuedOffline = false)
                        )
                    }
                    launch(Dispatchers.Main) {
                        deleteLocalPhotos()
                        showSuccessAndFinish()
                    }
                } else {
                    var errorMessage = extractErrorMessage(response)
                    if (errorMessage.contains("Already clocked in today", ignoreCase = true)) {
                        errorMessage = "You can only clock in once per day!"
                    }

                    if (isRetriableHttpFailure(response.code()) && requestObj != null) {
                        val queued = queueClockInForLater(requestObj, sessionManager)
                        launch(Dispatchers.Main) {
                            if (queued) {
                                deleteLocalPhotos()
                                android.widget.Toast.makeText(
                                    this@ConfirmSubmissionActivity,
                                    "No stable network. Clock-in saved and will auto-sync.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                showSuccessAndFinish()
                            } else {
                                android.widget.Toast.makeText(
                                    this@ConfirmSubmissionActivity,
                                    "Submission failed and offline save also failed. Please try again.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                btnSubmit.isEnabled = true
                            }
                        }
                    } else {
                        launch(Dispatchers.Main) {
                            android.widget.Toast.makeText(this@ConfirmSubmissionActivity, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                            btnSubmit.isEnabled = true
                        }
                    }
                }
            } catch (e: Exception) {
                val queued = if (requestObj != null) {
                    queueClockInForLater(requestObj, sessionManager)
                } else {
                    false
                }

                launch(Dispatchers.Main) {
                    if (queued) {
                        deleteLocalPhotos()
                        android.widget.Toast.makeText(
                            this@ConfirmSubmissionActivity,
                            "Offline mode: clock-in saved and will sync automatically.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        showSuccessAndFinish()
                    } else {
                        android.widget.Toast.makeText(this@ConfirmSubmissionActivity, "Network error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        btnSubmit.isEnabled = true
                    }
                }
            }
        }
    }

    private suspend fun queueClockInForLater(
        requestObj: com.example.attendanceapp.data.network.dto.ClockInRequest?,
        sessionManager: com.example.attendanceapp.utils.SessionManager
    ): Boolean {
        if (requestObj == null) return false
        val userId = sessionManager.getUserId()
        if (userId == -1L) return false

        val rowId = com.example.attendanceapp.service.AttendanceOfflineQueue.enqueueClockIn(
            context = this@ConfirmSubmissionActivity,
            userId = userId,
            request = requestObj.copy(queuedOffline = true),
            photoUri = photoUri,
            documentPath = attachmentPath
        )
        return rowId != -1L
    }

    private fun extractErrorMessage(response: Response<*>): String {
        return try {
            val errorString = response.errorBody()?.string()
            if (errorString.isNullOrBlank()) {
                "Failed to submit: ${response.message()}"
            } else {
                val jsonObject = org.json.JSONObject(errorString)
                jsonObject.optString("message", "Failed to submit")
            }
        } catch (_: Exception) {
            "Failed to submit: ${response.message()}"
        }
    }

    private fun isRetriableHttpFailure(code: Int): Boolean {
        return code >= 500 || code == 408 || code == 429
    }

    private fun showSuccessAndFinish() {
        ivSuccessOverlay.visibility = View.VISIBLE
        val popIn = AnimationUtils.loadAnimation(this, R.anim.pop_in)
        ivSuccessOverlay.startAnimation(popIn)

        var locationText = "Recorded"
        if (currentLatitude != null && currentLongitude != null) {
            locationText = String.format(java.util.Locale.US, "%.4f, %.4f", currentLatitude, currentLongitude)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, SuccessActivity::class.java).apply {
                putExtra("location", locationText)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }, 1500)
    }

    private fun deleteLocalPhotos() {
        try {
            // 1. Delete the temporary upload copy from cacheDir
            tempUploadFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }

            // 2. Delete the original captured photo from external files dir
            photoUri?.let { uriString ->
                val uri = Uri.parse(uriString)
                val originalPath = uri.path
                if (originalPath != null) {
                    // ContentProvider paths often look like /my_images/Pictures/JPEG_2024...
                    // We need to resolve it back to the actual file path or delete via ContentResolver
                    try {
                        contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        // Fallback fallback if it's just a raw path
                        val file = java.io.File(originalPath)
                        if (file.exists()) file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFileFromUri(uri: Uri): java.io.File? {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = java.io.File.createTempFile("upload_", ".tmp", cacheDir)
            tempFile.outputStream().use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getCompressedImageFileFromUri(uri: Uri): java.io.File? {
        return try {
            val bitmap = contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return getFileFromUri(uri)

            val tempFile = java.io.File.createTempFile("upload_", ".jpg", cacheDir)
            java.io.FileOutputStream(tempFile).use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, output)
                output.flush()
            }
            bitmap.recycle()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            getFileFromUri(uri)
        }
    }

    private fun showLocationOnMap(latitude: Double, longitude: Double) {
        if (!mapEnabled) return
        val point = GeoPoint(latitude, longitude)
        mapView.controller.setCenter(point)

        val marker = Marker(mapView)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Your Location"
        mapView.overlays.add(marker)
        mapView.invalidate()

        cardMap.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (mapEnabled) mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (mapEnabled) mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationHelper.stopLocationUpdates()
        if (mapEnabled) mapView.onDetach()
    }

    private fun isRunningOnEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
        val model = Build.MODEL.lowercase(Locale.US)
        val product = Build.PRODUCT.lowercase(Locale.US)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        return fingerprint.contains("generic")
            || fingerprint.contains("emulator")
            || model.contains("emulator")
            || model.contains("android sdk built for x86")
            || manufacturer.contains("genymotion")
            || product.contains("sdk")
            || product.contains("emulator")
    }

    // Re-check location when user returns from Settings
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (locationHelper.hasLocationPermission()) {
            fetchLocation()
        } else {
            showLocationRequiredDialog()
        }
    }

    private fun showLocationRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("📍 Location Required")
            .setMessage(
                "Location access is mandatory to submit attendance. " +
                "Your GPS coordinates are recorded to verify you are at the correct location.\n\n" +
                "Please enable Location permission in Settings."
            )
            .setCancelable(false)
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                settingsLauncher.launch(intent)
            }
            .setNegativeButton("Retry") { _, _ ->
                if (locationHelper.hasLocationPermission()) {
                    fetchLocation()
                } else {
                    val perms = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    requestLocationPermission.launch(perms.toTypedArray())
                }
            }
            .show()
    }
}
