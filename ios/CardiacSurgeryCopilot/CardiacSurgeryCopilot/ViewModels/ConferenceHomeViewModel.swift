//
//  ConferenceHomeViewModel.swift
//  CardiacSurgeryCopilot
//
//  Drives the Heart Team tab's recent-cases list. No subscription/
//  AI-consent gating yet (see MMCoach's HomeViewModel for that pattern,
//  once a paywall and dictation-consent flow exist here) -- "Start New
//  Case" pushes `.newCase` directly for now.
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

    private let hiddenCaseIds: HiddenCaseIdsStore

    init(hiddenCaseIds: HiddenCaseIdsStore? = nil) {
        self.hiddenCaseIds = hiddenCaseIds ?? HiddenCaseIdsStore(namespace: "conference")
    }

    #if DEBUG
    convenience init(previewRecentCases: [ConferenceCaseSummary]) {
        self.init()
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
    /// the underlying case still exists on the backend.
    func deleteRecentCases(at offsets: IndexSet) {
        for index in offsets {
            hiddenCaseIds.hide(id: recentCases[index].id)
        }
        for index in offsets.sorted(by: >) {
            recentCases.remove(at: index)
        }
    }
}
