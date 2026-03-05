package com.example.attendanceapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LateSubmissionActivity : AppCompatActivity() {

    private var attachedFilePath: String? = null

    private lateinit var etReason: TextInputEditText
    private lateinit var tvFileName: TextView
    private lateinit var btnAttachFile: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var btnBack: ImageButton

    // Launcher for picking an optional file attachment
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getOriginalFileName(it)
            val cachedFile = copyFileToCache(it, fileName)
            if (cachedFile != null) {
                attachedFilePath = cachedFile.absolutePath
                tvFileName.text = fileName ?: "File attached"
            } else {
                Toast.makeText(this, "Failed to load attached file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getOriginalFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (result == null) {
            result = uri.path?.let { java.io.File(it).name }
        }
        return result
    }

    private fun copyFileToCache(uri: Uri, fileName: String?): java.io.File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val safeName = fileName?.replace(Regex("[^a-zA-Z0-9.-]"), "_") ?: "attachment_${System.currentTimeMillis()}.tmp"
            val tempFile = java.io.File(cacheDir, safeName)
            tempFile.outputStream().use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_late_submission)

        etReason = findViewById(R.id.etReason)
        tvFileName = findViewById(R.id.tvFileName)
        btnAttachFile = findViewById(R.id.btnAttachFile)
        btnNext = findViewById(R.id.btnNext)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            finish()
        }

        btnAttachFile.setOnClickListener {
            // Allows user to pick images or PDF
            pickFileLauncher.launch("*/*")
        }

        btnNext.setOnClickListener {
            val reason = etReason.text.toString().trim()
            if (reason.isEmpty()) {
                Toast.makeText(this, "Please provide a reason for the late attendance.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Navigate to next screen, which is CapturePhotoActivity, passing the late submission info
            val intent = Intent(this, CapturePhotoActivity::class.java).apply {
                putExtra("clock_type", "late")
                putExtra("late_reason", reason)
                attachedFilePath?.let { path ->
                    putExtra("attachment_path", path)
                }
            }
            startActivity(intent)
            finish()
        }
    }
}
