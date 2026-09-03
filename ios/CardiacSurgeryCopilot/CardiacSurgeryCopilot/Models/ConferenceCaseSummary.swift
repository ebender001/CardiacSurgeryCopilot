//
//  ConferenceCaseSummary.swift
//  CardiacSurgeryCopilot
//
//  One row in the Heart Team tab's "Recent Cases" list, as returned
//  by cscListConferenceCases. The backend is the single source of truth
//  for which cases exist and their status -- this client keeps no local
//  index of them (see HiddenCaseIdsStore for the one thing that's still
//  local: which case ids this device has swiped away from the list).
//

import Foundation

/// Mirrors the backend's `collecting_information` / `ready_to_finalize` /
/// `completed` state machine (see `schemas/conferenceCaseStatus.js`).
enum ConferenceCaseStatus: String, Codable, Hashable {
    case collectingInformation = "collecting_information"
    case readyToFinalize = "ready_to_finalize"
    case completed = "completed"
}

struct ConferenceCaseSummary: Decodable, Identifiable, Hashable {
    let id: String
    var title: String
    let createdAt: Date
    var status: ConferenceCaseStatus

    private enum CodingKeys: String, CodingKey {
        case id = "caseId"
        case title
        case createdAt
        case status
    }
}
