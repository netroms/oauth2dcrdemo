package com.example.oauth2demo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.oauth2demo.databinding.ActivityMainBinding
import com.example.oauth2demo.network.models.ApiResult
import com.example.oauth2demo.oauth.DCRManager
import com.example.oauth2demo.oauth.OAuth2Manager
import com.example.oauth2demo.ui.WelcomeActivity
import kotlinx.coroutines.launch

/**
 * Main activity - shown after successful login.
 * Displays user information, access token, and device info.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var oauth2Manager: OAuth2Manager
    private lateinit var dcrManager: DCRManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        oauth2Manager = OAuth2Manager(this)
        dcrManager = DCRManager(this)

        setupUI()
        loadUserInfo()
    }

    private fun setupUI() {
        binding.logoutButton.setOnClickListener {
            logout()
        }

        binding.resetButton.setOnClickListener {
            showResetConfirmation()
        }
    }

    private fun loadUserInfo() {
        showLoading()

        // Display device info immediately
        displayDeviceInfo()

        // Fetch user info from API
        lifecycleScope.launch {
            when (val result = oauth2Manager.getUserInfo()) {
                is ApiResult.Success -> {
                    hideLoading()
                    displayUserInfo(result.data)
                }
                is ApiResult.Error -> {
                    hideLoading()
                    showError("Failed to load user info: ${result.message}")
                }
                is ApiResult.NetworkError -> {
                    hideLoading()
                    showError("Network error: ${result.exception.message}")
                }
            }
        }
    }

    private fun displayUserInfo(userInfo: com.example.oauth2demo.network.models.UserInfo) {
        binding.usernameTextView.text = "Username: ${userInfo.username}"
        binding.displayNameTextView.text = "Display Name: ${userInfo.displayName ?: "N/A"}"
        binding.emailTextView.text = "Email: ${userInfo.email ?: "N/A"}"
    }

    private fun displayDeviceInfo() {
        val serverUrl = oauth2Manager.getServerUrl()
        val clientId = oauth2Manager.getClientId()
        val accessToken = oauth2Manager.getAccessToken()

        binding.serverUrlTextView.text = "Server: $serverUrl"
        binding.clientIdTextView.text = "Client ID: $clientId"

        // Truncate access token for display
        if (!accessToken.isNullOrBlank()) {
            val truncatedToken = if (accessToken.length > 80) {
                accessToken.take(77) + "..."
            } else {
                accessToken
            }
            binding.accessTokenTextView.text = truncatedToken
        }
    }

    private fun logout() {
        oauth2Manager.logout()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        
        // Navigate back to Welcome/Login screen
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
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
        dcrManager.resetRegistration()
        oauth2Manager.logout()
        
        Toast.makeText(this, "Registration reset complete", Toast.LENGTH_SHORT).show()
        
        // Navigate back to Welcome screen
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}