//
//  ConferenceReport.swift
//  CardiacSurgeryCopilot
//
//  The nine-section preoperative case conference report -- eight prose
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
    let evidenceGuidelines: [ConferenceReferenceTopic]
}
