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
import kotlinx.coroutines.launch
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager
import org.hisp.dhis.android.core.user.oauth2.OAuth2Config

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
    private lateinit var d2: D2
    
    companion object {
        private const val LOGIN_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize DHIS2 SDK
        val d2Configuration = D2Configuration.builder()
            .context(applicationContext)
            .build()
        
        d2 = D2Manager.blockingInstantiateD2(d2Configuration)
            ?: throw IllegalStateException("Failed to initialize D2")

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
        val clientId = d2.userModule().oauth2Handler().getClientId()

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
     * Start OAuth2 authorization flow using SDK.
     * The SDK handles PKCE and state generation internally.
     */
    private fun startOAuthFlow() {
        if (!d2.userModule().oauth2Handler().isDeviceRegistered()) {
            showError("Registration data not found. Please register device first.")
            return
        }

        // Get server URL from temp storage (set during enrollment)
        val serverUrl = getSharedPreferences("temp_prefs", MODE_PRIVATE)
            .getString("pending_server_url", null)
        
        if (serverUrl.isNullOrBlank()) {
            showError("Server URL not found. Please restart enrollment.")
            return
        }
        
        lifecycleScope.launch {
            try {
                val config = OAuth2Config(serverUrl = serverUrl)
                
                // SDK generates PKCE parameters and builds authorization URL internally
                val intentWithRequestCode = d2.userModule().oauth2Handler()
                    .blockingLogIn(config)
                
                // Launch browser with the OAuth flow
                @Suppress("DEPRECATION")
                startActivityForResult(
                    intentWithRequestCode.intent,
                    intentWithRequestCode.requestCode
                )
                
                showStatus("Please login in the browser...")
            } catch (e: Exception) {
                showError("Failed to start login: ${e.message}")
            }
        }
    }

    /**
     * Process authorization code received from OAuthCallbackActivity.
     */
    private fun processAuthCode(code: String) {
        showLoading("Processing login...")

        lifecycleScope.launch {
            try {
                val serverUrl = getSharedPreferences("temp_prefs", MODE_PRIVATE)
                    .getString("pending_server_url", null)
                
                if (serverUrl.isNullOrBlank()) {
                    showError("Server URL not found.")
                    hideLoading()
                    return@launch
                }
                
                // Handle login response using SDK
                val user = d2.userModule().oauth2Handler()
                    .blockingHandleLogInResponse(serverUrl, code)
                
                showSuccess("Login successful! Welcome ${user.username()}")
                
                // Navigate to main activity
                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                hideLoading()
                showError("Login failed: ${e.message}")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Handle result from OAuth browser flow
        if (data != null) {
            val code = data.data?.getQueryParameter("code")
            if (code != null) {
                processAuthCode(code)
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
