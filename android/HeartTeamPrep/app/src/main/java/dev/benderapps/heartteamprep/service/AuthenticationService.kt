package dev.benderapps.heartteamprep.service

import dev.benderapps.heartteamprep.model.AuthenticatedUser

/**
 * The single interface between the app and account authentication. Views
 * and view models never call Parse APIs directly -- see
 * `AuthenticationViewModel`, the only caller of this interface, and
 * `ParseAuthenticationService`, the only concrete implementation.
 *
 * Flow: Screen -> AuthenticationViewModel -> AuthenticationService -> Parse.
 */
interface AuthenticationService {
    /**
     * The signed-in user, if a session is already persisted (e.g. from a
     * previous launch). Synchronous: the Parse SDK caches this in memory
     * (backed by shared preferences) rather than making a network call.
     */
    fun currentUser(): AuthenticatedUser?

    suspend fun signUp(email: String, password: String): AuthenticatedUser
    suspend fun signIn(email: String, password: String): AuthenticatedUser
    suspend fun signInWithGoogle(credential: GoogleSignInCredential): AuthenticatedUser

    /**
     * Always succeeds from the caller's point of view (matches Parse
     * Server's own enumeration-safe behavior) unless the request never
     * reached the server -- see `ParseAuthenticationService`.
     */
    suspend fun sendPasswordReset(email: String)

    suspend fun signOut()

    /**
     * Permanently deletes the signed-in account and all of its case data,
     * then clears the local session. Unlike [signOut], this can genuinely
     * fail (network/server error) and callers must surface that -- it's a
     * destructive, irreversible action the person needs accurate feedback
     * on, not a best-effort background cleanup.
     */
    suspend fun deleteAccount()
}

/** User-facing errors surfaced by [AuthenticationService]. Raw Parse error text is never shown to the trainee. */
sealed class AuthenticationServiceError(val userMessage: String) : Exception(userMessage) {
    data object InvalidCredentials :
        AuthenticationServiceError("That email and password don't match. Please try again.")

    data object EmailAlreadyInUse :
        AuthenticationServiceError("An account already exists for that email.")

    /**
     * A server-side validation rule was rejected (e.g. a Back4App
     * password-policy requirement) -- the message is server-authored but
     * already user-safe, unlike other Parse error text.
     */
    data class Validation(val serverMessage: String) : AuthenticationServiceError(serverMessage)

    data object Network :
        AuthenticationServiceError("Heart Team Prep couldn't reach the server. Check your connection and try again.")

    data object Server : AuthenticationServiceError("Something went wrong. Please try again.")
}
