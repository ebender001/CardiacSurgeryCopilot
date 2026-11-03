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
}
