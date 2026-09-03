//
//  WwydHomeViewModel.swift
//  CardiacSurgeryCopilot
//
//  Drives the What Would You Do tab's recent-discussions list. Mirrors
//  ConferenceHomeViewModel -- see that file for the gating-not-built-yet
//  note.
//

import Combine
import Foundation

@MainActor
final class WwydHomeViewModel: ObservableObject {
    @Published private(set) var recentCases: [WwydCaseSummary] = []
    @Published private(set) var isLoadingRecentCases = false
    @Published private(set) var recentCasesErrorMessage: String?

    private let hiddenCaseIds: HiddenCaseIdsStore

    init(hiddenCaseIds: HiddenCaseIdsStore? = nil) {
        self.hiddenCaseIds = hiddenCaseIds ?? HiddenCaseIdsStore(namespace: "wwyd")
    }

    #if DEBUG
    convenience init(previewRecentCases: [WwydCaseSummary]) {
        self.init()
        recentCases = previewRecentCases
    }
    #endif

    func refresh() async {
        isLoadingRecentCases = true
        defer { isLoadingRecentCases = false }

        do {
            let hidden = hiddenCaseIds.all()
            recentCases = try await BackendService.listWwydCases().filter { !hidden.contains($0.id) }
            recentCasesErrorMessage = nil
        } catch {
            if recentCases.isEmpty {
                recentCasesErrorMessage = (error as? LocalizedError)?.errorDescription ?? "Couldn't load your discussions. Please try again."
            }
        }
    }

    func deleteRecentCases(at offsets: IndexSet) {
        for index in offsets {
            hiddenCaseIds.hide(id: recentCases[index].id)
        }
        for index in offsets.sorted(by: >) {
            recentCases.remove(at: index)
        }
    }
}
