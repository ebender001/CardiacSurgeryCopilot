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
    /// Every already-answered follow-up question for this case, oldest
    /// first -- empty means none were needed, not that it hasn't loaded
    /// yet (see HeartTeamResponsesView's use of this).
    @Published private(set) var conversation: [ConferenceConversationEntry] = []

    let caseId: String

    init(caseId: String) {
        self.caseId = caseId
    }

    #if DEBUG
    convenience init(caseId: String, previewResponses: HeartTeamResponses, previewConversation: [ConferenceConversationEntry] = []) {
        self.init(caseId: caseId)
        responses = previewResponses
        conversation = previewConversation
    }
    #endif

    /// A no-op if responses are already loaded -- call from `.task` so
    /// returning to this screen (e.g. after backing out and back in)
    /// doesn't repeat the network call.
    func load() async {
        guard responses == nil else { return }
        isLoading = true
        defer { isLoading = false }

        // The follow-up-question history is a supplementary, read-only
        // detail -- its own failure should never block or error out the
        // main heart-team-responses display, so it's fetched separately
        // and swallowed with `try?` rather than joined into the same
        // do/catch below.
        async let conversationFetch: [ConferenceConversationEntry] = {
            (try? await BackendService.getConferenceCase(caseId: caseId))?.conversation ?? []
        }()

        do {
            responses = try await BackendService.getConferenceHeartTeamResponses(caseId: caseId)
            conversation = await conversationFetch
            errorMessage = nil
        } catch {
            errorMessage = (error as? LocalizedError)?.errorDescription ?? "Couldn't load heart team responses. Please try again."
        }
    }
}
