//
//  ConferenceFollowUpQuestionsView.swift
//  CardiacSurgeryCopilot
//
//  Reached from HeartTeamResponsesView's follow-up-question summary --
//  a read-only review of every question actually asked during intake and
//  how it was answered. Purely informational; there's nothing to edit
//  here (a completed case's Q&A is part of its history, not a draft).
//

import SwiftUI

struct ConferenceFollowUpQuestionsView: View {
    let entries: [ConferenceConversationEntry]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                    entryCard(entry, number: index + 1)
                }
            }
            .padding(20)
        }
        .background(Color.warmBackground)
        .navigationTitle("Follow-Up Questions")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func entryCard(_ entry: ConferenceConversationEntry, number: Int) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text("Q\(number)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.slateText)
                StatusBadge(text: entry.category, tint: Color(.systemGray5), textColor: Color.slateText)
            }
            Text(entry.question)
                .font(.body.weight(.medium))
                .foregroundStyle(.primary)

            Divider()

            Text(entry.answer)
                .font(.body)
                .foregroundStyle(.primary)
        }
        .polishedCard()
    }
}

#Preview {
    NavigationStack {
        ConferenceFollowUpQuestionsView(entries: [
            ConferenceConversationEntry(
                questionId: "q1",
                question: "Has the patient's religious objection to blood products been discussed with the team, and is a bloodless-surgery protocol in place?",
                category: "patient goals",
                reason: "As a Jehovah's Witness, transfusion avoidance materially shapes the operative plan.",
                answer: "Yes, discussed with anesthesia and blood bank. Cell salvage will be used, and preoperative iron/EPO optimization is planned."
            ),
            ConferenceConversationEntry(
                questionId: "q2",
                question: "Have non-surgical or catheter-based options been considered for this patient, and what were the conclusions?",
                category: "non-surgical alternatives",
                reason: "The heart team will expect this to have been addressed before recommending surgery.",
                answer: "Interventional cardiology reviewed the anatomy; the RCA CTO was felt to be a poor long-term PCI target given the diffuse disease."
            )
        ])
    }
}
