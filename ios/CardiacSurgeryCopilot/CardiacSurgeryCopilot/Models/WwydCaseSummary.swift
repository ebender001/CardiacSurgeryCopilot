//
//  WwydCaseSummary.swift
//  CardiacSurgeryCopilot
//
//  One row in the What Would You Do tab's "Recent Discussions" list, as
//  returned by cscListWwydCases. The backend is the single source of
//  truth for which cases exist and their status -- this client keeps no
//  local index of them (see HiddenCaseIdsStore for the one thing that's
//  still local: which case ids this device has swiped away from the list).
//

import Foundation

/// Mirrors the backend's `active` / `archived` state (see
/// `schemas/wwydCaseStatus.js`).
enum WwydCaseStatus: String, Codable, Hashable {
    case active
    case archived
}

struct WwydCaseSummary: Decodable, Identifiable, Hashable {
    let id: String
    var title: String
    let createdAt: Date
    var status: WwydCaseStatus

    private enum CodingKeys: String, CodingKey {
        case id = "caseId"
        case title
        case createdAt
        case status
    }
}
