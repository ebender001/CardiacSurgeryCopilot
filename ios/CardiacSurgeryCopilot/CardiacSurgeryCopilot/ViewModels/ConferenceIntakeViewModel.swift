//
//  ConferenceIntakeViewModel.swift
//  CardiacSurgeryCopilot
//
//  Drives ConferenceIntakeView. No dictation/PHI screening yet (see
//  MMCoach's NewCaseViewModel for that pattern -- DictationController,
//  MedicalDictionaryService, PHIFilterService -- none of which are ported
//  into this app yet); this is a typed-text-only MVP so the create-case
//  round trip can be built and tested before that infra exists.
//

import Combine
import Foundation

@MainActor
final class ConferenceIntakeViewModel: ObservableObject {
    @Published var narrativeText: String
    @Published private(set) var isSubmitting = false
    @Published var errorMessage: String?

    private static let minimumNarrativeLength = 20

    init(narrativeText: String = "") {
        self.narrativeText = narrativeText
    }

    /// Enabled as soon as there's any typed text -- the stricter
    /// `minimumNarrativeLength` check happens on submit, which surfaces a
    /// specific inline message instead of just leaving the button
    /// disabled with no explanation.
    var canContinue: Bool {
        !isSubmitting && !narrativeText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// Submits the narrative and returns the created case, or nil on
    /// failure (in which case `errorMessage` is set and `narrativeText`
    /// is preserved).
    func submit() async -> ConferenceCase? {
        errorMessage = nil
        let trimmed = narrativeText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= Self.minimumNarrativeLength else {
            errorMessage = "Add a bit more detail before continuing."
            return nil
        }

        isSubmitting = true
        defer { isSubmitting = false }

        do {
            return try await BackendService.createConferenceCase(narrative: trimmed)
        } catch {
            errorMessage = (error as? LocalizedError)?.errorDescription ?? "Something went wrong. Please try again."
            return nil
        }
    }
}
