package dev.benderapps.heartteamprep.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.benderapps.heartteamprep.model.AuthenticationState
import dev.benderapps.heartteamprep.model.EmailAuthMode
import dev.benderapps.heartteamprep.service.AuthenticationService
import dev.benderapps.heartteamprep.service.AuthenticationServiceError
import dev.benderapps.heartteamprep.service.GoogleSignInCredentialExtracting
import dev.benderapps.heartteamprep.service.GoogleSignInError
import dev.benderapps.heartteamprep.service.GoogleSignInService
import dev.benderapps.heartteamprep.service.ParseAuthenticationService
import kotlinx.coroutines.launch

/**
 * Drives every authentication screen (Welcome, EmailAuthScreen,
 * PasswordResetScreen) and the app-root decision between them and the
 * signed-in app (see AppRoot). Screens only ever call this -- never
 * AuthenticationService/GoogleSignInService/Parse directly.
 */
class AuthenticationViewModel(
    private val authService: AuthenticationService = ParseAuthenticationService(),
    private val googleSignIn: GoogleSignInCredentialExtracting = GoogleSignInService()
) : ViewModel() {

    var state: AuthenticationState by mutableStateOf(AuthenticationState.CheckingSession)
        private set

    // Sign in with Google
    var isGoogleSignInInProgress: Boolean by mutableStateOf(false)
        private set
    var googleSignInErrorMessage: String? by mutableStateOf(null)

    // Email sign-in/create-account form
    var emailMode: EmailAuthMode by mutableStateOf(EmailAuthMode.SIGN_IN)
    var email: String by mutableStateOf("")
    var password: String by mutableStateOf("")
    var confirmPassword: String by mutableStateOf("")
    var isPasswordVisible: Boolean by mutableStateOf(false)
    var isSubmittingEmailForm: Boolean by mutableStateOf(false)
        private set
    var emailFieldError: String? by mutableStateOf(null)
        private set
    var passwordFieldError: String? by mutableStateOf(null)
        private set
    var confirmPasswordFieldError: String? by mutableStateOf(null)
        private set
    var formErrorMessage: String? by mutableStateOf(null)

    // Password reset
    var resetEmail: String by mutableStateOf("")
    var isSendingResetLink: Boolean by mutableStateOf(false)
        private set
    var resetEmailFieldError: String? by mutableStateOf(null)
        private set
    var resetErrorMessage: String? by mutableStateOf(null)
    var resetConfirmationMessage: String? by mutableStateOf(null)
        private set

    /**
     * Checks for an already-persisted session at launch. Fast and
     * synchronous under the hood (Parse caches this in shared
     * preferences), but modeled as suspend so AppRoot can show a brief
     * loading state rather than assume it's instantaneous.
     */
    fun refreshSession() {
        val user = authService.currentUser()
        state = if (user != null) AuthenticationState.SignedIn(user) else AuthenticationState.SignedOut
    }

    // MARK: - Sign in with Google

    fun signInWithGoogle(context: Context) {
        if (isGoogleSignInInProgress) return
        googleSignInErrorMessage = null
        viewModelScope.launch {
            isGoogleSignInInProgress = true
            try {
                val credential = googleSignIn.credential(context)
                val user = authService.signInWithGoogle(credential)
                state = AuthenticationState.SignedIn(user)
            } catch (e: GoogleSignInError.Cancelled) {
                // The person dismissed the account picker -- not an error.
            } catch (e: AuthenticationServiceError) {
                googleSignInErrorMessage = e.userMessage
            } catch (e: Exception) {
                googleSignInErrorMessage = "Sign in with Google didn't complete. Please try again."
            } finally {
                isGoogleSignInInProgress = false
            }
        }
    }

    // MARK: - Email / password

    /**
     * Clears form-specific errors and the password fields (but not the
     * email) -- call when switching between Sign In and Create Account, or
     * when presenting the screen fresh.
     */
    fun resetEmailForm() {
        password = ""
        confirmPassword = ""
        isPasswordVisible = false
        emailFieldError = null
        passwordFieldError = null
        confirmPasswordFieldError = null
        formErrorMessage = null
    }

    fun submitEmailForm(onSignedIn: () -> Unit) {
        if (isSubmittingEmailForm) return
        formErrorMessage = null
        if (!validateEmailForm()) return

        viewModelScope.launch {
            isSubmittingEmailForm = true
            try {
                val normalizedEmail = email.trim().lowercase()
                val user = when (emailMode) {
                    EmailAuthMode.SIGN_IN -> authService.signIn(normalizedEmail, password)
                    EmailAuthMode.CREATE_ACCOUNT -> authService.signUp(normalizedEmail, password)
                }
                state = AuthenticationState.SignedIn(user)
                onSignedIn()
            } catch (e: AuthenticationServiceError) {
                formErrorMessage = e.userMessage
            } catch (e: Exception) {
                formErrorMessage = AuthenticationServiceError.Network.userMessage
            } finally {
                isSubmittingEmailForm = false
            }
        }
    }

    private fun validateEmailForm(): Boolean {
        emailFieldError = null
        passwordFieldError = null
        confirmPasswordFieldError = null

        var isValid = true
        val normalizedEmail = email.trim()
        if (!isValidEmail(normalizedEmail)) {
            emailFieldError = "Enter a valid email address."
            isValid = false
        }
        if (password.length < MINIMUM_PASSWORD_LENGTH) {
            passwordFieldError = "Use at least $MINIMUM_PASSWORD_LENGTH characters."
            isValid = false
        }
        if (emailMode == EmailAuthMode.CREATE_ACCOUNT && password.isNotEmpty() && password != confirmPassword) {
            confirmPasswordFieldError = "Passwords don't match."
            isValid = false
        }
        return isValid
    }

    // MARK: - Password reset

    fun resetPasswordResetForm() {
        resetEmailFieldError = null
        resetErrorMessage = null
        resetConfirmationMessage = null
    }

    fun sendPasswordReset() {
        if (isSendingResetLink) return
        resetErrorMessage = null
        resetConfirmationMessage = null
        resetEmailFieldError = null

        val normalizedEmail = resetEmail.trim().lowercase()
        if (!isValidEmail(normalizedEmail)) {
            resetEmailFieldError = "Enter a valid email address."
            return
        }

        viewModelScope.launch {
            isSendingResetLink = true
            try {
                authService.sendPasswordReset(normalizedEmail)
                resetConfirmationMessage = "We sent password-reset instructions if an account exists for that email."
            } catch (e: AuthenticationServiceError) {
                resetErrorMessage = e.userMessage
            } catch (e: Exception) {
                resetErrorMessage = AuthenticationServiceError.Network.userMessage
            } finally {
                isSendingResetLink = false
            }
        }
    }

    // MARK: - Sign out

    /**
     * Clears the local Parse session and returns to the Welcome screen.
     * Never touches case data.
     */
    fun signOut() {
        viewModelScope.launch {
            state = AuthenticationState.EndingSession
            try {
                authService.signOut()
            } catch (e: Exception) {
                // Best-effort, matching the iOS client -- there's nothing
                // meaningful to stay on-screen for on failure.
            }
            state = AuthenticationState.SignedOut
        }
    }

    /**
     * Permanently deletes the signed-in account and its case data (App
     * Store/Play Store account-deletion requirement), then returns to
     * Welcome. Unlike [signOut], a failure here is real and is surfaced to
     * [onError] so the caller stays on-screen and can let the person retry.
     */
    fun deleteAccount(onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authService.deleteAccount()
            } catch (e: AuthenticationServiceError) {
                onError(e.userMessage)
                return@launch
            } catch (e: Exception) {
                onError(AuthenticationServiceError.Network.userMessage)
                return@launch
            }
            state = AuthenticationState.EndingSession
            state = AuthenticationState.SignedOut
        }
    }

    companion object {
        private const val MINIMUM_PASSWORD_LENGTH = 8
        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        private fun isValidEmail(value: String): Boolean =
            value.isNotEmpty() && EMAIL_PATTERN.matches(value)
    }
}
