package com.example.oauth2demo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.oauth2demo.databinding.ActivityMainBinding
import com.example.oauth2demo.ui.WelcomeActivity
import kotlinx.coroutines.launch
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager

/**
 * Main activity - shown after successful login.
 * Displays user information, access token, and device info.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var d2: D2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize DHIS2 SDK
        val d2Configuration = D2Configuration.builder()
            .context(applicationContext)
            .build()
        
        d2 = D2Manager.blockingInstantiateD2(d2Configuration)
            ?: throw IllegalStateException("Failed to initialize D2")

        setupUI()
        loadUserInfo()
    }

    private fun setupUI() {
        binding.retryButton.setOnClickListener {
            loadUserInfo()
        }

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

        // Fetch user info from SDK
        lifecycleScope.launch {
            try {
                // The SDK handles token refresh automatically
                val user = d2.userModule().user().blockingGet()
                
                hideLoading()
                
                if (user != null) {
                    binding.usernameTextView.text = "Username: ${user.username()}"
                    binding.displayNameTextView.text = "Display Name: ${user.displayName() ?: "N/A"}"
                    binding.emailTextView.text = "Email: ${user.email() ?: "N/A"}"
                } else {
                    showError("User info not available")
                }
            } catch (e: Exception) {
                hideLoading()
                showError("Failed to load user info: ${e.message}")
            }
        }
    }

    private fun displayDeviceInfo() {
        val clientId = d2.userModule().oauth2Handler().getClientId()

        binding.clientIdTextView.text = "Client ID: $clientId"
    }

    private fun logout() {
        lifecycleScope.launch {
            try {
                d2.userModule().oauth2Handler().blockingLogOut()
                Toast.makeText(this@MainActivity, "Logged out successfully", Toast.LENGTH_SHORT).show()
                
                // Navigate back to Welcome/Login screen
                val intent = Intent(this@MainActivity, WelcomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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