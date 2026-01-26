package com.example.oauth2demo.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.example.oauth2demo.MainActivity
import com.example.oauth2demo.databinding.ActivityWelcomeBinding
import com.example.oauth2demo.network.DHIS2ApiClient
import com.example.oauth2demo.network.models.ApiResult
import kotlinx.coroutines.launch
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager

/**
 * Welcome screen - first screen shown to user.
 * Allows user to enter DHIS2 server URL and start device registration.
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var d2: D2
    private lateinit var apiClient: DHIS2ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize DHIS2 SDK
        val d2Configuration = D2Configuration.builder()
            .context(applicationContext)
            .build()
        
        d2 = D2Manager.blockingInstantiateD2(d2Configuration) 
            ?: throw IllegalStateException("Failed to initialize D2")
        apiClient = DHIS2ApiClient()

        setupUI()
        checkExistingState()
    }

    private fun setupUI() {
        // Pre-fill with example server URL
        binding.serverUrlEditText.setText("https://play.dhis2.org/dev")

        binding.registerButton.setOnClickListener {
            val serverUrl = binding.serverUrlEditText.text.toString().trim()
            if (serverUrl.isBlank()) {
                binding.serverUrlInputLayout.error = "Please enter a server URL"
                return@setOnClickListener
            }
            binding.serverUrlInputLayout.error = null

            // Validate URL format
            val cleanUrl = serverUrl.removeSuffix("/")
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                binding.serverUrlInputLayout.error = "URL must start with http:// or https://"
                return@setOnClickListener
            }

            startDeviceRegistration(cleanUrl)
        }

        binding.loginButton.setOnClickListener {
            navigateToLogin()
        }

        binding.resetButton.setOnClickListener {
            showResetConfirmation()
        }
    }

    private fun checkExistingState() {
        // Check if already registered
        if (d2.userModule().oauth2Handler().isDeviceRegistered()) {
            showRegisteredState()
            return
        }

        // Check if already logged in
        if (d2.userModule().oauth2Handler().isLoggedIn()) {
            navigateToMain()
            return
        }
    }

    private fun startDeviceRegistration(serverUrl: String) {
        // First test server connection
        showLoading("Checking server connection...")

        lifecycleScope.launch {
            when (apiClient.testServerConnection(serverUrl)) {
                is ApiResult.Success -> {
                    // Server is reachable, proceed with enrollment
                    openEnrollmentInBrowser(serverUrl)
                }
                is ApiResult.Error -> {
                    hideLoading()
                    showError("Cannot connect to server. Please check the URL.")
                }
                is ApiResult.NetworkError -> {
                    hideLoading()
                    showError("Network error. Please check your connection.")
                }
            }
        }
    }

    private fun openEnrollmentInBrowser(serverUrl: String) {
        // Store server URL temporarily for callback
        getSharedPreferences("temp_prefs", MODE_PRIVATE)
            .edit()
            .putString("pending_server_url", serverUrl)
            .apply()

        // Build enrollment URL using SDK
        val enrollmentUrl = d2.userModule().oauth2Handler()
            .blockingBuildEnrollmentUrl(serverUrl)
        
        hideLoading()
        
        // Open enrollment URL in Custom Tab (browser)
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        
        customTabsIntent.launchUrl(this, Uri.parse(enrollmentUrl))
        
        showStatus("Please login in the browser...")
    }

    private fun showRegisteredState() {
        val clientId = d2.userModule().oauth2Handler().getClientId()
        binding.serverUrlEditText.isEnabled = false
        binding.registerButton.visibility = View.GONE
        binding.loginButton.visibility = View.VISIBLE
        showStatus("Device registered! Client ID: $clientId")
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showLoading(message: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.statusTextView.visibility = View.VISIBLE
        binding.statusTextView.text = message
        binding.registerButton.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.statusTextView.visibility = View.GONE
        binding.registerButton.isEnabled = true
    }

    private fun showStatus(message: String) {
        binding.statusTextView.visibility = View.VISIBLE
        binding.statusTextView.text = message
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.statusTextView.visibility = View.VISIBLE
        binding.statusTextView.text = message
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Reset Registration")
            .setMessage("This will delete all registration data and keys. You will need to register again. Continue?")
            .setPositiveButton("Reset") { _, _ ->
                resetRegistration()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetRegistration() {
        d2.userModule().oauth2Handler().resetRegistration()
        
        Toast.makeText(this, "Registration reset complete", Toast.LENGTH_SHORT).show()
        
        // Reset UI to initial state
        binding.serverUrlEditText.isEnabled = true
        binding.serverUrlEditText.setText("https://play.dhis2.org/dev")
        binding.registerButton.visibility = View.VISIBLE
        binding.loginButton.visibility = View.GONE
        binding.statusTextView.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        // Check if registration completed while in browser
        if (d2.userModule().oauth2Handler().isDeviceRegistered()) {
            showRegisteredState()
        }
    }
}

