//
//  ConferenceReportViewModel.swift
//  CardiacSurgeryCopilot
//
//  Drives ConferenceReportView. Reached either from an already-`completed`
//  Recent Cases row or from HeartTeamResponsesView's "View Full Report"
//  button on a case that hasn't been finalized yet -- `load()` handles
//  both by fetching the case and finalizing it itself if no report exists
//  yet, so the view never has to know which path got it here.
//

import Combine
import Foundation

@MainActor
final class ConferenceReportViewModel: ObservableObject {
    let caseId: String

    @Published private(set) var report: ConferenceReport?
    @Published private(set) var isLoading = false
    @Published var errorMessage: String?

    init(caseId: String) {
        self.caseId = caseId
    }

    #if DEBUG
    convenience init(caseId: String, previewReport: ConferenceReport) {
        self.init(caseId: caseId)
        report = previewReport
    }
    #endif

    func load() async {
        guard report == nil else { return }
        errorMessage = nil
        isLoading = true
        defer { isLoading = false }

        do {
            let existing = try await BackendService.getConferenceCase(caseId: caseId)
            if let existingReport = existing.report {
                report = existingReport
            } else {
                let finalized = try await BackendService.finalizeConferenceCase(caseId: caseId)
                report = finalized.report
            }
        } catch {
            errorMessage = (error as? LocalizedError)?.errorDescription ?? "Couldn't load the report. Please try again."
        }
    }
}
