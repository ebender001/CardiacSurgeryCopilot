//
//  HeartTeamRoleEvidence.swift
//  CardiacSurgeryCopilot
//
//  One heart-team role's pro/con PubMed evidence, as returned by
//  cscGetHeartTeamRoleEvidence -- "pro" supports that role's stated
//  recommendation, "con" favors an alternative or argues against it.
//  Both are restricted server-side to publications from the last 10
//  years (see backend/README.md) and may legitimately be empty -- no
//  citation is ever fabricated.
//

import Foundation

struct PubMedEvidenceSet: Decodable, Hashable {
    let query: String
    let results: [PubMedArticle]
}

struct HeartTeamRoleEvidence: Decodable, Hashable {
    let caseId: String
    let role: HeartTeamRole
    let pro: PubMedEvidenceSet
    let con: PubMedEvidenceSet
}
