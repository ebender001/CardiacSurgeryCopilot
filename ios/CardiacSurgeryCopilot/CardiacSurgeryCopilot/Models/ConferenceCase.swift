//
//  ConferenceCase.swift
//  CardiacSurgeryCopilot
//
//  Client-side representation of a Heart Team case-preparation session,
//  as returned by cscCreateConferenceCase / cscAnswerConferenceQuestion
//  (`{caseId, status, extractedCase, nextQuestion}` -- see backend/
//  README.md). `extractedCase` is a flexible, AI-populated JSON object
//  with no fixed shape; this client doesn't decode it (mirrors MMCoach's
//  MMCase, which never surfaced `extractedCase` to the UI either -- Swift's
//  Decodable simply ignores JSON keys with no matching CodingKey).
//

import Foundation

struct ConferenceCase: Decodable, Identifiable, Hashable {
    let id: String
    var status: ConferenceCaseStatus
    var nextQuestion: ConferenceQuestion?

    private enum CodingKeys: String, CodingKey {
        case id = "caseId"
        case status
        case nextQuestion
    }
}
