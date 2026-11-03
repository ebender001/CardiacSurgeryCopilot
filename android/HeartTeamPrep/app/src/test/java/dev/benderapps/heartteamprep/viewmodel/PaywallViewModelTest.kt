package dev.benderapps.heartteamprep.viewmodel

import android.app.Activity
import dev.benderapps.heartteamprep.service.SubscriptionPeriod
import dev.benderapps.heartteamprep.service.SubscriptionPlan
import dev.benderapps.heartteamprep.service.SubscriptionPurchaseOutcome
import dev.benderapps.heartteamprep.service.SubscriptionService
import dev.benderapps.heartteamprep.service.SubscriptionServiceError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {
    private val monthly = SubscriptionPlan("m", SubscriptionPeriod.MONTHLY, "Monthly", "$1.99", "3 days")
    private val annual = SubscriptionPlan("a", SubscriptionPeriod.ANNUAL, "Annual", "$9.99", "3 days")

    private class FakeService : SubscriptionService {
        var plansResults: MutableList<Result<List<SubscriptionPlan>>> = mutableListOf()
        var loadCalls = 0
        var outcome: Result<SubscriptionPurchaseOutcome> = Result.success(SubscriptionPurchaseOutcome.SUCCESS)
        var entitled = false
        var restoreError: SubscriptionServiceError? = null

        override suspend fun loadPlans(): List<SubscriptionPlan> {
            loadCalls++
            return plansResults.removeAt(0).getOrThrow()
        }

        override suspend fun purchase(activity: Activity, productId: String): SubscriptionPurchaseOutcome = outcome.getOrThrow()
        override suspend fun restorePurchases() { restoreError?.let { throw it } }
        override suspend fun hasActiveEntitlement(): Boolean = entitled
    }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun loadsPlansAnnualFirst() {
        val service = FakeService().apply { plansResults = mutableListOf(Result.success(listOf(monthly, annual))) }
        val vm = PaywallViewModel(service, retryDelayMs = 0)
        vm.load()
        assertEquals(PaywallState.Ready, vm.state)
        assertEquals(listOf("a", "m"), vm.sortedPlans.map { it.id })
    }

    @Test
    fun retriesThenSucceeds() {
        val service = FakeService().apply {
            plansResults = mutableListOf(
                Result.failure(SubscriptionServiceError.network),
                Result.success(listOf(annual))
            )
        }
        val vm = PaywallViewModel(service, retryDelayMs = 0)
        vm.load()
        assertEquals(2, service.loadCalls)
        assertEquals(PaywallState.Ready, vm.state)
    }

    @Test
    fun givesUpAfterThreeAttempts() {
        val service = FakeService().apply {
            plansResults = MutableList(3) { Result.failure(SubscriptionServiceError.network) }
        }
        val vm = PaywallViewModel(service, retryDelayMs = 0)
        vm.load()
        assertEquals(3, service.loadCalls)
        assertEquals(PaywallState.UnableToLoadProducts, vm.state)
    }

    @Test
    fun restoreWithoutSubscriptionReportsNone() {
        val vm = PaywallViewModel(FakeService(), retryDelayMs = 0)
        vm.restore()
        assertEquals(PaywallState.RestoreFailed("No active subscription was found to restore."), vm.state)
        assertFalse(vm.didUnlockAccess)
    }

    @Test
    fun restoreWithSubscriptionUnlocks() {
        val vm = PaywallViewModel(FakeService().apply { entitled = true }, retryDelayMs = 0)
        vm.restore()
        assertTrue(vm.didUnlockAccess)
    }

    @Test
    fun restoreFailureShowsServiceMessage() {
        val vm = PaywallViewModel(FakeService().apply { restoreError = SubscriptionServiceError.network }, retryDelayMs = 0)
        vm.restore()
        assertEquals(PaywallState.RestoreFailed(SubscriptionServiceError.network.userMessage), vm.state)
    }
}
