//
//  ConferenceReport.swift
//  CardiacSurgeryCopilot
//
//  The ten-section preoperative case conference report -- nine prose
//  sections plus a list of evidence/guideline topics -- as returned by
//  cscFinalizeConferenceCase / cscUpdateConferenceReport, or inline on
//  cscGetConferenceCase once a case is `completed`. See backend/README.md.
//

import Foundation

/// A literature/guideline topic identified by the backend as relevant to
/// this case's evidence/guidelines section. The backend only identifies
/// *what* should be looked up, not a verified citation -- `citation` is
/// always nil and `verified` always false today. Tapping one runs a live
/// PubMed search (see ConferenceReferenceLookupView), which never writes
/// back onto the case.
struct ConferenceReferenceTopic: Decodable, Hashable {
    let topic: String
    let searchIntent: String
    let citation: String?
    let verified: Bool
}

struct ConferenceReport: Decodable, Hashable {
    let diagnosis: String
    let indication: String
    let missingInformation: String
    let operativeStrategy: String
    let alternatives: String
    let controversies: String
    let technicalConsiderations: String
    let postoperativeConcerns: String
    /// Whether the totality of reviewed heart-team evidence leans toward
    /// one approach, is genuinely mixed, or is too thin to say -- see
    /// finalizeConferencePrompt.js. Optional (rather than required, like
    /// every other section) only so a report finalized before this field
    /// existed still decodes -- `nil` there, never an empty string; a
    /// report finalized after this field's introduction always has it.
    let preponderanceOfEvidence: String?
    /// Which heart-team roles had their evidence (see HeartTeamEvidenceView)
    /// reviewed as of when this report was finalized -- lets the client
    /// nudge toward reviewing the rest rather than trusting
    /// `preponderanceOfEvidence`'s free text to convey that reliably.
    /// `nil` (not just empty) for a report finalized before this field
    /// existed, so an old report is never nudged based on data it never
    /// tracked -- `[]` means "tracked, and reviewed for zero roles".
    let evidenceReviewedRoles: [HeartTeamRole]?
    let evidenceGuidelines: [ConferenceReferenceTopic]
}
