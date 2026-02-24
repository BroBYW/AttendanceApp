package com.example.attendanceapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CapturePhotoActivity : AppCompatActivity() {

    private lateinit var photoUri: Uri
    private var qrCode: String? = null

    // Register the activity result for taking a picture
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess) {
            // Photo taken successfully, navigate to confirmation
            val intent = Intent(this, ConfirmSubmissionActivity::class.java)
            intent.putExtra("qr_code", qrCode)
            intent.putExtra("photo_uri", photoUri.toString())
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Photo capture failed.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture_photo)

        // Get the QR code passed from the previous activity
        qrCode = intent.getStringExtra("qr_code")

        val btnTakePhoto = findViewById<Button>(R.id.btnTakePhoto)
        btnTakePhoto.setOnClickListener {
            takePhoto()
        }
    }

    private fun takePhoto() {
        // Create a temporary file to store the photo
        val photoFile = createImageFile()
        // Get a URI for the file using FileProvider (requires setup in AndroidManifest)
        photoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        // Launch the camera
        takePicture.launch(photoUri)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
    }
}