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
 * OAuth Callback Activity - central router for all OAuth/DCR deep link callbacks.
 * 
 * This activity is triggered when DHIS2 redirects back to dhis2oauth://oauth with:
 * - Enrollment callback: ?iat=...&state=... (device registration)
 * - Login callback: ?code=...&state=... (OAuth authorization code)
 * - Error callback: ?error=...&error_description=... (OAuth error)
 * 
 * The activity inspects the callback parameters and routes to the appropriate handler.
 */
class OAuthCallbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnrollmentBinding
    private lateinit var dcrManager: DCRManager

    companion object {
        // Intent extras for forwarding OAuth code to LoginActivity
        const val EXTRA_AUTH_CODE = "auth_code"
        const val EXTRA_STATE = "state"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnrollmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dcrManager = DCRManager(this)

        // Route the callback based on parameters
        routeCallback()
    }

    /**
     * Inspect callback parameters and route to appropriate handler.
     */
    private fun routeCallback() {
        val uri = intent.data
        if (uri == null) {
            showError("Invalid callback - no data received")
            navigateToWelcome()
            return
        }

        // Check for error first
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val errorDescription = uri.getQueryParameter("error_description")
            showError("OAuth error: ${errorDescription ?: error}")
            navigateToWelcome()
            return
        }

        // Check if this is an enrollment callback (has IAT)
        val iat = uri.getQueryParameter("iat")
        if (!iat.isNullOrBlank()) {
            processEnrollmentCallback(iat, uri.getQueryParameter("state"))
            return
        }

        // Check if this is a login callback (has authorization code)
        val code = uri.getQueryParameter("code")
        if (!code.isNullOrBlank()) {
            processLoginCallback(code, uri.getQueryParameter("state"))
            return
        }

        // Unknown callback type
        showError("Unknown callback type - missing iat or code parameter")
        navigateToWelcome()
    }

    /**
     * Process enrollment callback with IAT (Initial Access Token).
     * Validates state and registers device with DHIS2.
     */
    private fun processEnrollmentCallback(iat: String, state: String?) {
        binding.statusTextView.text = "Processing enrollment..."

        // Validate state (CSRF protection)
        val sharedPrefs = getSharedPreferences("temp_prefs", MODE_PRIVATE)
        val expectedState = sharedPrefs.getString("pending_state", null)
        val serverUrl = sharedPrefs.getString("pending_server_url", null)

        if (state != expectedState) {
            showError("Invalid state parameter (CSRF check failed)")
            navigateToWelcome()
            return
        }

        if (serverUrl.isNullOrBlank()) {
            showError("Server URL not found")
            navigateToWelcome()
            return
        }

        // Clear enrollment-related temporary data but keep OAuth data if present
        sharedPrefs.edit()
            .remove("pending_state")
            .remove("pending_server_url")
            .apply()

        // Register device with DCR
        registerDevice(serverUrl, iat)
    }

    /**
     * Process login callback with authorization code.
     * Validates state and forwards to LoginActivity for token exchange.
     */
    private fun processLoginCallback(code: String, state: String?) {
        binding.statusTextView.text = "Processing login..."

        // Validate state (CSRF protection)
        val sharedPrefs = getSharedPreferences("temp_prefs", MODE_PRIVATE)
        val expectedState = sharedPrefs.getString("oauth_state", null)

        if (state != expectedState) {
            showError("Invalid state parameter (CSRF check failed)")
            navigateToLogin()
            return
        }

        // Forward to LoginActivity with the code
        // The code_verifier is still in temp_prefs, LoginActivity will retrieve it
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_AUTH_CODE, code)
            putExtra(EXTRA_STATE, state)
        }
        startActivity(intent)
        finish()
    }

    private fun registerDevice(serverUrl: String, iat: String) {
        binding.statusTextView.text = "Registering device with DHIS2..."

        lifecycleScope.launch {
            when (val result = dcrManager.registerDevice(serverUrl, iat)) {
                is ApiResult.Success -> {
                    showSuccess("Device registered successfully!")
                    
                    // Navigate to Login screen
                    val intent = Intent(this@OAuthCallbackActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
                is ApiResult.Error -> {
                    showError("Registration failed: ${result.message}")
                    navigateToWelcome()
                }
                is ApiResult.NetworkError -> {
                    showError("Network error: ${result.exception.message}")
                    navigateToWelcome()
                }
            }
        }
    }

    private fun navigateToWelcome() {
        // Wait a moment to show error, then go back to Welcome
        binding.root.postDelayed({
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }, 2000)
    }

    private fun navigateToLogin() {
        // Wait a moment to show error, then go back to Login
        binding.root.postDelayed({
            val intent = Intent(this, LoginActivity::class.java)
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

