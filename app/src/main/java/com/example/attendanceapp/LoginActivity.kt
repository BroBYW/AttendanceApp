package com.example.attendanceapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {

    private lateinit var tilUsername: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progressLogin: ProgressBar
    private lateinit var cbRememberMe: MaterialCheckBox
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "attendance_prefs"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_USERNAME = "saved_username"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        tilUsername = findViewById(R.id.tilUsername)
        tilPassword = findViewById(R.id.tilPassword)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressLogin = findViewById(R.id.progressLogin)
        cbRememberMe = findViewById(R.id.cbRememberMe)

        // Show app version
        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = "v${pInfo.versionName} (Build ${pInfo.longVersionCode})"
        } catch (_: Exception) {
            tvVersion.text = "v1.0"
        }

        // Load saved username if Remember Me was checked
        val rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)
        if (rememberMe) {
            etUsername.setText(prefs.getString(KEY_USERNAME, ""))
            cbRememberMe.isChecked = true
        }

        // Clear errors when user starts typing
        etUsername.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) tilUsername.error = null
        }
        etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) tilPassword.error = null
        }

        btnLogin.setOnClickListener {
            if (validateForm()) {
                performLogin()
            }
        }
    }

    private fun performLogin() {
        val username = etUsername.text.toString().trim()

        // Save or clear Remember Me preference
        prefs.edit().apply {
            putBoolean(KEY_REMEMBER_ME, cbRememberMe.isChecked)
            if (cbRememberMe.isChecked) {
                putString(KEY_USERNAME, username)
            } else {
                remove(KEY_USERNAME)
            }
            apply()
        }

        // Show loading state
        btnLogin.text = ""
        btnLogin.isEnabled = false
        progressLogin.visibility = View.VISIBLE
        etUsername.isEnabled = false
        etPassword.isEnabled = false
        cbRememberMe.isEnabled = false

        // Simulate authentication delay
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("username", username)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }, 1500)
    }

    private fun validateForm(): Boolean {
        var isValid = true
        val shakeAnim = AnimationUtils.loadAnimation(this, R.anim.shake)

        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (username.isEmpty()) {
            tilUsername.error = "Username is required"
            tilUsername.startAnimation(shakeAnim)
            isValid = false
        }

        if (password.isEmpty()) {
            tilPassword.error = "Password is required"
            tilPassword.startAnimation(shakeAnim)
            isValid = false
        } else if (password.length < 6) {
            tilPassword.error = "Password must be at least 6 characters"
            tilPassword.startAnimation(shakeAnim)
            isValid = false
        }

        return isValid
    }
}
