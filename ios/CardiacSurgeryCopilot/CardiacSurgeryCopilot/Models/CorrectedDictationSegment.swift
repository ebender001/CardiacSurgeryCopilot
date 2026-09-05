//
//  CorrectedDictationSegment.swift
//  CardiacSurgeryCopilot
//
//  Result of BackendService.correctDictation, mirroring
//  cscCorrectDictation's response shape.
//

import Foundation

struct CorrectedDictationSegment: Decodable {
    let correctedSegment: String
    let changes: [Change]

    struct Change: Decodable {
        let original: String
        let corrected: String
    }
}
