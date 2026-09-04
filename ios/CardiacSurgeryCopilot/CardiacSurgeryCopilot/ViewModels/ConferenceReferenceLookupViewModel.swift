//
//  ConferenceReferenceLookupViewModel.swift
//  CardiacSurgeryCopilot
//
//  Drives ConferenceReferenceLookupView: an on-demand PubMed search for
//  one evidence/guideline topic from the report, triggered by tapping a
//  topic in ConferenceReportView. The backend caches this per-case/topic,
//  so a no-op here (guarding on `results == nil`) just avoids a redundant
//  round trip on a screen the trainee has already visited this session.
//

import Combine
import Foundation

@MainActor
final class ConferenceReferenceLookupViewModel: ObservableObject {
    let caseId: String
    let topic: String
    let searchIntent: String

    @Published private(set) var results: [PubMedArticle]?
    @Published private(set) var isLoading = false
    @Published var errorMessage: String?

    init(caseId: String, topic: String, searchIntent: String) {
        self.caseId = caseId
        self.topic = topic
        self.searchIntent = searchIntent
    }

    #if DEBUG
    convenience init(caseId: String, topic: String, searchIntent: String, previewResults: [PubMedArticle]) {
        self.init(caseId: caseId, topic: topic, searchIntent: searchIntent)
        results = previewResults
    }
    #endif

    func search() async {
        guard results == nil else { return }
        errorMessage = nil
        isLoading = true
        defer { isLoading = false }

        do {
            results = try await BackendService.findConferenceReferences(topic: topic, searchIntent: searchIntent, caseId: caseId)
        } catch {
            errorMessage = (error as? LocalizedError)?.errorDescription ?? "Couldn't search PubMed. Please try again."
        }
    }
}
