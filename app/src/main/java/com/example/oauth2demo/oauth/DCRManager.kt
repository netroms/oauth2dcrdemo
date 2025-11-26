package com.example.oauth2demo.oauth

import android.content.Context
import android.os.Build
import com.example.oauth2demo.network.DHIS2ApiClient
import com.example.oauth2demo.network.models.ApiResult
import com.example.oauth2demo.network.models.ClientRegistrationRequest
import com.example.oauth2demo.storage.KeyStoreManager
import com.example.oauth2demo.storage.SecureStorage

/**
 * Manager for Dynamic Client Registration (DCR) flow.
 * Handles device enrollment, IAT retrieval, key generation, and client registration.
 */
class DCRManager(private val context: Context) {

    private val storage = SecureStorage(context)
    private val keyStoreManager = KeyStoreManager()
    private val apiClient = DHIS2ApiClient()

    companion object {
        const val REDIRECT_URI = "dhis2oauth://oauth"
        const val DEVICE_TYPE = "android"
    }

    /**
     * Build the enrollment URL for device registration.
     * User must authenticate in browser, then IAT is returned via redirect.
     */
    fun buildEnrollmentUrl(serverUrl: String, state: String): String {
        val deviceVersion = Build.VERSION.RELEASE
        val deviceAttestation = "android_sdk_${Build.VERSION.SDK_INT}"

        return "$serverUrl/api/auth/enrollDevice" +
                "?deviceVersion=$deviceVersion" +
                "&deviceType=$DEVICE_TYPE" +
                "&deviceAttestation=$deviceAttestation" +
                "&redirectUri=$REDIRECT_URI" +
                "&state=$state"
    }

    /**
     * Process IAT received from enrollment redirect and perform client registration.
     * 
     * Steps:
     * 1. Validate IAT
     * 2. Generate RSA key pair
     * 3. Create JWKS from public key
     * 4. Register client with DHIS2 using DCR
     * 5. Store client_id and key_id
     * 
     * @param serverUrl DHIS2 server URL
     * @param iat Initial Access Token (JWT) from enrollment
     * @return Result with success/error
     */
    suspend fun registerDevice(serverUrl: String, iat: String): ApiResult<String> {
        try {
            // Validate IAT
            val claims = JWTHelper.validateJWT(iat)
                ?: return ApiResult.Error("Invalid or expired IAT")

            // Generate RSA key pair
            val keyId = keyStoreManager.generateKeyPair()

            // Create JWKS JSON with public key
            val jwks = keyStoreManager.createJWKS(keyId)

            // Build registration request
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            
            val registrationRequest = ClientRegistrationRequest(
                clientName = "DHIS2 Android Demo - $deviceId",
                redirectUris = listOf(REDIRECT_URI),
                grantTypes = listOf("authorization_code", "refresh_token"),
                responseTypes = listOf("code"),
                tokenEndpointAuthMethod = "private_key_jwt",
                tokenEndpointAuthSigningAlg = "RS256",
                scope = "openid profile username",
                jwksUri = "https://dhis2.org/jwks.json", // Required but ignored per spec
                jwks = jwks
            )

            // Register with DHIS2
            return when (val result = apiClient.registerClient(serverUrl, iat, registrationRequest)) {
                is ApiResult.Success -> {
                    // Save registration data
                    storage.serverUrl = serverUrl
                    storage.clientId = result.data.clientId
                    storage.keyId = keyId
                    storage.isRegistered = true
                    storage.registrationDate = System.currentTimeMillis()

                    ApiResult.Success(result.data.clientId)
                }
                is ApiResult.Error -> {
                    // Clean up key on failure
                    keyStoreManager.deleteKey(keyId)
                    result
                }
                is ApiResult.NetworkError -> {
                    // Clean up key on failure
                    keyStoreManager.deleteKey(keyId)
                    result
                }
            }
        } catch (e: Exception) {
            return ApiResult.NetworkError(e)
        }
    }

    /**
     * Check if device is already registered.
     */
    fun isDeviceRegistered(): Boolean {
        return storage.isRegistered && 
               !storage.clientId.isNullOrBlank() && 
               !storage.keyId.isNullOrBlank() &&
               storage.keyId?.let { keyStoreManager.hasKey(it) } == true
    }

    /**
     * Get the registered client ID.
     */
    fun getClientId(): String? = storage.clientId

    /**
     * Get the registered server URL.
     */
    fun getServerUrl(): String? = storage.serverUrl

    /**
     * Reset registration (for testing/debugging).
     * Deletes all keys and storage.
     */
    fun resetRegistration() {
        storage.keyId?.let { keyStoreManager.deleteKey(it) }
        storage.clearAll()
    }
}

