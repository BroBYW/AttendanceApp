package com.example.attendanceapp

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SuccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_success)

        // Animate the success icon with a bouncy pop-in
        val ivSuccessIcon = findViewById<ImageView>(R.id.ivSuccessIcon)
        val popIn = AnimationUtils.loadAnimation(this, R.anim.pop_in)
        ivSuccessIcon.startAnimation(popIn)

        // Display submission time
        val tvSuccessTime = findViewById<TextView>(R.id.tvSuccessTime)
        val timeFormat = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.ENGLISH)
        timeFormat.timeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur")
        tvSuccessTime.text = timeFormat.format(Date())

        // Display location
        val tvSuccessLocation = findViewById<TextView>(R.id.tvSuccessLocation)
        val location = intent.getStringExtra("location") ?: "Recorded"
        tvSuccessLocation.text = location

        // Back to Home button
        val btnBackToHome = findViewById<MaterialButton>(R.id.btnBackToHome)
        btnBackToHome.setOnClickListener {
            navigateHome()
        }
    }

    private fun navigateHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    // Prevent back to submission page
    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        navigateHome()
    }
}
