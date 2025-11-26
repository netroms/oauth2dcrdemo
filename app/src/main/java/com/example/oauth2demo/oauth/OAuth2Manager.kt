package com.example.oauth2demo.oauth

import android.content.Context
import android.net.Uri
import com.example.oauth2demo.network.DHIS2ApiClient
import com.example.oauth2demo.network.models.ApiResult
import com.example.oauth2demo.network.models.UserInfo
import com.example.oauth2demo.storage.KeyStoreManager
import com.example.oauth2demo.storage.SecureStorage

/**
 * Manager for OAuth2 authorization code flow and token management.
 * Handles login, token exchange, token refresh, and logout.
 */
class OAuth2Manager(private val context: Context) {

    private val storage = SecureStorage(context)
    private val keyStoreManager = KeyStoreManager()
    private val apiClient = DHIS2ApiClient()

    companion object {
        const val REDIRECT_URI = "dhis2oauth://oauth"
    }

    /**
     * Build the OAuth2 authorization URL for user login with PKCE support.
     * Opens in browser for user authentication.
     * 
     * @param serverUrl The DHIS2 server URL
     * @param clientId The OAuth2 client ID
     * @param state Random state for CSRF protection
     * @param codeChallenge PKCE code challenge (Base64URL encoded SHA256 of code verifier)
     */
    fun buildAuthorizationUrl(
        serverUrl: String,
        clientId: String,
        state: String,
        codeChallenge: String
    ): String {
        return Uri.parse("$serverUrl/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "openid profile")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    /**
     * Exchange authorization code for access token using private_key_jwt with PKCE.
     * 
     * @param authorizationCode Code received from OAuth redirect
     * @param codeVerifier The PKCE code verifier used when initiating the auth flow
     * @return Result with success/error
     */
    suspend fun exchangeCodeForToken(
        authorizationCode: String,
        codeVerifier: String
    ): ApiResult<Unit> {
        val serverUrl = storage.serverUrl
            ?: return ApiResult.Error("Server URL not found")
        val clientId = storage.clientId
            ?: return ApiResult.Error("Client ID not found")
        val keyId = storage.keyId
            ?: return ApiResult.Error("Key ID not found")

        try {
            // Get private key from Android KeyStore for signing
            val privateKey = keyStoreManager.getPrivateKey(keyId)
                ?: return ApiResult.Error("Private key not found for keyId: $keyId")

            // Create client assertion (private_key_jwt)
            val tokenEndpoint = "$serverUrl/oauth2/token"
            val clientAssertion = JWTHelper.createClientAssertion(
                clientId = clientId,
                tokenEndpoint = tokenEndpoint,
                privateKey = privateKey,
                keyId = keyId
            )

            // Exchange code for tokens with PKCE code_verifier
            return when (val result = apiClient.exchangeCodeForToken(
                serverUrl = serverUrl,
                clientId = clientId,
                authorizationCode = authorizationCode,
                redirectUri = REDIRECT_URI,
                clientAssertion = clientAssertion,
                codeVerifier = codeVerifier
            )) {
                is ApiResult.Success -> {
                    // Store tokens
                    storage.accessToken = result.data.accessToken
                    storage.refreshToken = result.data.refreshToken
                    storage.tokenExpiresAt = System.currentTimeMillis() + 
                                             (result.data.expiresIn * 1000)
                    ApiResult.Success(Unit)
                }
                is ApiResult.Error -> result
                is ApiResult.NetworkError -> result
            }
        } catch (e: Exception) {
            return ApiResult.NetworkError(e)
        }
    }

    /**
     * Refresh the access token using the refresh token.
     */
    suspend fun refreshAccessToken(): ApiResult<Unit> {
        val serverUrl = storage.serverUrl
            ?: return ApiResult.Error("Server URL not found")
        val clientId = storage.clientId
            ?: return ApiResult.Error("Client ID not found")
        val keyId = storage.keyId
            ?: return ApiResult.Error("Key ID not found")
        val refreshToken = storage.refreshToken
            ?: return ApiResult.Error("Refresh token not found")

        try {
            // Get private key from Android KeyStore for signing
            val privateKey = keyStoreManager.getPrivateKey(keyId)
                ?: return ApiResult.Error("Private key not found for keyId: $keyId")

            // Create client assertion
            val tokenEndpoint = "$serverUrl/oauth2/token"
            val clientAssertion = JWTHelper.createClientAssertion(
                clientId = clientId,
                tokenEndpoint = tokenEndpoint,
                privateKey = privateKey,
                keyId = keyId
            )

            // Refresh token
            return when (val result = apiClient.refreshToken(
                serverUrl = serverUrl,
                clientId = clientId,
                refreshToken = refreshToken,
                clientAssertion = clientAssertion
            )) {
                is ApiResult.Success -> {
                    // Update tokens
                    storage.accessToken = result.data.accessToken
                    result.data.refreshToken?.let { storage.refreshToken = it }
                    storage.tokenExpiresAt = System.currentTimeMillis() + 
                                             (result.data.expiresIn * 1000)
                    ApiResult.Success(Unit)
                }
                is ApiResult.Error -> result
                is ApiResult.NetworkError -> result
            }
        } catch (e: Exception) {
            return ApiResult.NetworkError(e)
        }
    }

    /**
     * Get current user information from DHIS2 API.
     */
    suspend fun getUserInfo(): ApiResult<UserInfo> {
        val serverUrl = storage.serverUrl
            ?: return ApiResult.Error("Server URL not found")
        val accessToken = storage.accessToken
            ?: return ApiResult.Error("Access token not found")

        // Check if token is expired and refresh if needed
        if (System.currentTimeMillis() >= storage.tokenExpiresAt) {
            when (val refreshResult = refreshAccessToken()) {
                is ApiResult.Error -> return refreshResult
                is ApiResult.NetworkError -> return refreshResult
                is ApiResult.Success -> {} // Continue with refreshed token
            }
        }

        return apiClient.getUserInfo(serverUrl, storage.accessToken!!)
    }

    /**
     * Check if user is currently logged in (has valid token).
     */
    fun isLoggedIn(): Boolean {
        return storage.isLoggedIn
    }

    /**
     * Get the current access token.
     */
    fun getAccessToken(): String? = storage.accessToken

    /**
     * Logout user (clear tokens but keep registration).
     */
    fun logout() {
        storage.clearTokens()
    }

    /**
     * Get server URL.
     */
    fun getServerUrl(): String? = storage.serverUrl

    /**
     * Get client ID.
     */
    fun getClientId(): String? = storage.clientId
}

