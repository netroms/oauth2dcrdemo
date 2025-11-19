package com.example.oauth2demo.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.oauth2demo.databinding.ActivityEnrollmentBinding
import com.example.oauth2demo.network.models.ApiResult
import com.example.oauth2demo.oauth.DCRManager
import kotlinx.coroutines.launch

/**
 * Enrollment callback activity - captures IAT from deep link redirect.
 * This activity is triggered when DHIS2 redirects back to dhis2oauth://oauth?iat=...&state=...
 * 
 * Flow:
 * 1. Extract IAT and state from intent
 * 2. Validate state (CSRF protection)
 * 3. Call DCRManager to register device with IAT
 * 4. Navigate to Login screen on success
 */
class EnrollmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnrollmentBinding
    private lateinit var dcrManager: DCRManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnrollmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dcrManager = DCRManager(this)

        // Process the deep link
        processEnrollmentCallback()
    }

    private fun processEnrollmentCallback() {
        val uri = intent.data
        if (uri == null) {
            showError("Invalid enrollment callback")
            navigateBack()
            return
        }

        // Extract parameters from redirect URL: dhis2oauth://oauth?iat=...&state=...
        val iat = uri.getQueryParameter("iat")
        val state = uri.getQueryParameter("state")

        if (iat.isNullOrBlank()) {
            showError("No IAT received from server")
            navigateBack()
            return
        }

        // Validate state (CSRF protection)
        val sharedPrefs = getSharedPreferences("temp_prefs", MODE_PRIVATE)
        val expectedState = sharedPrefs.getString("pending_state", null)
        val serverUrl = sharedPrefs.getString("pending_server_url", null)

        if (state != expectedState) {
            showError("Invalid state parameter (CSRF check failed)")
            navigateBack()
            return
        }

        if (serverUrl.isNullOrBlank()) {
            showError("Server URL not found")
            navigateBack()
            return
        }

        // Clear temporary data
        sharedPrefs.edit().clear().apply()

        // Register device with DCR
        registerDevice(serverUrl, iat)
    }

    private fun registerDevice(serverUrl: String, iat: String) {
        binding.statusTextView.text = "Registering device with DHIS2..."

        lifecycleScope.launch {
            when (val result = dcrManager.registerDevice(serverUrl, iat)) {
                is ApiResult.Success -> {
                    showSuccess("Device registered successfully!")
                    
                    // Navigate to Login screen
                    val intent = Intent(this@EnrollmentActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
                is ApiResult.Error -> {
                    showError("Registration failed: ${result.message}")
                    navigateBack()
                }
                is ApiResult.NetworkError -> {
                    showError("Network error: ${result.exception.message}")
                    navigateBack()
                }
            }
        }
    }

    private fun navigateBack() {
        // Wait a moment to show error, then go back to Welcome
        binding.root.postDelayed({
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }, 2000)
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.statusTextView.text = message
    }
}

