package com.example.attendanceapp // Change to your package name

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStartCheckIn = findViewById<Button>(R.id.btnStartCheckIn)
        btnStartCheckIn.setOnClickListener {
            // Start the flow by going to the QR scan screen
            startActivity(Intent(this, ScanQrActivity::class.java))
        }
    }
}