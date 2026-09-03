//
//  ConferenceQuestion.swift
//  CardiacSurgeryCopilot
//
//  The single active follow-up question on a Heart Team case, as returned
//  by cscCreateConferenceCase / cscAnswerConferenceQuestion's `nextQuestion`
//  field. `nil` on the case means no question is currently pending (either
//  more were never needed, or the case has moved to ready_to_finalize).
//

import Foundation

struct ConferenceQuestion: Decodable, Hashable {
    let id: String
    let text: String
    let category: String
    let reason: String
}
