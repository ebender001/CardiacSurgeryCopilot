//
//  ConferenceHomeViewModel.swift
//  CardiacSurgeryCopilot
//
//  Drives the Heart Team tab's recent-cases list, the AI-data-consent gate,
//  and the subscription gate in front of "Start a New Case" -- mirrors
//  MMCoach's HomeViewModel.
//

import Combine
import Foundation

@MainActor
final class ConferenceHomeViewModel: ObservableObject {
    @Published private(set) var recentCases: [ConferenceCaseSummary] = []
    @Published private(set) var isLoadingRecentCases = false
    /// Set only when a refresh fails *and* leaves no list to show -- a
    /// background refresh failing while a previously-loaded list is still
    /// on screen just keeps showing that stale list rather than alarming
    /// the trainee over a transient network blip (see ConferenceHomeView).
    @Published private(set) var recentCasesErrorMessage: String?
    /// Bound directly to the paywall sheet's `isPresented` (see
    /// ConferenceHomeView) -- not `private(set)`, since dismissing the
    /// sheet (swipe-down) must be able to set this back to `false` too.
    @Published var isPresentingPaywall = false
    @Published private(set) var isCheckingCaseAccess = false
    /// Set alongside `isPresentingPaywall = false` when the paywall closed
    /// because access was unlocked (vs. the trainee swiping it away).
    /// Consumed by `consumePaywallUnlock()` from the sheet's `onDismiss`,
    /// once the dismissal has actually finished -- see ConferenceHomeView.
    private var didUnlockCaseAccessViaPaywall = false
    /// Bound to the AI-data-disclosure sheet's `isPresented` (see
    /// ConferenceHomeView), same reasoning as `isPresentingPaywall` above.
    @Published var isPresentingAIConsent = false
    /// Mirrors `didUnlockCaseAccessViaPaywall`, but for the consent sheet --
    /// consumed by `consumeAIConsentGranted()` from the sheet's `onDismiss`.
    private var didGrantAIConsentJustNow = false

    private let hiddenCaseIds: HiddenCaseIdsStore
    private let subscriptionService: SubscriptionService
    private let aiConsentStore: AIDataConsentStore

    init(hiddenCaseIds: HiddenCaseIdsStore? = nil,
         subscriptionService: SubscriptionService? = nil,
         aiConsentStore: AIDataConsentStore? = nil) {
        self.hiddenCaseIds = hiddenCaseIds ?? HiddenCaseIdsStore(namespace: "conference")
        self.subscriptionService = subscriptionService ?? StoreKitSubscriptionService.shared
        self.aiConsentStore = aiConsentStore ?? AIDataConsentStore()
    }

    #if DEBUG
    convenience init(previewRecentCases: [ConferenceCaseSummary], subscriptionService: SubscriptionService? = nil) {
        self.init(subscriptionService: subscriptionService)
        recentCases = previewRecentCases
    }
    #endif

    /// Reloads the case list from the backend. Call from `.onAppear` so
    /// the list reflects cases created or progressed elsewhere.
    func refresh() async {
        isLoadingRecentCases = true
        defer { isLoadingRecentCases = false }

        do {
            let hidden = hiddenCaseIds.all()
            recentCases = try await BackendService.listConferenceCases().filter { !hidden.contains($0.id) }
            recentCasesErrorMessage = nil
        } catch {
            if recentCases.isEmpty {
                recentCasesErrorMessage = (error as? LocalizedError)?.errorDescription ?? "Couldn't load your cases. Please try again."
            }
        }
    }

    /// Hides cases at the given offsets from this device's list (e.g. via
    /// swipe-to-delete/EditButton). Only a local, per-device preference --
    /// the underlying case still exists on the backend, and still counts
    /// toward free-case eligibility (see BackendService.checkFreeCaseEligibility).
    func deleteRecentCases(at offsets: IndexSet) {
        for index in offsets {
            hiddenCaseIds.hide(id: recentCases[index].id)
        }
        for index in offsets.sorted(by: >) {
            recentCases.remove(at: index)
        }
    }

    /// The case-access gate behind "Start a New Case": the AI-data-consent
    /// disclosure comes first (it governs whether case content is allowed
    /// to be sent anywhere at all, regardless of subscription status), then
    /// an active subscriber proceeds directly and everyone else sees the
    /// paywall. Entitlement is re-checked fresh against StoreKit every
    /// call, never cached. Guarded against re-entrancy
    /// (`isCheckingCaseAccess`, and bailing if either sheet is already up)
    /// so repeatedly tapping the button never kicks off overlapping checks
    /// or presents a sheet twice. Returns whether the caller should push
    /// `.newCase` now.
    func startNewCase() async -> Bool {
        guard !isCheckingCaseAccess, !isPresentingPaywall, !isPresentingAIConsent else { return false }

        guard aiConsentStore.hasConsented else {
            isPresentingAIConsent = true
            return false
        }

        isCheckingCaseAccess = true
        defer { isCheckingCaseAccess = false }

        if await subscriptionService.hasActiveEntitlement() {
            return true
        }
        isPresentingPaywall = true
        return false
    }

    /// Called when the trainee taps "I Agree & Continue" on the
    /// AI-data-disclosure sheet. Only records consent and flips the
    /// binding that closes the sheet -- deliberately does NOT resume
    /// `startNewCase()` itself, for the same race-avoidance reason as
    /// `paywallDidUnlockAccess()` below.
    func recordAIConsent() {
        aiConsentStore.hasConsented = true
        didGrantAIConsentJustNow = true
        isPresentingAIConsent = false
    }

    /// Call from the AI-consent sheet's `onDismiss`. Returns whether the
    /// sheet closed because consent was just granted (vs. "Not Now" or a
    /// manual swipe-to-dismiss), consuming the flag either way so it can't
    /// fire twice.
    func consumeAIConsentGranted() -> Bool {
        defer { didGrantAIConsentJustNow = false }
        return didGrantAIConsentJustNow
    }

    /// Called once PaywallView confirms the trainee unlocked access
    /// (purchase, restore, or the free case). Only flips the binding that
    /// closes the sheet -- deliberately does NOT push `.newCase` itself.
    /// Pushing here would race the sheet's own dismiss animation (both
    /// mutating navigation state in the same tick); the push instead
    /// happens from the sheet's `onDismiss`, once SwiftUI confirms the
    /// dismissal actually completed (see ConferenceHomeView, `consumePaywallUnlock()`).
    func paywallDidUnlockAccess() {
        didUnlockCaseAccessViaPaywall = true
        isPresentingPaywall = false
    }

    /// Call from the paywall sheet's `onDismiss`. Returns whether the sheet
    /// closed because access was unlocked (vs. a manual swipe-to-dismiss),
    /// consuming the flag either way so it can't fire twice.
    func consumePaywallUnlock() -> Bool {
        defer { didUnlockCaseAccessViaPaywall = false }
        return didUnlockCaseAccessViaPaywall
    }
}
