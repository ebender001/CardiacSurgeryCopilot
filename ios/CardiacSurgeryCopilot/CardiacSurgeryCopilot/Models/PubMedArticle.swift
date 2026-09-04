//
//  PubMedArticle.swift
//  CardiacSurgeryCopilot
//
//  One PubMed article as returned by pubmedService.js's `toClientResult`
//  (via cscGetHeartTeamRoleEvidence) -- no citation is ever fabricated on
//  the backend, so an empty results array is a real, expected outcome,
//  not an error.
//

import Foundation

struct PubMedAbstractSection: Decodable, Hashable {
    let label: String?
    let text: String
}

struct PubMedArticle: Decodable, Identifiable, Hashable {
    let pmid: String
    let title: String
    let authors: [String]
    let journal: String?
    let year: String?
    let abstractSections: [PubMedAbstractSection]
    let url: String

    var id: String { pmid }
}
