//
//  PaywallViewModel.swift
//  CardiacSurgeryCopilot
//
//  Drives PaywallView. Loads the configured subscription plans and owns
//  the purchase/restore state machine. Views never call SubscriptionService
//  directly -- only this. Ported from MMCoach's PaywallViewModel.
//

import Combine
import Foundation
// Only for `StoreKitError.userCancelled`, to tell a dismissed offer-code
// sheet apart from a real failure in `offerCodeRedemptionCompleted(_:)` --
// everything else here still goes through SubscriptionService, never
// StoreKit directly.
import StoreKit

enum PaywallState: Equatable {
    case loadingProducts
    case ready
    case purchasing(productID: String)
    case restoring
    /// Waiting on the entitlement to catch up after the system offer-code
    /// redemption sheet (see PaywallView's `.offerCodeRedemption`) reports
    /// success -- see `offerCodeRedemptionCompleted(_:)`.
    case redeemingOfferCode
    case purchaseFailed(message: String)
    case restoreFailed(message: String)
    /// The initial product request (plus its automatic retries -- see
    /// `attemptLoadPlans()`) never came back with any products. Distinct
    /// from `purchaseFailed` so the paywall can show a dedicated "can't
    /// load at all" screen instead of a purchase-error footnote, and so an
    /// App Store reviewer never gets stuck on an indefinite spinner if
    /// StoreKit is temporarily unavailable.
    case unableToLoadProducts
}

@MainActor
final class PaywallViewModel: ObservableObject {
    @Published private(set) var state: PaywallState = .loadingProducts
    @Published private(set) var plans: [SubscriptionPlan] = []
    /// Flips to `true` once a purchase or a restore has confirmed the
    /// trainee should proceed. `PaywallView` observes this to dismiss
    /// itself and continue to the new-case workflow.
    @Published private(set) var didUnlockAccess = false

    var sortedPlans: [SubscriptionPlan] {
        // Annual first (visually recommended) regardless of the order
        // StoreKit/the mock returns them in.
        plans.sorted { lhs, _ in lhs.period == .annual }
    }

    var isBusy: Bool {
        switch state {
        case .purchasing, .restoring, .redeemingOfferCode:
            return true
        case .loadingProducts, .ready, .purchaseFailed, .restoreFailed, .unableToLoadProducts:
            return false
        }
    }

    private let subscriptionService: SubscriptionService

    /// Automatic retries after the initial product request fails or comes
    /// back empty: 1 initial attempt + 2 retries before giving up and
    /// showing `.unableToLoadProducts`.
    private static let maxProductLoadAttempts = 3
    private static let retryDelay: Duration = .seconds(1.5)
    private var productLoadAttempt = 0

    init(subscriptionService: SubscriptionService? = nil) {
        self.subscriptionService = subscriptionService ?? StoreKitSubscriptionService.shared
    }

    /// Loads the subscription plans. Call once from the sheet's `.task`,
    /// and again from the "Try Again" action on `.unableToLoadProducts` --
    /// both start a fresh retry sequence.
    func load() async {
        state = .loadingProducts
        productLoadAttempt = 0
        await attemptLoadPlans()
    }

    func purchase(_ plan: SubscriptionPlan) async {
        guard !isBusy else { return }
        state = .purchasing(productID: plan.id)
        do {
            let outcome = try await subscriptionService.purchase(productID: plan.id)
            switch outcome {
            case .success:
                await refreshEntitlementAndUnlock()
            case .userCancelled:
                // Not an error -- leave the trainee on the paywall.
                state = .ready
            case .pending:
                // Awaiting approval (e.g. Ask to Buy); nothing more to do
                // here -- the transaction listener finishes it if/when it
                // resolves.
                state = .ready
            }
        } catch {
            state = .purchaseFailed(message: Self.message(for: error))
        }
    }

    func restore() async {
        guard !isBusy else { return }
        state = .restoring
        do {
            try await subscriptionService.restorePurchases()
            if await subscriptionService.hasActiveEntitlement() {
                didUnlockAccess = true
                state = .ready
            } else {
                state = .restoreFailed(message: "No active subscription was found to restore.")
            }
        } catch {
            state = .restoreFailed(message: Self.message(for: error))
        }
    }

