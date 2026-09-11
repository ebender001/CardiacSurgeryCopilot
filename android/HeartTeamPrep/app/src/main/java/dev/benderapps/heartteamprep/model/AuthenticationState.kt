package dev.benderapps.heartteamprep.model

/**
 * Display-facing view of the signed-in Parse User, mirroring the iOS
 * client's `AuthenticatedUser`. Views and view models never touch Parse
 * types directly -- see `service/AuthenticationService.kt`.
 */
data class AuthenticatedUser(
    val id: String,
    val email: String?,
    val isEmailVerified: Boolean,
    val signInMethod: SignInMethod
) {
    enum class SignInMethod { EMAIL, GOOGLE }
}

sealed interface AuthenticationState {
    /** The app hasn't yet checked whether a persisted session exists. */
    data object CheckingSession : AuthenticationState

    /**
     * Signing out or deleting the account is in progress -- shown between
     * [SignedIn] and [SignedOut] so that transition has a visible
     * in-between state rather than either freezing on the signed-in app
     * during the network call or cutting straight to Welcome.
     */
    data object EndingSession : AuthenticationState

    data object SignedOut : AuthenticationState

    data class SignedIn(val user: AuthenticatedUser) : AuthenticationState
}

enum class EmailAuthMode { SIGN_IN, CREATE_ACCOUNT }
