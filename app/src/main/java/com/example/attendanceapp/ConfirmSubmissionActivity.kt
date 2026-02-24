package com.example.attendanceapp

import android.Manifest
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.button.MaterialButton

class ConfirmSubmissionActivity : AppCompatActivity() {

    private var qrCode: String? = null
    private var photoUri: String? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var currentAccuracy: Float? = null

    private lateinit var locationHelper: LocationHelper
    private lateinit var tvLocationValue: TextView
    private lateinit var tvLocationAccuracy: TextView
    private lateinit var progressLocation: ProgressBar
    private lateinit var btnSubmit: MaterialButton
    private lateinit var lottieSuccess: LottieAnimationView

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
            tvLocationValue.text = "Location permission denied"
            tvLocationAccuracy.text = "Please enable location to record attendance"
            // Still allow submission without location
            btnSubmit.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_submission)

        // Initialize location helper
        locationHelper = LocationHelper(this)

        // Retrieve data from the previous activity
        qrCode = intent.getStringExtra("qr_code")
        photoUri = intent.getStringExtra("photo_uri")

        // Display QR code value
        val tvQrCodeValue = findViewById<TextView>(R.id.tvQrCodeValue)
        tvQrCodeValue.text = qrCode ?: "No QR code scanned"

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
        lottieSuccess = findViewById(R.id.lottieSuccess)
        lottieSuccess.setAnimation(R.raw.success_checkmark)

        // Submit button (starts disabled until location is fetched)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnSubmit.isEnabled = false
        btnSubmit.setOnClickListener {
            submitAttendance()
        }

        // Retake photo button
        val btnRetake = findViewById<MaterialButton>(R.id.btnRetake)
        btnRetake.setOnClickListener {
            finish()
        }

        // Start fetching location
        if (locationHelper.hasLocationPermission()) {
            fetchLocation()
        } else {
            requestLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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

                // Enable submit button now that location is ready
                btnSubmit.isEnabled = true
            }

            override fun onLocationError(message: String) {
                progressLocation.visibility = View.GONE
                tvLocationValue.text = "Location unavailable"
                tvLocationAccuracy.text = message
                // Still allow submission without location
                btnSubmit.isEnabled = true
            }
        })
    }

    private fun submitAttendance() {
        // Disable button to prevent double-submission
        btnSubmit.isEnabled = false

        // Show Lottie success animation
        lottieSuccess.visibility = View.VISIBLE
        lottieSuccess.playAnimation()

        val locationText = if (currentLatitude != null && currentLongitude != null) {
            "Location: $currentLatitude, $currentLongitude (±${currentAccuracy}m)"
        } else {
            "Location: not available"
        }

        // TODO: Implement actual submission logic (e.g., send data to a server)
        Toast.makeText(
            this,
            "Attendance submitted successfully!",
            Toast.LENGTH_SHORT
        ).show()

        // Wait for animation to finish, then close
        Handler(Looper.getMainLooper()).postDelayed({
            finishAffinity()
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationHelper.stopLocationUpdates()
    }
}
