package com.example.attendanceapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.budiyev.android.codescanner.AutoFocusMode
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ErrorCallback
import com.budiyev.android.codescanner.ScanMode
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

class ScanQrActivity : AppCompatActivity() {
    private lateinit var codeScanner: CodeScanner
    private val CAMERA_REQUEST_CODE = 101

    // Gallery image picker
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { decodeQrFromImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_qr)

        // Bring overlay buttons to front so they receive touches above the scanner
        findViewById<LinearLayout>(R.id.topBar).bringToFront()
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnScanFromGallery).bringToFront()

        // Back button
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        // Flash toggle button
        val btnFlash = findViewById<ImageButton>(R.id.btnFlash)
        btnFlash.setOnClickListener {
            if (::codeScanner.isInitialized) {
                codeScanner.isFlashEnabled = !codeScanner.isFlashEnabled
                btnFlash.setImageResource(
                    if (codeScanner.isFlashEnabled) R.drawable.ic_flash_on
                    else R.drawable.ic_flash_off
                )
            }
        }

        // Scan from gallery button
        val btnScanFromGallery = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnScanFromGallery)
        btnScanFromGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        if (checkCameraPermission()) {
            setupScanner()
        } else {
            requestCameraPermission()
        }
    }

    private fun setupScanner() {
        val scannerView = findViewById<CodeScannerView>(R.id.scanner_view)
        codeScanner = CodeScanner(this, scannerView)

        // Parameters (default values)
        codeScanner.camera = CodeScanner.CAMERA_BACK
        codeScanner.formats = CodeScanner.ALL_FORMATS
        codeScanner.autoFocusMode = AutoFocusMode.SAFE
        codeScanner.scanMode = ScanMode.SINGLE
        codeScanner.isAutoFocusEnabled = true
        codeScanner.isFlashEnabled = false

        // Callback when a code is scanned successfully
        codeScanner.decodeCallback = DecodeCallback {
            runOnUiThread {
                Toast.makeText(this, "Scan result: ${it.text}", Toast.LENGTH_SHORT).show()
                // Navigate to photo capture, passing the scanned QR code
                val intent = Intent(this, CapturePhotoActivity::class.java)
                intent.putExtra("qr_code", it.text)
                startActivity(intent)
                finish()
            }
        }

        // Callback for scanner errors
        codeScanner.errorCallback = ErrorCallback {
            runOnUiThread {
                Toast.makeText(this, "Camera initialization error: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun decodeQrFromImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show()
                return
            }

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = MultiFormatReader().decode(binaryBitmap)

            Toast.makeText(this, "QR result: ${result.text}", Toast.LENGTH_SHORT).show()
            // Navigate to photo capture, passing the scanned QR code
            val intent = Intent(this, CapturePhotoActivity::class.java)
            intent.putExtra("qr_code", result.text)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "No QR code found in the selected image.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::codeScanner.isInitialized && checkCameraPermission()) {
            codeScanner.startPreview()
        }
        // Start scan-line animation
        val scanLine = findViewById<android.view.View>(R.id.scanLine)
        val anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.scan_line)
        scanLine.startAnimation(anim)
    }

    override fun onPause() {
        if (::codeScanner.isInitialized) {
            codeScanner.releaseResources()
        }
        val scanLine = findViewById<android.view.View>(R.id.scanLine)
        scanLine.clearAnimation()
        super.onPause()
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupScanner()
                codeScanner.startPreview()
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR codes.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}