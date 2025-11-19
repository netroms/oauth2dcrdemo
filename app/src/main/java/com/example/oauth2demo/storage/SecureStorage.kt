package com.example.oauth2demo.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for sensitive application data using EncryptedSharedPreferences.
 * Stores server URL, client ID, tokens, and registration status.
 */
class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Server URL
    var serverUrl: String?
        get() = sharedPreferences.getString(KEY_SERVER_URL, null)
        set(value) = sharedPreferences.edit().putString(KEY_SERVER_URL, value).apply()

    // Client ID from DCR registration
    var clientId: String?
        get() = sharedPreferences.getString(KEY_CLIENT_ID, null)
        set(value) = sharedPreferences.edit().putString(KEY_CLIENT_ID, value).apply()

    // Key ID (kid) used in JWT header
    var keyId: String?
        get() = sharedPreferences.getString(KEY_KEY_ID, null)
        set(value) = sharedPreferences.edit().putString(KEY_KEY_ID, value).apply()

    // Access token
    var accessToken: String?
        get() = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
        set(value) = sharedPreferences.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    // Refresh token
    var refreshToken: String?
        get() = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
        set(value) = sharedPreferences.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    // Token expiration timestamp
    var tokenExpiresAt: Long
        get() = sharedPreferences.getLong(KEY_TOKEN_EXPIRES_AT, 0)
        set(value) = sharedPreferences.edit().putLong(KEY_TOKEN_EXPIRES_AT, value).apply()

    // Registration status
    var isRegistered: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_REGISTERED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_IS_REGISTERED, value).apply()

    // Registration timestamp
    var registrationDate: Long
        get() = sharedPreferences.getLong(KEY_REGISTRATION_DATE, 0)
        set(value) = sharedPreferences.edit().putLong(KEY_REGISTRATION_DATE, value).apply()

    // Login status
    var isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank() && System.currentTimeMillis() < tokenExpiresAt
        private set(value) {} // Computed property

    /**
     * Clear all tokens (logout) but keep registration data
     */
    fun clearTokens() {
        sharedPreferences.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_TOKEN_EXPIRES_AT)
            apply()
        }
    }

    /**
     * Clear everything (reset app state)
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "dhis2_oauth_secure_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_KEY_ID = "key_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val KEY_IS_REGISTERED = "is_registered"
        private const val KEY_REGISTRATION_DATE = "registration_date"
    }
}

