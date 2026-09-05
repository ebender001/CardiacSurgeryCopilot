//
//  BackendService.swift
//  CardiacSurgeryCopilot
//
//  The single interface between the app and the Back4App Cloud Functions.
//  This is the ONLY file that talks to Parse-Swift Cloud Function calls.
//  View models never call Parse Cloud Functions directly, and no AI/
//  clinical logic lives here or anywhere on the client -- all of that is
//  the backend's responsibility.
//
//  Flow: View -> ViewModel -> BackendService -> Back4App Cloud Function.
//
//  Scoped to what the app's UI currently needs -- the rest of the
//  Conference case functions (see backend/README.md) get their own
//  `ParseCloudable` request types here as each screen is built.
//

import Foundation
import ParseSwift

/// User-facing errors surfaced by `BackendService`. Raw Parse/server error
/// text is never shown to the trainee -- only these concise messages.
enum BackendError: LocalizedError, Equatable {
    case validation(String)
    case notFound
    case invalidState(String)
    /// The caller's Parse session is missing/expired/revoked -- every
    /// `csc*` Cloud Function requires one (see backend `requireUser`).
    /// `BackendService.run(_:)` also broadcasts `.sessionExpired` via
    /// `NotificationCenter` when this is thrown, which is what lets
    /// `AuthenticationViewModel` force a clean sign-out/re-auth instead of
    /// the app being stuck retrying calls a dead session can never pass.
    case sessionExpired
    case server
    case network
    case decoding

    var errorDescription: String? {
        switch self {
        case .validation(let message):
            return message
        case .notFound:
            return "This item is no longer available."
        case .invalidState(let message):
            return message
        case .sessionExpired:
            return "Your session expired. Please sign in again."
        case .server:
            return "Something went wrong. Please try again."
        case .network:
            return "Heart Team Copilot couldn't reach the server. Check your connection and try again."
        case .decoding:
            return "Heart Team Copilot couldn't read the server's response. Please try again."
        }
    }

    fileprivate init(parseError: ParseError) {
        switch parseError.code {
        case .validationFailed:
            self = .validation(parseError.message)
        case .objectNotFound:
            self = .notFound
        case .operationForbidden:
            self = .invalidState(parseError.message)
        case .invalidSessionToken:
            self = .sessionExpired
        case .connectionFailed, .timeout:
            self = .network
        default:
            self = .server
        }
    }
}

extension Notification.Name {
    /// Posted by `BackendService` whenever a Cloud Function call fails
    /// because the local session is no longer valid server-side.
    /// `AuthenticationViewModel` is the sole subscriber -- it forces a
    /// local sign-out and returns to the Welcome screen so the trainee
    /// re-authenticates instead of hitting the same dead-session error
    /// repeatedly on every subsequent action.
    static let cscSessionExpired = Notification.Name("CardiacSurgeryCopilot.sessionExpired")
}

enum BackendService {
    /// Stores the narrative, extracts structured case information, and
    /// either asks one follow-up question or determines the case is
    /// already `ready_to_finalize`. `narrative` must be non-empty and
    /// reasonably descriptive -- the backend rejects a bare few words.
    static func createConferenceCase(narrative: String) async throws -> ConferenceCase {
        try await run(CreateConferenceCaseFunction(narrative: narrative))
    }

    /// Submits an answer to the case's current follow-up question. The
    /// backend either asks one more question or moves the case to
    /// `ready_to_finalize` -- the client never decides which.
    static func answerConferenceQuestion(caseId: String, questionId: String, answer: String) async throws -> ConferenceCase {
        try await run(AnswerConferenceQuestionFunction(caseId: caseId, questionId: questionId, answer: answer))
    }

    /// Generates and persists the nine-section report, moving the case to
    /// `completed`. Only valid once the case needs no more follow-up
    /// questions -- the backend rejects this while `status ==
    /// collecting_information`. Idempotent-in-effect but not cheap: calling
    /// this on an already-`completed` case is rejected too (see
    /// ConferenceReportViewModel, which only calls this once per case).
    static func finalizeConferenceCase(caseId: String) async throws -> ConferenceCase {
        try await run(FinalizeConferenceCaseFunction(caseId: caseId))
    }

    /// The full current state of one case -- used to resume an
    /// in-progress case (interview or report) reached from Recent Cases,
    /// where nothing about it is known client-side beyond its id/status.
    static func getConferenceCase(caseId: String) async throws -> ConferenceCase {
        try await run(GetConferenceCaseFunction(caseId: caseId))
    }

    /// Live PubMed lookup for one evidence/guideline topic identified on
    /// the case's report -- not itself persisted back onto the report; the
    /// trainee reviews results and picks their own sources, same reasoning
    /// as `getHeartTeamRoleEvidence`. `caseId` lets the backend verify
    /// ownership, cache the result on the case, and roll this call's AI
    /// cost into that case's running total.
    static func findConferenceReferences(topic: String, searchIntent: String, caseId: String) async throws -> [PubMedArticle] {
        try await run(FindConferenceReferencesFunction(topic: topic, searchIntent: searchIntent, caseId: caseId)).results
    }

