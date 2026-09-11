package dev.benderapps.heartteamprep.service

import com.parse.ParseCloud
import com.parse.ParseException
import com.parse.ParseUser
import dev.benderapps.heartteamprep.model.AuthenticatedUser

/**
 * The only file that calls ParseUser/Parse Android SDK authentication APIs.
 * Everything else (screens, view models) goes through the
 * [AuthenticationService] interface -- see that file for the flow.
 */
class ParseAuthenticationService : AuthenticationService {
    override fun currentUser(): AuthenticatedUser? =
        ParseUser.getCurrentUser()?.toAuthenticatedUser()

    override suspend fun signUp(email: String, password: String): AuthenticatedUser {
        val newUser = ParseUser()
        newUser.username = email
        newUser.email = email
        newUser.setPassword(password)
        return try {
            newUser.signUpInBackground().await()
            newUser.toAuthenticatedUser()
        } catch (e: ParseException) {
            throw map(e)
        } catch (e: Exception) {
            throw AuthenticationServiceError.Network
        }
    }

    override suspend fun signIn(email: String, password: String): AuthenticatedUser =
        try {
            ParseUser.logInInBackground(email, password).await().toAuthenticatedUser()
        } catch (e: ParseException) {
            throw map(e)
        } catch (e: Exception) {
            throw AuthenticationServiceError.Network
        }

    override suspend fun signInWithGoogle(credential: GoogleSignInCredential): AuthenticatedUser {
        val authData = mapOf("id" to credential.id, "id_token" to credential.idToken)
        return try {
            val loggedIn = ParseUser.logInWithInBackground("google", authData).await()
            var needsSave = false

            // Parse Server already links repeat sign-ins by the identity
            // baked into authData -- this field is an explicit, directly
            // queryable record of the same id (the Android SDK doesn't
            // expose authData for reads, unlike Parse-Swift), which is
            // also what `toAuthenticatedUser` below uses to tell a
            // Google-linked account apart from an email/password one.
            if (loggedIn.getString(GOOGLE_USER_IDENTIFIER_KEY) != credential.id) {
                loggedIn.put(GOOGLE_USER_IDENTIFIER_KEY, credential.id)
                needsSave = true
            }

            // Parse Server's "google" adapter doesn't persist the email
            // from the ID token onto the User itself -- persist it the one
            // time it's missing rather than leaving it unset.
            if (loggedIn.email == null && credential.email != null) {
                loggedIn.email = credential.email
                needsSave = true
            }

            if (needsSave) {
                loggedIn.saveInBackground().await()
            }

            loggedIn.toAuthenticatedUser()
        } catch (e: ParseException) {
            throw map(e)
        } catch (e: Exception) {
            throw AuthenticationServiceError.Network
        }
    }

    override suspend fun sendPasswordReset(email: String) {
        try {
            ParseUser.requestPasswordResetInBackground(email).await()
        } catch (e: ParseException) {
            if (isNetworkFailure(e)) throw AuthenticationServiceError.Network
            // Parse Server's password-reset endpoint intentionally does not
            // reveal whether the email is registered -- any non-network
            // failure here is treated the same as success by the caller.
            // See AuthenticationViewModel.
        }
    }

    override suspend fun signOut() {
        try {
            ParseUser.logOutInBackground().await()
        } catch (e: ParseException) {
            throw map(e)
        } catch (e: Exception) {
            throw AuthenticationServiceError.Network
        }
    }

    override suspend fun deleteAccount() {
        try {
            ParseCloud.callFunctionInBackground<Any>("cscDeleteAccount", emptyMap<String, Any>()).await()
        } catch (e: ParseException) {
            throw map(e)
        } catch (e: Exception) {
            throw AuthenticationServiceError.Network
        }
        // The Parse User no longer exists server-side once the call above
        // succeeds, so there's no remote session left to invalidate --
        // this only clears the locally cached session. Best-effort: the
        // account is already gone regardless of whether this succeeds, so
        // a failure here must never be surfaced as a deletion failure.
        try {
            ParseUser.logOutInBackground().await()
        } catch (e: Exception) {
            // Intentionally ignored -- see comment above.
        }
    }

    private fun isNetworkFailure(error: ParseException): Boolean =
        error.code == ParseException.CONNECTION_FAILED || error.code == ParseException.TIMEOUT

    private fun map(error: ParseException): AuthenticationServiceError = when (error.code) {
        ParseException.USERNAME_TAKEN, ParseException.EMAIL_TAKEN -> AuthenticationServiceError.EmailAlreadyInUse
        // Parse Server returns this same code for "wrong username/password"
        // as for "object not found" -- in a login context it always means
        // invalid credentials.
        ParseException.OBJECT_NOT_FOUND -> AuthenticationServiceError.InvalidCredentials
        ParseException.VALIDATION_ERROR -> AuthenticationServiceError.Validation(error.message ?: "")
        ParseException.CONNECTION_FAILED, ParseException.TIMEOUT -> AuthenticationServiceError.Network
        else -> AuthenticationServiceError.Server
    }
}

private const val GOOGLE_USER_IDENTIFIER_KEY = "googleUserIdentifier"

private fun ParseUser.toAuthenticatedUser() = AuthenticatedUser(
    id = objectId ?: "",
    email = email,
    isEmailVerified = getBoolean("emailVerified"),
    signInMethod = if (getString(GOOGLE_USER_IDENTIFIER_KEY).isNullOrEmpty()) {
        AuthenticatedUser.SignInMethod.EMAIL
    } else {
        AuthenticatedUser.SignInMethod.GOOGLE
    }
)
