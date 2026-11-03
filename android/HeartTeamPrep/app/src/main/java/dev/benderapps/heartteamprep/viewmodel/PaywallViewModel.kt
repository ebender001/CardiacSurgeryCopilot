package dev.benderapps.heartteamprep.viewmodel

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.benderapps.heartteamprep.service.PlayBillingSubscriptionService
import dev.benderapps.heartteamprep.service.SubscriptionPeriod
import dev.benderapps.heartteamprep.service.SubscriptionPlan
import dev.benderapps.heartteamprep.service.SubscriptionPurchaseOutcome
import dev.benderapps.heartteamprep.service.SubscriptionService
import dev.benderapps.heartteamprep.service.SubscriptionServiceError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface PaywallState {
    data object LoadingProducts : PaywallState
    data object Ready : PaywallState
    data class Purchasing(val productId: String) : PaywallState
    data object Restoring : PaywallState
    data class PurchaseFailed(val message: String) : PaywallState
    data class RestoreFailed(val message: String) : PaywallState
    data object UnableToLoadProducts : PaywallState
}

/** Drives the paywall. Mirrors the iOS `PaywallViewModel`. */
class PaywallViewModel(
    private val service: SubscriptionService = PlayBillingSubscriptionService,
    private val retryDelayMs: Long = 1_500
) : ViewModel() {

    var state: PaywallState by mutableStateOf(PaywallState.LoadingProducts)
        private set
    var plans: List<SubscriptionPlan> by mutableStateOf(emptyList())
        private set

    /** Flips true once a purchase or restore confirms an active subscription. */
    var didUnlockAccess: Boolean by mutableStateOf(false)
        private set

    /** Annual first, matching the "Best Value" emphasis. */
    val sortedPlans: List<SubscriptionPlan>
        get() = plans.sortedBy { if (it.period == SubscriptionPeriod.ANNUAL) 0 else 1 }

    val isBusy: Boolean
        get() = state is PaywallState.Purchasing || state is PaywallState.Restoring

    fun load() {
        viewModelScope.launch {
            state = PaywallState.LoadingProducts
            repeat(MAX_LOAD_ATTEMPTS) { attempt ->
                try {
                    val loaded = service.loadPlans()
                    if (loaded.isEmpty()) throw SubscriptionServiceError.productsUnavailable
                    plans = loaded
                    state = PaywallState.Ready
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (attempt < MAX_LOAD_ATTEMPTS - 1) delay(retryDelayMs)
                }
            }
            state = PaywallState.UnableToLoadProducts
        }
    }

    fun purchase(plan: SubscriptionPlan, activity: Activity) {
        if (isBusy) return
        viewModelScope.launch {
            state = PaywallState.Purchasing(plan.id)
            try {
                when (service.purchase(activity, plan.id)) {
                    SubscriptionPurchaseOutcome.SUCCESS -> {
                        if (service.hasActiveEntitlement()) didUnlockAccess = true
                        state = PaywallState.Ready
                    }
                    SubscriptionPurchaseOutcome.USER_CANCELLED, SubscriptionPurchaseOutcome.PENDING -> state = PaywallState.Ready
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = PaywallState.PurchaseFailed(messageFor(e))
            }
        }
    }

    fun restore() {
        if (isBusy) return
        viewModelScope.launch {
            state = PaywallState.Restoring
            try {
                service.restorePurchases()
                if (service.hasActiveEntitlement()) {
                    didUnlockAccess = true
                    state = PaywallState.Ready
                } else {
                    state = PaywallState.RestoreFailed("No active subscription was found to restore.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = PaywallState.RestoreFailed(messageFor(e))
            }
        }
    }

    private fun messageFor(e: Exception): String =
        (e as? SubscriptionServiceError)?.userMessage ?: SubscriptionServiceError.unknown.userMessage

    private companion object {
        const val MAX_LOAD_ATTEMPTS = 3
    }
}
