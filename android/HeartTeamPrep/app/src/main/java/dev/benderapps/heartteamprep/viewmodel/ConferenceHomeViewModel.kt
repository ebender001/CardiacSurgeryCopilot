package dev.benderapps.heartteamprep.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.benderapps.heartteamprep.model.ConferenceCaseSummary
import dev.benderapps.heartteamprep.service.AIDataConsentStore
import dev.benderapps.heartteamprep.service.BackendError
import dev.benderapps.heartteamprep.service.BackendService
import dev.benderapps.heartteamprep.service.AuthenticationService
import dev.benderapps.heartteamprep.service.HiddenCaseIdsStore
import dev.benderapps.heartteamprep.service.ParseAuthenticationService
import dev.benderapps.heartteamprep.service.PlayBillingSubscriptionService
import dev.benderapps.heartteamprep.service.SubscriptionService
import kotlinx.coroutines.launch

/**
 * Drives the home screen's recent-cases list and the AI-data-consent gate
 * and subscription gates in front of "Start a New Case". Mirrors the iOS
 * `ConferenceHomeViewModel`.
 */
class ConferenceHomeViewModel(
    private val hiddenCaseIds: HiddenCaseIdsStore = HiddenCaseIdsStore(namespace = "conference"),
    private val aiConsentStore: AIDataConsentStore = AIDataConsentStore(),
    private val subscriptionService: SubscriptionService = PlayBillingSubscriptionService,
    private val authService: AuthenticationService = ParseAuthenticationService()
) : ViewModel() {

    var recentCases: List<ConferenceCaseSummary> by mutableStateOf(emptyList())
        private set
    var isLoadingRecentCases: Boolean by mutableStateOf(false)
        private set

    /**
     * Set only when a refresh fails *and* leaves no list to show -- a
     * background refresh failing while a previously-loaded list is still on
     * screen keeps showing that stale list rather than alarming the trainee
     * over a transient network blip.
     */
    var recentCasesErrorMessage: String? by mutableStateOf(null)
        private set

    var isPresentingAIConsent: Boolean by mutableStateOf(false)

    /** Reloads the case list from the backend; call whenever the screen becomes visible again. */
    fun refresh() {
        viewModelScope.launch {
            isLoadingRecentCases = true
            try {
                val hidden = hiddenCaseIds.all()
                recentCases = BackendService.listConferenceCases().filter { it.id !in hidden }
                recentCasesErrorMessage = null
            } catch (e: Exception) {
                if (recentCases.isEmpty()) {
                    recentCasesErrorMessage =
                        (e as? BackendError)?.userMessage ?: "Couldn't load your cases. Please try again."
                }
            } finally {
                isLoadingRecentCases = false
            }
        }
    }

    /**
     * Hides a case from this device's list (swipe-to-dismiss). Only a local
     * per-device preference -- the underlying case still exists on the backend.
     */
    fun hideRecentCase(id: String) {
        hiddenCaseIds.hide(id)
        recentCases = recentCases.filterNot { it.id == id }
    }

    var isPresentingPaywall: Boolean by mutableStateOf(false)
    private var isCheckingCaseAccess = false

    /**
     * The gate behind "Start a New Case": the AI-data-consent disclosure
     * comes first, since it governs whether case content may be sent
     * anywhere at all; then an active subscriber proceeds and everyone else
     * sees the paywall. Entitlement is re-checked against Play every time.
     * [onProceed] runs only when the caller should open the New Case screen.
     */
    fun startNewCase(onProceed: () -> Unit) {
        if (isCheckingCaseAccess || isPresentingPaywall || isPresentingAIConsent) return
        if (!aiConsentStore.hasConsented) {
            isPresentingAIConsent = true
            return
        }
        isCheckingCaseAccess = true
        viewModelScope.launch {
            val subscribed = try {
                // The app-store review account can't buy a subscription.
                authService.currentUser()?.isReviewer == true ||
                    subscriptionService.hasActiveEntitlement()
            } finally {
                isCheckingCaseAccess = false
            }
            if (subscribed) onProceed() else isPresentingPaywall = true
        }
    }

    /** Records consent and closes the disclosure; the caller then re-runs [startNewCase]. */
    fun recordAIConsent() {
        aiConsentStore.hasConsented = true
        isPresentingAIConsent = false
    }
}