    /// Every Heart Team case the signed-in trainee owns, most recent
    /// first -- the single source of truth for the Heart Team tab's
    /// Recent Cases list.
    static func listConferenceCases() async throws -> [ConferenceCaseSummary] {
        try await run(ListConferenceCasesFunction()).cases
    }

    /// The case's three heart-team-member responses (surgeon,
    /// non-interventional cardiologist, interventional cardiologist),
    /// generated and cached server-side on first call. Only valid once
    /// the case needs no more follow-up questions -- the backend rejects
    /// this while `status == collecting_information`.
    static func getConferenceHeartTeamResponses(caseId: String) async throws -> HeartTeamResponses {
        try await run(GetConferenceHeartTeamResponsesFunction(caseId: caseId))
    }

    /// One heart-team role's pro/con PubMed evidence for its stated
    /// recommendation, generated and cached server-side on first call.
    /// Requires `getConferenceHeartTeamResponses` to have already run for
    /// this case -- the backend rejects this otherwise.
    static func getHeartTeamRoleEvidence(caseId: String, role: HeartTeamRole) async throws -> HeartTeamRoleEvidence {
        try await run(GetHeartTeamRoleEvidenceFunction(caseId: caseId, role: role.rawValue))
    }

    /// Corrects one freshly-dictated narrative segment. `priorNarrative` is
    /// passed only as context for disambiguation -- the backend does not
    /// re-edit it, and only `correctedSegment` should be appended locally.
    /// Deliberately does not require an authenticated session (mirrors the
    /// backend's `cscCorrectDictation`, which isn't gated by `requireUser`)
    /// -- dictation correction has no per-user data of its own.
    static func correctDictation(priorNarrative: String, newSegment: String) async throws -> CorrectedDictationSegment {
        try await run(CorrectDictationFunction(priorNarrative: priorNarrative, newSegment: newSegment))
    }

    /// Permanently deletes the signed-in account and every case/AI-cost
    /// record it owns. Irreversible, and there is no confirmation step on
    /// the backend -- the caller is responsible for confirming with the
    /// person first.
    static func deleteAccount() async throws {
        _ = try await run(DeleteAccountFunction())
    }

    private static func run<Function: ParseCloudable>(_ function: Function) async throws -> Function.ReturnType {
        do {
            return try await function.runFunction()
        } catch let error as ParseError {
            let mapped = BackendError(parseError: error)
            if mapped == .sessionExpired {
                NotificationCenter.default.post(name: .cscSessionExpired, object: nil)
            }
            throw mapped
        } catch is DecodingError {
            throw BackendError.decoding
        } catch {
            throw BackendError.network
        }
    }
}

// MARK: - Cloud Function request definitions
//
// Each Back4App Cloud Function gets one small ParseCloudable request type.
// Any stored property besides `functionJobName` is sent as a parameter.

private struct CreateConferenceCaseFunction: ParseCloudable {
    typealias ReturnType = ConferenceCase
    var functionJobName = "cscCreateConferenceCase"
    var narrative: String
}

private struct AnswerConferenceQuestionFunction: ParseCloudable {
    typealias ReturnType = ConferenceCase
    var functionJobName = "cscAnswerConferenceQuestion"
    var caseId: String
    var questionId: String
    var answer: String
}

private struct FinalizeConferenceCaseFunction: ParseCloudable {
    typealias ReturnType = ConferenceCase
    var functionJobName = "cscFinalizeConferenceCase"
    var caseId: String
}

private struct GetConferenceCaseFunction: ParseCloudable {
    typealias ReturnType = ConferenceCase
    var functionJobName = "cscGetConferenceCase"
    var caseId: String
}

private struct FindConferenceReferencesResponse: Decodable {
    let topic: String
    let results: [PubMedArticle]
}

private struct FindConferenceReferencesFunction: ParseCloudable {
    typealias ReturnType = FindConferenceReferencesResponse
    var functionJobName = "cscFindConferenceReferences"
    var topic: String
    var searchIntent: String
    var caseId: String
}

private struct GetConferenceHeartTeamResponsesFunction: ParseCloudable {
    typealias ReturnType = HeartTeamResponses
    var functionJobName = "cscGetConferenceHeartTeamResponses"
    var caseId: String
}

private struct GetHeartTeamRoleEvidenceFunction: ParseCloudable {
    typealias ReturnType = HeartTeamRoleEvidence
    var functionJobName = "cscGetHeartTeamRoleEvidence"
    var caseId: String
    var role: String
}

private struct ListConferenceCasesResponse: Decodable {
    let cases: [ConferenceCaseSummary]
}

private struct ListConferenceCasesFunction: ParseCloudable {
    typealias ReturnType = ListConferenceCasesResponse
    var functionJobName = "cscListConferenceCases"
}

private struct CorrectDictationFunction: ParseCloudable {
    typealias ReturnType = CorrectedDictationSegment
    var functionJobName = "cscCorrectDictation"
    var priorNarrative: String
    var newSegment: String
}

private struct DeleteAccountResponse: Decodable {
    let deleted: Bool
}

private struct DeleteAccountFunction: ParseCloudable {
    typealias ReturnType = DeleteAccountResponse
    var functionJobName = "cscDeleteAccount"
}
