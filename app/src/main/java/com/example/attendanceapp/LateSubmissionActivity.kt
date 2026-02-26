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

    private var attachedFileUri: Uri? = null

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
            attachedFileUri = it
            // Show just the filename segment or 'File attached'
            tvFileName.text = "File attached successfully"
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
                attachedFileUri?.let { uri ->
                    putExtra("attachment_uri", uri.toString())
                }
            }
            startActivity(intent)
            finish()
        }
    }
}
