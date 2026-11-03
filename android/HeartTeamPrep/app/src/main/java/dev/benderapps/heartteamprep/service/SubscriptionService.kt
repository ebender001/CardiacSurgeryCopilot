package dev.benderapps.heartteamprep.service

import android.app.Activity

/**
 * The Play Console product ids for the two auto-renewing subscriptions.
 * They must match the Play Console exactly (Play ids are lowercase, so they
 * differ in spelling from the iOS ids). See PLAY_BILLING.md for the setup.
 */
object SubscriptionProductId {
    const val monthly = "heartteamprep_monthly"
    const val annual = "heartteamprep_annual"
    val all = listOf(annual, monthly)
}

enum class SubscriptionPeriod { MONTHLY, ANNUAL }

/** Display-facing view of a Play product -- views never touch Play Billing types. */
data class SubscriptionPlan(
    val id: String,
    val period: SubscriptionPeriod,
    val displayName: String,
    /** The real, localized Play price for one period -- never hard-coded. */
    val displayPrice: String,
    /** E.g. "3 days"; null unless Play reports a free-trial phase for this plan. */
    val freeTrialDuration: String?
)

enum class SubscriptionPurchaseOutcome {
    SUCCESS,
    USER_CANCELLED,

    /** Awaiting approval or a slow payment method -- not an error. */
    PENDING
}

/** A failure with a message that is safe to show the trainee (raw Play text never is). */
class SubscriptionServiceError(val userMessage: String) : Exception(userMessage) {
    companion object {
        val productsUnavailable = SubscriptionServiceError("Subscription plans aren't available right now. Please try again.")
        val network = SubscriptionServiceError("Heart Team Prep couldn't reach Google Play. Check your connection and try again.")
        val unknown = SubscriptionServiceError("Something went wrong with that purchase. Please try again.")
    }
}

/** The single interface between the app and Google Play Billing; mirrors the iOS `SubscriptionService`. */
interface SubscriptionService {
    suspend fun loadPlans(): List<SubscriptionPlan>
    suspend fun purchase(activity: Activity, productId: String): SubscriptionPurchaseOutcome

    /** Re-syncs with Google Play. Only called from an explicit "Restore Purchases" action. */
    suspend fun restorePurchases()

    /** Whether the signed-in Google account currently holds an active subscription. Always checked fresh. */
    suspend fun hasActiveEntitlement(): Boolean
}

/** "P3D" -> "3 days", "P1W" -> "1 week"; null if the period can't be read. */
internal fun describeBillingPeriod(iso: String): String? {
    val match = Regex("""P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?""").matchEntire(iso) ?: return null
    val units = listOf("year", "month", "week", "day")
    for ((index, unit) in units.withIndex()) {
        val count = match.groupValues[index + 1].toIntOrNull() ?: continue
        if (count > 0) return "$count ${if (count == 1) unit else unit + "s"}"
    }
    return null
}
