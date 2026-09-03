//
//  HeartTeamResponsesViewModel.swift
//  CardiacSurgeryCopilot
//
//  Drives HeartTeamResponsesView: loads (and caches locally, since the
//  backend already caches per-case) the three responses, and tracks which
//  role's response is currently selected.
//

import Combine
import Foundation

@MainActor
final class HeartTeamResponsesViewModel: ObservableObject {
    @Published private(set) var responses: HeartTeamResponses?
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?
    @Published var selectedRole: HeartTeamRole = .surgeon

    let caseId: String

    init(caseId: String) {
        self.caseId = caseId
    }

    #if DEBUG
    convenience init(caseId: String, previewResponses: HeartTeamResponses) {
        self.init(caseId: caseId)
        responses = previewResponses
    }
    #endif

    /// A no-op if responses are already loaded -- call from `.task` so
    /// returning to this screen (e.g. after backing out and back in)
    /// doesn't repeat the network call.
    func load() async {
        guard responses == nil else { return }
        isLoading = true
        defer { isLoading = false }

        do {
            responses = try await BackendService.getConferenceHeartTeamResponses(caseId: caseId)
            errorMessage = nil
        } catch {
            errorMessage = (error as? LocalizedError)?.errorDescription ?? "Couldn't load heart team responses. Please try again."
        }
    }
}
