package dev.benderapps.heartteamprep.service

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dev.benderapps.heartteamprep.BackendConfig

/**
 * What the app needs from a Google sign-in, the Android equivalent of the
 * iOS client's `AppleSignInCredential`. [id] is the stable Google account
 * identifier (`GoogleIdTokenCredential.uniqueId`, the ID token's `sub`
 * claim -- NOT `.id`/`.email`, which is the account's email and can
 * change) and is what Parse Server's built-in "google" auth adapter
 * requires alongside [idToken] -- see `ParseAuthenticationService`.
 */
data class GoogleSignInCredential(
    val id: String,
    val idToken: String,
    val displayName: String?,
    val email: String?
)

sealed class GoogleSignInError : Exception() {
    /** The person dismissed the account picker themselves -- callers should handle this silently, not show an error. */
    data object Cancelled : GoogleSignInError()

    /** The credential succeeded but wasn't a Google ID token, or couldn't be parsed -- shouldn't happen in practice. */
    data object InvalidResponse : GoogleSignInError()

    /** A genuine failure (network, Google-side issue, etc.) -- callers should show an error. */
    data object Failed : GoogleSignInError()
}

interface GoogleSignInCredentialExtracting {
    suspend fun credential(context: Context): GoogleSignInCredential
}

/**
 * The only file that talks to Credential Manager / Google Identity APIs.
 * `AuthenticationViewModel` hands the resulting credential to
 * `AuthenticationService`, which is the only thing that talks to Parse.
 */
class GoogleSignInService : GoogleSignInCredentialExtracting {
    override suspend fun credential(context: Context): GoogleSignInCredential {
        val option = GetSignInWithGoogleOption.Builder(BackendConfig.googleWebClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val response = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInError.Cancelled
        } catch (e: GetCredentialException) {
            throw GoogleSignInError.Failed
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw GoogleSignInError.InvalidResponse
        }

        val googleIdTokenCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            throw GoogleSignInError.InvalidResponse
        }

        return GoogleSignInCredential(
            id = googleIdTokenCredential.uniqueId,
            idToken = googleIdTokenCredential.idToken,
            displayName = googleIdTokenCredential.displayName,
            email = googleIdTokenCredential.email
        )
    }
}
