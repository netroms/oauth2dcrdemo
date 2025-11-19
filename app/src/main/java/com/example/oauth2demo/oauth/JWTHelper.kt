package com.example.oauth2demo.oauth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.util.Date
import java.util.UUID

/**
 * Helper for creating JWT assertions for private_key_jwt authentication.
 * Follows RFC 7523: JSON Web Token (JWT) Profile for OAuth 2.0 Client Authentication.
 */
object JWTHelper {

    /**
     * Create a signed JWT assertion for private_key_jwt client authentication.
     * 
     * @param clientId The OAuth2 client ID (used as both issuer and subject)
     * @param tokenEndpoint The token endpoint URL (used as audience)
     * @param rsaKey The RSA key pair with private key for signing
     * @param keyId The key ID (kid) to include in JWT header
     * @param expiresInSeconds How long the JWT should be valid (default: 60 seconds)
     * @return Signed JWT string
     */
    fun createClientAssertion(
        clientId: String,
        tokenEndpoint: String,
        rsaKey: com.nimbusds.jose.jwk.RSAKey,
        keyId: String,
        expiresInSeconds: Long = 60
    ): String {
        val now = Date()
        val expirationTime = Date(now.time + expiresInSeconds * 1000)

        // Create JWT claims per RFC 7523
        val claimsSet = JWTClaimsSet.Builder()
            .issuer(clientId)           // iss: The client_id
            .subject(clientId)          // sub: The client_id
            .audience(tokenEndpoint)    // aud: The token endpoint URL
            .issueTime(now)             // iat: Current time
            .expirationTime(expirationTime) // exp: Expiration time
            .jwtID(UUID.randomUUID().toString()) // jti: Unique token ID
            .build()

        // Create JWT header with key ID
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(keyId)
            .build()

        // Sign the JWT with the private key
        val signedJWT = SignedJWT(header, claimsSet)
        val signer = RSASSASigner(rsaKey)
        signedJWT.sign(signer)

        return signedJWT.serialize()
    }

    /**
     * Generate a random state parameter for OAuth flows to prevent CSRF attacks.
     */
    fun generateState(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Validate that a JWT has the expected claims.
     * Used for validating the IAT (Initial Access Token) from DHIS2.
     */
    fun validateJWT(jwtString: String): JWTClaimsSet? {
        return try {
            val signedJWT = SignedJWT.parse(jwtString)
            val claims = signedJWT.jwtClaimsSet
            
            // Basic validation: check expiration
            val now = Date()
            if (claims.expirationTime != null && claims.expirationTime.before(now)) {
                null // Token expired
            } else {
                claims
            }
        } catch (e: Exception) {
            null // Invalid JWT
        }
    }
}

