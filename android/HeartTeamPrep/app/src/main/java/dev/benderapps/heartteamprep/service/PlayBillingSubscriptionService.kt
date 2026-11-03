package dev.benderapps.heartteamprep.service

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.parse.Parse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Google Play Billing implementation of [SubscriptionService] -- the only
 * file that touches the billing library. Mirrors `StoreKitSubscriptionService`.
 */
object PlayBillingSubscriptionService : SubscriptionService, PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val connectLock = Mutex()
    private var client: BillingClient? = null

    private val detailsById = mutableMapOf<String, ProductDetails>()
    private val offerTokenById = mutableMapOf<String, String>()

    /** Completed by [onPurchasesUpdated] when the Play purchase sheet finishes. */
    private var pendingPurchase: CompletableDeferred<SubscriptionPurchaseOutcome>? = null

    private suspend fun connectedClient(): BillingClient = connectLock.withLock {
        val existing = client ?: BillingClient.newBuilder(Parse.getApplicationContext())
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
            .also { client = it }
        if (!existing.isReady) {
            val result = suspendCancellableCoroutine<BillingResult> { cont ->
                existing.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (cont.isActive) cont.resume(billingResult)
                    }

                    override fun onBillingServiceDisconnected() {}
                })
            }
            if (result.responseCode != BillingClient.BillingResponseCode.OK) throw SubscriptionServiceError.network
        }
        existing
    }

    override suspend fun loadPlans(): List<SubscriptionPlan> {
        val billing = connectedClient()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                SubscriptionProductId.all.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()
        val result = billing.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) throw SubscriptionServiceError.network
        val plans = (result.productDetailsList ?: emptyList()).mapNotNull(::plan)
        if (plans.isEmpty()) throw SubscriptionServiceError.productsUnavailable
        return plans
    }

    override suspend fun purchase(activity: Activity, productId: String): SubscriptionPurchaseOutcome {
        val billing = connectedClient()
        val details = detailsById[productId]
        val token = offerTokenById[productId]
        if (details == null || token == null) throw SubscriptionServiceError.productsUnavailable

        val outcome = CompletableDeferred<SubscriptionPurchaseOutcome>()
        pendingPurchase = outcome
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(token)
                        .build()
                )
            )
            .build()
        val launch = billing.launchBillingFlow(activity, flow)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchase = null
            throw SubscriptionServiceError.unknown
        }
        // No timeout: the Play sheet is user-paced, and it always reports back.
        return try {
            outcome.await()
        } finally {
            if (pendingPurchase === outcome) pendingPurchase = null
        }
    }

    override suspend fun restorePurchases() {
        // Play has no explicit "sync"; re-reading the account's purchases is the restore.
        queryActivePurchases()
    }

    override suspend fun hasActiveEntitlement(): Boolean = try {
        queryActivePurchases().isNotEmpty()
    } catch (e: SubscriptionServiceError) {
        false
    }

    private suspend fun queryActivePurchases(): List<Purchase> {
        val billing = connectedClient()
        val result = billing.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) throw SubscriptionServiceError.network
        val active = result.purchasesList.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.any(SubscriptionProductId.all::contains)
        }
        active.forEach { acknowledgeIfNeeded(billing, it) }
        return active
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        val outcome = pendingPurchase
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty().filter { it.products.any(SubscriptionProductId.all::contains) }
                scope.launch { client?.let { c -> list.forEach { acknowledgeIfNeeded(c, it) } } }
                val purchased = list.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                val pending = list.any { it.purchaseState == Purchase.PurchaseState.PENDING }
                outcome?.complete(
                    when {
                        purchased -> SubscriptionPurchaseOutcome.SUCCESS
                        pending -> SubscriptionPurchaseOutcome.PENDING
                        else -> SubscriptionPurchaseOutcome.USER_CANCELLED
                    }
                )
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> outcome?.complete(SubscriptionPurchaseOutcome.USER_CANCELLED)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> outcome?.complete(SubscriptionPurchaseOutcome.SUCCESS)
            BillingClient.BillingResponseCode.NETWORK_ERROR,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> outcome?.completeExceptionally(SubscriptionServiceError.network)
            else -> outcome?.completeExceptionally(SubscriptionServiceError.unknown)
        }
    }

    /** Play refunds a purchase that isn't acknowledged within three days. */
    private suspend fun acknowledgeIfNeeded(billing: BillingClient, purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
    }

    private fun plan(details: ProductDetails): SubscriptionPlan? {
        val offers = details.subscriptionOfferDetails ?: return null
        // Prefer the offer carrying a free trial; otherwise the plain base plan.
        val offer = offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } } ?: offers.first()
        val phases = offer.pricingPhases.pricingPhaseList
        val recurring = phases.last()
        val trial = phases.firstOrNull { it.priceAmountMicros == 0L }

        detailsById[details.productId] = details
        offerTokenById[details.productId] = offer.offerToken
        return SubscriptionPlan(
            id = details.productId,
            period = if (details.productId == SubscriptionProductId.annual) SubscriptionPeriod.ANNUAL else SubscriptionPeriod.MONTHLY,
            displayName = details.name,
            displayPrice = recurring.formattedPrice,
            freeTrialDuration = trial?.let { describeBillingPeriod(it.billingPeriod) }
        )
    }
}
