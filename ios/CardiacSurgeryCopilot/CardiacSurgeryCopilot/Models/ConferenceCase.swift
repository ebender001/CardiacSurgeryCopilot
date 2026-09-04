//
//  ConferenceCase.swift
//  CardiacSurgeryCopilot
//
//  Client-side representation of a Heart Team case-preparation session.
//  This is the single Decodable type returned by every case-scoped
//  BackendService call -- createConferenceCase, answerConferenceQuestion,
//  finalizeConferenceCase, and getConferenceCase all return a subset of
//  these same fields (see backend/README.md), so one flexible decoder
//  handles all four responses, mirroring MMCoach's MMCase. `extractedCase`
//  is a flexible, AI-populated JSON object with no fixed shape; this
//  client doesn't decode it -- Swift's Decodable simply ignores JSON keys
//  with no matching CodingKey.
//

import Foundation

struct ConferenceCase: Decodable, Identifiable, Hashable {
    let id: String
    var status: ConferenceCaseStatus
    var nextQuestion: ConferenceQuestion?
    /// Present only once the case is `completed` (from finalizeConferenceCase
    /// / getConferenceCase); `nil` for a case still being collected or
    /// merely ready to finalize.
    var report: ConferenceReport?
    /// Every already-answered follow-up question, oldest first -- only
    /// present on getConferenceCase's full response (empty `[]` from
    /// create/answer/finalize, which don't return it). Empty means no
    /// follow-up was ever needed, not that it hasn't loaded yet -- see
    /// HeartTeamResponsesView's use of this to show that explicitly.
    var conversation: [ConferenceConversationEntry]

    private enum CodingKeys: String, CodingKey {
        case id = "caseId"
        case status
        case nextQuestion
        case report
        case conversation
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        status = try container.decode(ConferenceCaseStatus.self, forKey: .status)
        nextQuestion = try container.decodeIfPresent(ConferenceQuestion.self, forKey: .nextQuestion)
        report = try container.decodeIfPresent(ConferenceReport.self, forKey: .report)
        conversation = try container.decodeIfPresent([ConferenceConversationEntry].self, forKey: .conversation) ?? []
    }

    /// Convenience initializer for previews and tests.
    init(id: String, status: ConferenceCaseStatus, nextQuestion: ConferenceQuestion? = nil, report: ConferenceReport? = nil, conversation: [ConferenceConversationEntry] = []) {
        self.id = id
        self.status = status
        self.nextQuestion = nextQuestion
        self.report = report
        self.conversation = conversation
    }
}
