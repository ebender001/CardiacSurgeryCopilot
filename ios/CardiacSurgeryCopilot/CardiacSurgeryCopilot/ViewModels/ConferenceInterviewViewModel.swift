//
//  ConferenceInterviewViewModel.swift
//  CardiacSurgeryCopilot
//
//  Owns the one-question-at-a-time follow-up loop. The client never
//  decides whether enough information has been collected -- it only
//  displays whatever `status`/`nextQuestion` the backend returns and
//  reacts to it. No dictation/PHI screening yet, same as
//  ConferenceIntakeViewModel -- this is a typed-text-only MVP.
//

import Combine
import Foundation

@MainActor
final class ConferenceInterviewViewModel: ObservableObject {
    let caseId: String

    @Published private(set) var status: ConferenceCaseStatus
    @Published private(set) var currentQuestion: ConferenceQuestion?
    @Published var answerText = ""
    @Published private(set) var isLoadingCase: Bool
    @Published private(set) var isSubmittingAnswer = false
    @Published var errorMessage: String?

    /// - Parameter initialCase: Pass the case snapshot already returned by
    ///   createConferenceCase/answerConferenceQuestion when navigating here
    ///   right after that call, so no extra network round trip is needed.
    ///   Pass `nil` when resuming an in-progress case from Recent Cases;
    ///   the view model fetches current state via `loadIfNeeded()`.
    init(caseId: String, initialCase: ConferenceCase?) {
        self.caseId = caseId
        self.status = initialCase?.status ?? .collectingInformation
        self.currentQuestion = initialCase?.nextQuestion
        self.isLoadingCase = initialCase == nil
    }

    var canSubmitAnswer: Bool {
        !isSubmittingAnswer && !answerText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    func loadIfNeeded() async {
        guard isLoadingCase else { return }
        do {
            let result = try await BackendService.getConferenceCase(caseId: caseId)
            apply(result)
        } catch {
            errorMessage = Self.message(for: error)
        }
        isLoadingCase = false
    }

    func submitAnswer() async {
        guard let question = currentQuestion else { return }
        let trimmed = answerText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            errorMessage = "Enter an answer before submitting."
            return
        }

        errorMessage = nil
        isSubmittingAnswer = true
        defer { isSubmittingAnswer = false }

        do {
            let result = try await BackendService.answerConferenceQuestion(caseId: caseId, questionId: question.id, answer: trimmed)
            apply(result)
            answerText = ""
        } catch {
            // Preserve answerText so the trainee doesn't lose what they typed.
            errorMessage = Self.message(for: error)
        }
    }

    private func apply(_ result: ConferenceCase) {
        status = result.status
        currentQuestion = result.nextQuestion
    }

    private static func message(for error: Error) -> String {
        (error as? LocalizedError)?.errorDescription ?? "Something went wrong. Please try again."
    }
}
