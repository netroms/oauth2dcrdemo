package com.example.oauth2demo.network

import com.example.oauth2demo.network.models.ApiResult
import com.example.oauth2demo.network.models.ClientRegistrationRequest
import com.example.oauth2demo.network.models.ClientRegistrationResponse
import com.example.oauth2demo.network.models.TokenResponse
import com.example.oauth2demo.network.models.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * HTTP client for DHIS2 OAuth2 and API interactions.
 * Handles DCR registration, token exchange, and authenticated API calls.
 */
class DHIS2ApiClient {

    private val client: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Register a new OAuth2 client using Dynamic Client Registration.
     * POST /connect/register with IAT bearer token.
     */
    suspend fun registerClient(
        serverUrl: String,
        iat: String,
        request: ClientRegistrationRequest
    ): ApiResult<ClientRegistrationResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "$serverUrl/connect/register"
            val requestBody = request.toJson()
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $iat")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val registrationResponse = ClientRegistrationResponse.fromJson(responseBody)
                ApiResult.Success(registrationResponse)
            } else {
                ApiResult.Error("Registration failed: $responseBody", response.code)
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }

    /**
     * Exchange authorization code for access token using private_key_jwt.
     * POST /oauth2/token with client_assertion.
     */
    suspend fun exchangeCodeForToken(
        serverUrl: String,
        clientId: String,
        authorizationCode: String,
        redirectUri: String,
        clientAssertion: String
    ): ApiResult<TokenResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "$serverUrl/oauth2/token"

            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authorizationCode)
                .add("redirect_uri", redirectUri)
                .add("client_id", clientId)
                .add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .add("client_assertion", clientAssertion)
                .build()

            val httpRequest = Request.Builder()
                .url(url)
                .post(formBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val tokenResponse = TokenResponse.fromJson(responseBody)
                ApiResult.Success(tokenResponse)
            } else {
                ApiResult.Error("Token exchange failed: $responseBody", response.code)
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }

    /**
     * Refresh access token using refresh token and private_key_jwt.
     * POST /oauth2/token with refresh_token grant.
     */
    suspend fun refreshToken(
        serverUrl: String,
        clientId: String,
        refreshToken: String,
        clientAssertion: String
    ): ApiResult<TokenResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "$serverUrl/oauth2/token"

            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .add("client_assertion", clientAssertion)
                .build()

            val httpRequest = Request.Builder()
                .url(url)
                .post(formBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val tokenResponse = TokenResponse.fromJson(responseBody)
                ApiResult.Success(tokenResponse)
            } else {
                ApiResult.Error("Token refresh failed: $responseBody", response.code)
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }

    /**
     * Get current user information from /api/me endpoint.
     * Requires valid access token.
     */
    suspend fun getUserInfo(
        serverUrl: String,
        accessToken: String
    ): ApiResult<UserInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "$serverUrl/api/me"

            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val userInfo = UserInfo.fromJson(responseBody)
                ApiResult.Success(userInfo)
            } else {
                ApiResult.Error("Failed to fetch user info: $responseBody", response.code)
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }

    /**
     * Test server connectivity and check if it's a valid DHIS2 server.
     */
    suspend fun testServerConnection(serverUrl: String): ApiResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$serverUrl/api/system/info"

            val httpRequest = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(httpRequest).execute()

            if (response.isSuccessful) {
                ApiResult.Success(true)
            } else {
                ApiResult.Error("Server not reachable", response.code)
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }
}

