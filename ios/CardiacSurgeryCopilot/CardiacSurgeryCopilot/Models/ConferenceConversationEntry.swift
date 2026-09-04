//
//  ConferenceConversationEntry.swift
//  CardiacSurgeryCopilot
//
//  One already-answered follow-up question on a case, as returned inline
//  on cscGetConferenceCase's `conversation` array. Distinct from
//  ConferenceQuestion (the single currently-pending question, if any) --
//  every entry here is always answered; an unanswered question only ever
//  exists as `nextQuestion`, never in this array. See
//  ConferenceFollowUpQuestionsView.
//

import Foundation

struct ConferenceConversationEntry: Decodable, Identifiable, Hashable {
    let questionId: String
    let question: String
    let category: String
    let reason: String
    let answer: String

    var id: String { questionId }
}
