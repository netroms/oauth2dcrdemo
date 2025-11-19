package com.example.oauth2demo.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

/**
 * Manages RSA key pairs in Android KeyStore for private_key_jwt authentication.
 * Keys are generated and stored securely, never leaving the device.
 */
class KeyStoreManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Generate a new RSA 2048-bit key pair and store it in Android KeyStore.
     * Returns the key ID (kid) to be used in JWT headers.
     */
    fun generateKeyPair(): String {
        val keyId = UUID.randomUUID().toString()
        
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            getKeyAlias(keyId),
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .build()

        keyPairGenerator.initialize(keyGenParameterSpec)
        keyPairGenerator.generateKeyPair()

        return keyId
    }

    /**
     * Get the private key for a given key ID.
     */
    fun getPrivateKey(keyId: String): PrivateKey? {
        return keyStore.getKey(getKeyAlias(keyId), null) as? PrivateKey
    }

    /**
     * Get the public key for a given key ID.
     */
    fun getPublicKey(keyId: String): PublicKey? {
        return keyStore.getCertificate(getKeyAlias(keyId))?.publicKey
    }

    /**
     * Get both public and private keys as a KeyPair.
     */
    fun getKeyPair(keyId: String): KeyPair? {
        val privateKey = getPrivateKey(keyId) ?: return null
        val publicKey = getPublicKey(keyId) ?: return null
        return KeyPair(publicKey, privateKey)
    }

    /**
     * Create JWKS JSON for DCR registration (inline JWKS with public key only).
     * This is the format DHIS2 expects in the registration request.
     */
    fun createJWKS(keyId: String): String {
        val publicKey = getPublicKey(keyId) as? RSAPublicKey
            ?: throw IllegalStateException("Public key not found for keyId: $keyId")

        val rsaKey = RSAKey.Builder(publicKey)
            .keyID(keyId)
            .build()

        val jwkSet = JWKSet(rsaKey)
        return jwkSet.toString()
    }

    /**
     * Create RSAKey object (includes private key) for JWT signing.
     */
    fun createRSAKey(keyId: String): RSAKey {
        val keyPair = getKeyPair(keyId)
            ?: throw IllegalStateException("Key pair not found for keyId: $keyId")

        return RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID(keyId)
            .build()
    }

    /**
     * Check if a key exists for the given key ID.
     */
    fun hasKey(keyId: String): Boolean {
        return keyStore.containsAlias(getKeyAlias(keyId))
    }

    /**
     * Delete a key pair from the KeyStore.
     */
    fun deleteKey(keyId: String) {
        if (hasKey(keyId)) {
            keyStore.deleteEntry(getKeyAlias(keyId))
        }
    }

    /**
     * Delete all keys managed by this app.
     */
    fun deleteAllKeys() {
        keyStore.aliases().toList().forEach { alias ->
            if (alias.startsWith(KEY_ALIAS_PREFIX)) {
                keyStore.deleteEntry(alias)
            }
        }
    }

    private fun getKeyAlias(keyId: String): String = "$KEY_ALIAS_PREFIX$keyId"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "dhis2_oauth_key_"
    }
}

