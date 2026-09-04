//
//  HeartTeamResponses.swift
//  CardiacSurgeryCopilot
//
//  Three distinct, in-character heart-team member takes on the same
//  Conference case, as returned by cscGetConferenceHeartTeamResponses
//  once a case needs no more follow-up questions. The trainee picks one
//  to read and can switch freely between all three -- see
//  HeartTeamResponsesView.
//

import Foundation

enum HeartTeamRole: String, CaseIterable, Identifiable, Hashable, Decodable {
    case surgeon
    case nonInterventionalCardiologist
    case interventionalCardiologist

    var id: String { rawValue }

    /// Full label, shown as the response card's header.
    var displayName: String {
        switch self {
        case .surgeon: "Cardiac Surgeon"
        case .nonInterventionalCardiologist: "Non-Interventional Cardiologist"
        case .interventionalCardiologist: "Interventional Cardiologist"
        }
    }

    /// Shorter label for the role-selector chip, where the full name
    /// would wrap awkwardly at chip width.
    var shortName: String {
        switch self {
        case .surgeon: "Surgeon"
        case .nonInterventionalCardiologist: "Non-Interventional"
        case .interventionalCardiologist: "Interventional"
        }
    }

    var icon: String {
        switch self {
        case .surgeon: "cross.case.fill"
        case .nonInterventionalCardiologist: "heart.text.square.fill"
        case .interventionalCardiologist: "bolt.heart.fill"
        }
    }
}

struct HeartTeamResponse: Decodable, Hashable {
    let recommendation: String
    let rationale: String
}

struct HeartTeamResponses: Decodable, Hashable {
    let caseId: String
    let surgeon: HeartTeamResponse
    let nonInterventionalCardiologist: HeartTeamResponse
    let interventionalCardiologist: HeartTeamResponse

    subscript(role: HeartTeamRole) -> HeartTeamResponse {
        switch role {
        case .surgeon: surgeon
        case .nonInterventionalCardiologist: nonInterventionalCardiologist
        case .interventionalCardiologist: interventionalCardiologist
        }
    }
}
