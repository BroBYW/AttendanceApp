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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.attendanceapp.service.AttendanceSyncScheduler

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

        // Fix status bar color after splash screen
        window.statusBarColor = getColor(R.color.primary)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Auto-login: if "Remember Me" was checked and a valid session token exists, skip login
        val rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)
        if (rememberMe) {
            val sessionManager = com.example.attendanceapp.utils.SessionManager(this)
            val token = sessionManager.fetchAuthToken()
            if (!token.isNullOrEmpty()) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("username", sessionManager.getUserName() ?: "")
                }
                startActivity(intent)
                finish()
                return
            }
        }

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
        
        // Disable autofill to prevent Android Emulator clipboard access crashes
        etUsername.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        etPassword.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO

        btnLogin.setOnClickListener {
            if (validateForm()) {
                performLogin()
            }
        }
    }

    private fun performLogin() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()

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

        lifecycleScope.launch {
            try {
                val apiService = com.example.attendanceapp.data.network.RetrofitClient.getApiService(this@LoginActivity)
                val request = com.example.attendanceapp.data.network.dto.LoginRequest(username, password)
                val response = apiService.login(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val loginData = response.body()?.data
                    loginData?.let {
                        val sessionManager = com.example.attendanceapp.utils.SessionManager(this@LoginActivity)
                        sessionManager.saveAuthToken(it.accessToken)
                        sessionManager.saveRefreshToken(it.refreshToken)
                        sessionManager.saveUserDetails(
                            it.user.id,
                            it.user.name,
                            it.user.employeeId,
                            it.user.role,
                            it.user.assignedOfficeAreaIds
                        )
                    }
                    AttendanceSyncScheduler.schedulePeriodic(this@LoginActivity)
                    AttendanceSyncScheduler.enqueueOneShot(this@LoginActivity)

                    android.widget.Toast.makeText(this@LoginActivity, "Login Successful", android.widget.Toast.LENGTH_SHORT).show()
                    
                    val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                        putExtra("username", loginData?.user?.name ?: username)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    finish()
                } else {
                    showError("Login failed: ${response.message()}")
                }
            } catch (e: Exception) {
                showError("Network error: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        btnLogin.text = "Login"
        btnLogin.isEnabled = true
        progressLogin.visibility = View.GONE
        etUsername.isEnabled = true
        etPassword.isEnabled = true
        cbRememberMe.isEnabled = true
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
