package com.example.oauth2demo.network.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data models for DHIS2 OAuth2 DCR API interactions.
 */

/**
 * Request body for DCR registration at /connect/register
 */
data class ClientRegistrationRequest(
    val clientName: String,
    val redirectUris: List<String>,
    val grantTypes: List<String>,
    val responseTypes: List<String>,
    val tokenEndpointAuthMethod: String,
    val tokenEndpointAuthSigningAlg: String,
    val scope: String,
    val jwksUri: String? = null,
    val jwks: String // Inline JWKS JSON
) {
    fun toJson(): String {
        val json = JSONObject().apply {
            put("client_name", clientName)
            put("redirect_uris", JSONArray(redirectUris))
            put("grant_types", JSONArray(grantTypes))
            put("response_types", JSONArray(responseTypes))
            put("token_endpoint_auth_method", tokenEndpointAuthMethod)
            put("token_endpoint_auth_signing_alg", tokenEndpointAuthSigningAlg)
            put("scope", scope)
            jwksUri?.let { put("jwks_uri", it) }
            // Parse JWKS string to JSONObject for proper nesting
            put("jwks", JSONObject(jwks))
        }
        return json.toString()
    }
}

/**
 * Response from DCR registration
 */
data class ClientRegistrationResponse(
    val clientId: String,
    val clientIdIssuedAt: Long? = null,
    val clientName: String? = null,
    val redirectUris: List<String>? = null,
    val grantTypes: List<String>? = null,
    val responseTypes: List<String>? = null,
    val tokenEndpointAuthMethod: String? = null,
    val scope: String? = null
) {
    companion object {
        fun fromJson(json: String): ClientRegistrationResponse {
            val obj = JSONObject(json)
            return ClientRegistrationResponse(
                clientId = obj.getString("client_id"),
                clientIdIssuedAt = obj.optLong("client_id_issued_at", 0),
                clientName = obj.optString("client_name", null),
                redirectUris = obj.optJSONArray("redirect_uris")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                },
                grantTypes = obj.optJSONArray("grant_types")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                },
                responseTypes = obj.optJSONArray("response_types")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                },
                tokenEndpointAuthMethod = obj.optString("token_endpoint_auth_method", null),
                scope = obj.optString("scope", null)
            )
        }
    }
}

/**
 * Response from token endpoint
 */
data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val refreshToken: String? = null,
    val scope: String? = null
) {
    companion object {
        fun fromJson(json: String): TokenResponse {
            val obj = JSONObject(json)
            return TokenResponse(
                accessToken = obj.getString("access_token"),
                tokenType = obj.getString("token_type"),
                expiresIn = obj.getLong("expires_in"),
                refreshToken = obj.optString("refresh_token", null),
                scope = obj.optString("scope", null)
            )
        }
    }
}

/**
 * User info from /api/me endpoint
 */
data class UserInfo(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val email: String? = null
) {
    companion object {
        fun fromJson(json: String): UserInfo {
            val obj = JSONObject(json)
            return UserInfo(
                id = obj.getString("id"),
                username = obj.getString("username"),
                displayName = obj.optString("displayName", null),
                email = obj.optString("email", null)
            )
        }
    }
}

/**
 * Result wrapper for API calls
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
    data class NetworkError(val exception: Exception) : ApiResult<Nothing>()
}

