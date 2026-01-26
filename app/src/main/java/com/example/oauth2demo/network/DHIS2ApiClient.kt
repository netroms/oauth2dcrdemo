package com.example.oauth2demo.network

import com.example.oauth2demo.network.models.ApiResult
import com.example.oauth2demo.network.models.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * HTTP client for DHIS2 API interactions.
 * The SDK handles OAuth2 authentication automatically.
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