    /// Called when PaywallView's `.offerCodeRedemption` sheet finishes.
    /// `.success` here only means the system sheet itself completed --
    /// unlike `purchase(_:)`, which gets the resulting transaction directly
    /// from `product.purchase()`, the transaction from an offer-code
    /// redemption arrives separately via
    /// StoreKitSubscriptionService's `Transaction.updates` listener, so
    /// this polls briefly for the entitlement to catch up rather than
    /// assuming success means unlocked immediately.
    func offerCodeRedemptionCompleted(_ result: Result<Void, Error>) async {
        guard !isBusy else { return }
        switch result {
        case .success:
            state = .redeemingOfferCode
            await pollForEntitlementAfterOfferCodeRedemption()
        case .failure(let error):
            if case StoreKitError.userCancelled = error {
                // Not an error -- leave the trainee on the paywall.
                state = .ready
            } else {
                state = .purchaseFailed(message: Self.message(for: error))
            }
        }
    }

    /// Loads products, retrying automatically on failure or an empty
    /// result (both are treated as "unsuccessful" -- see
    /// `SubscriptionService.loadPlans()`) up to `maxProductLoadAttempts`
    /// times total, ~1.5s apart, before giving up and showing
    /// `.unableToLoadProducts`. Recurses rather than looping so each
    /// attempt is a clean stack frame for the DEBUG logs below.
    private func attemptLoadPlans() async {
        productLoadAttempt += 1
        #if DEBUG
        print("[Paywall] Loading products (attempt \(productLoadAttempt)/\(Self.maxProductLoadAttempts)): \(SubscriptionProductID.all)")
        #endif
        do {
            let loadedPlans = try await subscriptionService.loadPlans()
            guard !loadedPlans.isEmpty else {
                throw SubscriptionServiceError.productsUnavailable
            }
            #if DEBUG
            print("[Paywall] Product load succeeded on attempt \(productLoadAttempt): \(loadedPlans.map(\.id))")
            #endif
            plans = loadedPlans
            state = .ready
        } catch {
            #if DEBUG
            print("[Paywall] Product load attempt \(productLoadAttempt) failed: \(error)")
            #endif
            guard productLoadAttempt < Self.maxProductLoadAttempts else {
                #if DEBUG
                print("[Paywall] Exhausted \(productLoadAttempt) attempts -- entering unable-to-load state")
                #endif
                state = .unableToLoadProducts
                return
            }
            try? await Task.sleep(for: Self.retryDelay)
            guard !Task.isCancelled else { return }
            await attemptLoadPlans()
        }
    }

    private func refreshEntitlementAndUnlock() async {
        if await subscriptionService.hasActiveEntitlement() {
            didUnlockAccess = true
        }
        state = .ready
    }

    /// Up to 4 checks, 1s apart, for `Transaction.updates` to catch up
    /// after a successful offer-code redemption -- see
    /// `offerCodeRedemptionCompleted(_:)`. If the entitlement still hasn't
    /// shown up by the last attempt, this leaves the trainee on the ready
    /// paywall rather than blocking indefinitely; the listener still
    /// unlocks access on its own once the transaction does arrive.
    private static let offerCodeEntitlementPollAttempts = 4
    private static let offerCodeEntitlementPollDelay: Duration = .seconds(1)

    private func pollForEntitlementAfterOfferCodeRedemption() async {
        for attempt in 1...Self.offerCodeEntitlementPollAttempts {
            if await subscriptionService.hasActiveEntitlement() {
                didUnlockAccess = true
                state = .ready
                return
            }
            if attempt < Self.offerCodeEntitlementPollAttempts {
                try? await Task.sleep(for: Self.offerCodeEntitlementPollDelay)
            }
        }
        state = .ready
    }

    private static func message(for error: Error) -> String {
        (error as? LocalizedError)?.errorDescription ?? SubscriptionServiceError.unknown.errorDescription!
    }
}
