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

import SwiftUI

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

/// ACC/AHA-style Class of Recommendation -- benefit vs. risk of what's
/// being recommended. See LevelOfEvidence's doc comment for why this is a
/// plain enum rather than custom Decodable: the backend already
/// normalizes anything it doesn't recognize to `null` (see
/// heartTeamResponseSchema.js's `validateGrade`), so `rawValue` failing to
/// match here would mean a real bug, not a case to silently swallow.
enum ClassOfRecommendation: String, Decodable, Hashable {
    case i = "I"
    case iia = "IIa"
    case iib = "IIb"
    case iiiNoBenefit = "III-NoBenefit"
    case iiiHarm = "III-Harm"

    /// Short badge text -- "COR" is implied by context (always shown
    /// alongside a LevelOfEvidence badge), so this omits it.
    var badgeText: String {
        switch self {
        case .i: "Class I"
        case .iia: "Class IIa"
        case .iib: "Class IIb"
        case .iiiNoBenefit: "Class III: No Benefit"
        case .iiiHarm: "Class III: Harm"
        }
    }

    /// Matches the standard ACC/AHA guideline color convention (green ->
    /// yellow -> orange -> red as benefit-to-risk weakens), so a trainee
    /// already familiar with printed guidelines recognizes it instantly.
    var color: Color {
        switch self {
        case .i: Color(red: 0.29, green: 0.55, blue: 0.35)
        case .iia: Color(red: 0.82, green: 0.65, blue: 0.13)
        case .iib: Color(red: 0.80, green: 0.47, blue: 0.13)
        case .iiiNoBenefit, .iiiHarm: Color(red: 0.70, green: 0.20, blue: 0.18)
        }
    }
}

/// ACC/AHA-style Level of Evidence -- quality of evidence behind a
/// recommendation, graded independently of ClassOfRecommendation (a LOE-C
/// recommendation is not thereby weak -- see heartTeamResponsesPrompt.js).
///
/// Deliberately a plain `String`-backed enum decoded via Swift's
/// synthesized `Decodable`, not a custom initializer that falls back to
/// `nil` on an unrecognized raw value: the backend already validates
/// against this exact set before it ever reaches the client (invalid ->
/// `null`, which decodes to Swift `nil` for these `Optional` properties on
/// `HeartTeamResponse`), so a raw value that fails to match here would
/// mean the two disagree about the valid set -- a bug worth a decode
/// failure surfacing, not one to mask.
enum LevelOfEvidence: String, Decodable, Hashable {
    case a = "A"
    case bRandomized = "B-R"
    case bNonrandomized = "B-NR"
    case cLimitedData = "C-LD"
    case cExpertOpinion = "C-EO"

    var badgeText: String { "LOE \(rawValue)" }

    /// Matches the standard ACC/AHA guideline color convention -- darkest
    /// blue for the strongest evidence (A), progressively lighter as
    /// evidence quality decreases, with B-R/B-NR sharing a shade and
    /// C-LD/C-EO sharing a lighter one, same grouping as the printed chart.
    var color: Color {
        switch self {
        case .a: Color(red: 0.15, green: 0.24, blue: 0.48)
        case .bRandomized, .bNonrandomized: Color(red: 0.27, green: 0.40, blue: 0.70)
        case .cLimitedData, .cExpertOpinion: Color(red: 0.56, green: 0.66, blue: 0.86)
        }
    }
}

struct HeartTeamResponse: Decodable, Hashable {
    let recommendation: String
    let rationale: String
    /// `nil` when the AI omitted grading or produced a value the backend
    /// couldn't validate (see heartTeamResponseSchema.js) -- never a
    /// placeholder or fabricated grade; the badge is simply not shown.
    let classOfRecommendation: ClassOfRecommendation?
    let levelOfEvidence: LevelOfEvidence?
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
