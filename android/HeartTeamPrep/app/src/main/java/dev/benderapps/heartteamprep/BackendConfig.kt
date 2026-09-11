package dev.benderapps.heartteamprep

import java.net.URL

/**
 * Back4App connection details, mirroring the iOS client's `BackendConfig.swift`.
 *
 * `applicationId` and `clientKey` are the app's public client credentials
 * (the mobile-app equivalent of a Firebase `google-services.json`) -- they
 * identify this app to Back4App and are meant to ship inside the client
 * binary. They are **not** secrets. Never add the Master Key or REST API Key
 * here; those must stay server-side only.
 */
object BackendConfig {
    const val applicationId = "OatnJSvXMbC5wPJQLtMDVT1OZoGrSYtpF3ko3pz3"
    // TODO: fill in from the Back4App dashboard -- App Settings > Security
    // & Keys > "Client Key" (not the REST API Key, and not the Master Key).
    const val clientKey = "rLXlfRr1zJJfLMnAbeGSCvj7mqPUtHsmVNYJuLLs"
    val serverUrl: URL = URL("https://parseapi.back4app.com")

    // TODO: fill in with a Google Cloud Console OAuth 2.0 "Web application"
    // client ID (Credential Manager's Sign in with Google needs the *Web*
    // client, not an Android one, as the ID token audience -- see
    // https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation).
    // The same client ID must also be added to Back4App's dashboard under
    // Server Settings > Sign-in with Google, the same way Apple's Services
    // ID is configured there for Sign in with Apple.
    const val googleWebClientId = ""
}
