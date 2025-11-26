package com.example.oauth2demo.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.example.oauth2demo.MainActivity
import com.example.oauth2demo.databinding.ActivityLoginBinding
import com.example.oauth2demo.network.models.ApiResult
import com.example.oauth2demo.oauth.JWTHelper
import com.example.oauth2demo.oauth.OAuth2Manager
import kotlinx.coroutines.launch

/**
 * Login activity - handles OAuth2 authorization code flow with PKCE.
 * 
 * Flow:
 * 1. User clicks "Login with DHIS2"
 * 2. Generate PKCE code_verifier and code_challenge
 * 3. Open browser to /oauth2/authorize with code_challenge
 * 4. User authenticates
 * 5. Browser redirects to dhis2oauth://oauth?code=...&state=...
 * 6. OAuthCallbackActivity captures redirect and forwards code to this activity
 * 7. Exchange code for token using private_key_jwt + code_verifier (PKCE)
 * 8. Navigate to MainActivity on success
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var oauth2Manager: OAuth2Manager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        oauth2Manager = OAuth2Manager(this)

        setupUI()
        
        // Check if we received auth code from OAuthCallbackActivity
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun setupUI() {
        // Display device info
        val serverUrl = oauth2Manager.getServerUrl()
        val clientId = oauth2Manager.getClientId()

        binding.serverUrlTextView.text = "Server: $serverUrl"
        binding.clientIdTextView.text = "Client ID: ${clientId?.take(16)}..."

        binding.loginButton.setOnClickListener {
            startOAuthFlow()
        }
    }

    /**
     * Handle intent - check for authorization code from OAuthCallbackActivity.
     */
    private fun handleIntent(intent: Intent) {
        val authCode = intent.getStringExtra(OAuthCallbackActivity.EXTRA_AUTH_CODE)
        if (!authCode.isNullOrBlank()) {
            // Received auth code from callback router, process it
            processAuthCode(authCode)
        }
    }

    /**
     * Start OAuth2 authorization flow with PKCE.
     */
    private fun startOAuthFlow() {
        val serverUrl = oauth2Manager.getServerUrl()
        val clientId = oauth2Manager.getClientId()

        if (serverUrl.isNullOrBlank() || clientId.isNullOrBlank()) {
            showError("Registration data not found. Please register device first.")
            return
        }

        // Generate PKCE parameters
        val codeVerifier = JWTHelper.generateCodeVerifier()
        val codeChallenge = JWTHelper.generateCodeChallenge(codeVerifier)
        
        // Generate state for CSRF protection
        val state = JWTHelper.generateState()
        
        // Save state and code_verifier for validation and token exchange
        getSharedPreferences("temp_prefs", MODE_PRIVATE)
            .edit()
            .putString("oauth_state", state)
            .putString("oauth_code_verifier", codeVerifier)
            .apply()

        // Build authorization URL with PKCE
        val authUrl = oauth2Manager.buildAuthorizationUrl(
            serverUrl = serverUrl,
            clientId = clientId,
            state = state,
            codeChallenge = codeChallenge
        )

        // Open in Custom Tab
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(this, Uri.parse(authUrl))

        showStatus("Please login in the browser...")
    }

    /**
     * Process authorization code received from OAuthCallbackActivity.
     */
    private fun processAuthCode(code: String) {
        showLoading("Processing login...")

        // Get code_verifier from temp storage
        val sharedPrefs = getSharedPreferences("temp_prefs", MODE_PRIVATE)
        val codeVerifier = sharedPrefs.getString("oauth_code_verifier", null)

        if (codeVerifier.isNullOrBlank()) {
            showError("Code verifier not found. Please try logging in again.")
            hideLoading()
            return
        }

        // Clear temporary OAuth data
        sharedPrefs.edit()
            .remove("oauth_state")
            .remove("oauth_code_verifier")
            .apply()

        // Exchange code for token with PKCE
        exchangeCodeForToken(code, codeVerifier)
    }

    /**
     * Exchange authorization code for access token using private_key_jwt and PKCE.
     */
    private fun exchangeCodeForToken(code: String, codeVerifier: String) {
        binding.statusTextView.text = "Exchanging code for token..."

        lifecycleScope.launch {
            when (val result = oauth2Manager.exchangeCodeForToken(code, codeVerifier)) {
                is ApiResult.Success -> {
                    showSuccess("Login successful!")
                    
                    // Navigate to main activity
                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
                is ApiResult.Error -> {
                    hideLoading()
                    showError("Token exchange failed: ${result.message}")
                }
                is ApiResult.NetworkError -> {
                    hideLoading()
                    showError("Network error: ${result.exception.message}")
                }
            }
        }
    }

    private fun showLoading(message: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.statusTextView.visibility = View.VISIBLE
        binding.statusTextView.text = message
        binding.loginButton.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.statusTextView.visibility = View.GONE
        binding.loginButton.isEnabled = true
    }

    private fun showStatus(message: String) {
        binding.statusTextView.visibility = View.VISIBLE
        binding.statusTextView.text = message
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.statusTextView.visibility = View.VISIBLE
        binding.statusTextView.text = message
    }
}
