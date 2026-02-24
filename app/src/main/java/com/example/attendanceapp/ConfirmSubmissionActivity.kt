package com.example.attendanceapp

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ConfirmSubmissionActivity : AppCompatActivity() {

    private var qrCode: String? = null
    private var photoUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_submission)

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

        // Submit button
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        btnSubmit.setOnClickListener {
            submitAttendance()
        }

        // Retake photo button
        val btnRetake = findViewById<Button>(R.id.btnRetake)
        btnRetake.setOnClickListener {
            finish() // Go back to CapturePhotoActivity
        }
    }

    private fun submitAttendance() {
        // TODO: Implement actual submission logic (e.g., send data to a server)
        Toast.makeText(this, "Attendance submitted successfully!", Toast.LENGTH_SHORT).show()
        // Return to the main screen
        finishAffinity()
    }
}
