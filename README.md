# DHIS2 Android OAuth2 DCR Demo

> **⚠️ This is a Proof of Concept / Demo Project**
>
> This project is intended **for educational purposes only** to demonstrate how Android (or other mobile clients) can implement OAuth2 Dynamic Client Registration (DCR) with a DHIS2 server. It is not production-ready code.

## Overview

This Android application demonstrates the complete OAuth2 Dynamic Client Registration (DCR) flow with `private_key_jwt` authentication for DHIS2, as implemented in [PR #22183](https://github.com/dhis2/dhis2-core/pull/22183).

### What is Dynamic Client Registration?

DCR allows mobile devices to securely register themselves as OAuth2 clients without pre-shared secrets. Each device:

1. Generates its own RSA key pair (stored securely in Android KeyStore)
2. Registers with the DHIS2 server using the public key
3. Authenticates using `private_key_jwt` - signing JWTs with the private key

This eliminates the security risk of embedding shared client secrets in mobile apps.

## Features Demonstrated

- **Device Enrollment**: Obtain an Initial Access Token (IAT) from `/api/auth/enrollDevice`
- **Dynamic Client Registration**: Register as an OAuth2 client at `/connect/register` with inline JWKS
- **PKCE Support**: Proof Key for Code Exchange for enhanced security
- **Private Key JWT Authentication**: Sign client assertions with device-specific RSA keys
- **Secure Key Storage**: RSA keys stored in Android KeyStore (hardware-backed when available)
- **Token Management**: Access token, refresh token, and automatic token refresh

## DHIS2 Server Requirements

This demo requires a DHIS2 server with the DCR feature enabled (available in DHIS2 versions with [PR #22183](https://github.com/dhis2/dhis2-core/pull/22183) merged).

### Required Server Configuration

Configure the following system settings on your DHIS2 server:

| Setting | Description | Example |
|---------|-------------|---------|
| `deviceEnrollmentRedirectAllowlist` | Comma-separated list of allowed redirect URIs | `dhis2oauth://oauth` |
| `deviceEnrollmentAllowedUserGroups` | (Optional) Restrict enrollment to specific user groups | `<group-uid>` |
| `deviceEnrollmentIATTtlSeconds` | IAT validity period (default: 60 seconds) | `60` |

## Quick Start Guide

### Prerequisites

- Android Studio (latest version recommended)
- Android device or emulator (API 26+)
- DHIS2 server with DCR feature enabled

### Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd android_oauth2demo
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Build and Run**
   - Connect an Android device or start an emulator
   - Click "Run" or press `Shift+F10`

### Using the App

1. **Enter Server URL**
   - Launch the app
   - Enter your DHIS2 server URL (e.g., `https://play.dhis2.org/dev`)
   - Tap "Register Device"

2. **Authenticate in Browser**
   - A browser will open to the DHIS2 login page
   - Log in with your DHIS2 credentials
   - The browser will redirect back to the app

3. **Login**
   - After registration, tap "Login with DHIS2"
   - Authenticate again in the browser
   - The app will exchange the authorization code for tokens

4. **View Dashboard**
   - See your user information fetched from `/api/me`
   - View your access token and device info

## OAuth2 Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ENROLLMENT FLOW                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Android App                    Browser                    DHIS2 Server     │
│      │                            │                            │            │
│      │──── Open enrollment URL ───►                            │            │
│      │                            │──── /api/auth/enrollDevice─►            │
│      │                            │                            │            │
│      │                            │◄─── Login page ────────────│            │
│      │                            │                            │            │
│      │                            │──── User authenticates ────►            │
│      │                            │                            │            │
│      │◄─── Redirect with IAT ─────│◄─── Redirect with IAT ─────│            │
│      │                            │                            │            │
│      │──── Generate RSA key pair                               │            │
│      │                                                         │            │
│      │──── POST /connect/register (with IAT + JWKS) ──────────►│            │
│      │                                                         │            │
│      │◄─── client_id ──────────────────────────────────────────│            │
│      │                                                         │            │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                             LOGIN FLOW (with PKCE)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Android App                    Browser                    DHIS2 Server     │
│      │                            │                            │            │
│      │──── Generate code_verifier + code_challenge             │            │
│      │                            │                            │            │
│      │──── Open authorization URL (with code_challenge) ──────►│            │
│      │                            │                            │            │
│      │                            │◄─── Authorization page ────│            │
│      │                            │                            │            │
│      │◄─── Redirect with code ────│◄─── Redirect with code ────│            │
│      │                            │                            │            │
│      │──── Create JWT assertion (signed with private key)      │            │
│      │                                                         │            │
│      │──── POST /oauth2/token ────────────────────────────────►│            │
│      │     (code + code_verifier + client_assertion)           │            │
│      │                                                         │            │
│      │◄─── access_token, refresh_token ────────────────────────│            │
│      │                                                         │            │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Project Structure

```
app/src/main/java/com/example/oauth2demo/
├── MainActivity.kt              # Dashboard after login
├── ui/
│   ├── WelcomeActivity.kt       # Entry point, server URL input
│   ├── OAuthCallbackActivity.kt # Deep link callback router
│   └── LoginActivity.kt         # OAuth login flow
├── oauth/
│   ├── DCRManager.kt            # Device registration logic
│   ├── OAuth2Manager.kt         # OAuth2 flow logic
│   └── JWTHelper.kt             # JWT creation, PKCE support
├── storage/
│   ├── SecureStorage.kt         # Encrypted SharedPreferences
│   └── KeyStoreManager.kt       # RSA key management
└── network/
    ├── DHIS2ApiClient.kt        # HTTP client
    └── models/
        └── Models.kt            # Data classes
```

## Security Features

| Feature | Implementation |
|---------|---------------|
| **Private Key Protection** | RSA keys stored in Android KeyStore (hardware-backed when available) |
| **Encrypted Storage** | EncryptedSharedPreferences with AES256-GCM |
| **CSRF Protection** | Random state parameter validated on callbacks |
| **PKCE** | S256 code challenge method per RFC 7636 |
| **Short-lived IAT** | Initial Access Token expires in 60 seconds |

## References

### DHIS2 Server Implementation

- **Pull Request**: [feat: Android DCR [DHIS2-19948] #22183](https://github.com/dhis2/dhis2-core/pull/22183)
- **Enrollment Controller**: [OAuth2DynamicClientRegistrationController.java](https://github.com/dhis2/dhis2-core/blob/6093ab68416949c95e42ff27a7cfb67850b32af9/dhis-2/dhis-web-api/src/main/java/org/hisp/dhis/webapi/controller/security/oauth/OAuth2DynamicClientRegistrationController.java)

### Relevant RFCs

- [RFC 7591](https://datatracker.ietf.org/doc/html/rfc7591) - OAuth 2.0 Dynamic Client Registration Protocol
- [RFC 7523](https://datatracker.ietf.org/doc/html/rfc7523) - JSON Web Token (JWT) Profile for OAuth 2.0 Client Authentication
- [RFC 7636](https://datatracker.ietf.org/doc/html/rfc7636) - Proof Key for Code Exchange (PKCE)
- [RFC 8252](https://datatracker.ietf.org/doc/html/rfc8252) - OAuth 2.0 for Native Apps

## Dependencies

- **OkHttp**: HTTP client
- **Nimbus JOSE+JWT**: JWT creation and signing
- **AndroidX Security Crypto**: Encrypted storage
- **AndroidX Browser**: Custom Tabs for OAuth flows

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Cannot connect to server" | Check server URL format and network connectivity |
| "Invalid or expired IAT" | IAT expires in 60 seconds - retry enrollment |
| "Registration failed: 401" | IAT can only be used once - restart enrollment |
| "Token exchange failed" | Verify JWKS was correctly registered |
| Deep link not working | Check AndroidManifest.xml intent filter |

## License

This demo project is provided for educational purposes. See the DHIS2 project for licensing information.

## Disclaimer

This is a demonstration project and should not be used in production without proper security review and hardening. Always follow security best practices when implementing OAuth2 flows in production applications.

