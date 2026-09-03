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
//  Scoped to account deletion and the two recent-cases lists for now --
//  the rest of the Conference/WWYD case functions (cscCreateConferenceCase,
//  cscCreateWwydCase, etc., see backend/README.md) get their own
//  `ParseCloudable` request types here as each workflow's UI is built.
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
            return "Cardiac Surgery Copilot couldn't reach the server. Check your connection and try again."
        case .decoding:
            return "Cardiac Surgery Copilot couldn't read the server's response. Please try again."
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

    /// Every What Would You Do case the signed-in trainee owns, most
    /// recent first -- the single source of truth for the What Would You
    /// Do tab's Recent Discussions list.
    static func listWwydCases() async throws -> [WwydCaseSummary] {
        try await run(ListWwydCasesFunction()).cases
    }

    /// Permanently deletes the signed-in account and every case/AI-cost
    /// record it owns, across both workflows. Irreversible, and there is
    /// no confirmation step on the backend -- the caller is responsible
    /// for confirming with the person first.
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

private struct GetConferenceHeartTeamResponsesFunction: ParseCloudable {
    typealias ReturnType = HeartTeamResponses
    var functionJobName = "cscGetConferenceHeartTeamResponses"
    var caseId: String
}

private struct ListConferenceCasesResponse: Decodable {
    let cases: [ConferenceCaseSummary]
}

private struct ListConferenceCasesFunction: ParseCloudable {
    typealias ReturnType = ListConferenceCasesResponse
    var functionJobName = "cscListConferenceCases"
}

private struct ListWwydCasesResponse: Decodable {
    let cases: [WwydCaseSummary]
}

private struct ListWwydCasesFunction: ParseCloudable {
    typealias ReturnType = ListWwydCasesResponse
    var functionJobName = "cscListWwydCases"
}

private struct DeleteAccountResponse: Decodable {
    let deleted: Bool
}

private struct DeleteAccountFunction: ParseCloudable {
    typealias ReturnType = DeleteAccountResponse
    var functionJobName = "cscDeleteAccount"
}
