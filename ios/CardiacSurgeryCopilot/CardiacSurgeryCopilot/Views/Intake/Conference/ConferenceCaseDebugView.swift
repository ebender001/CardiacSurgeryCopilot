//
//  ConferenceCaseDebugView.swift
//  CardiacSurgeryCopilot
//
//  Temporary stand-in for the real interview/report views (see
//  MMCoach's CaseInterviewView/CaseSummaryView) -- shows what the backend
//  actually returned after create/answer, so the AI extraction and
//  follow-up-question loop can be verified end-to-end before the real
//  question-answering UI exists. Read-only: there's no way to answer
//  `nextQuestion` from here yet. Delete once the real interview view is
//  built (see ConferenceHomeView's `destination(for:)`).
//

import SwiftUI

struct ConferenceCaseDebugView: View {
    let caseId: String
    let initialCase: ConferenceCase

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                caseIdRow
                statusRow

                if let question = initialCase.nextQuestion {
                    nextQuestionCard(question)
                } else {
                    noQuestionCard
                }

                Text("This is a debug view of the raw backend response -- the real interview/report screens aren't built yet.")
                    .font(.caption)
                    .foregroundStyle(Color.slateText)
            }
            .padding(20)
        }
        .background(Color.warmBackground)
        .navigationTitle("Case Created")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var caseIdRow: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Case ID")
                .font(.caption)
                .foregroundStyle(Color.slateText)
            Text(caseId)
                .font(.footnote.monospaced())
                .textSelection(.enabled)
        }
    }

    private var statusRow: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Status")
                .font(.caption)
                .foregroundStyle(Color.slateText)
            StatusBadge(text: statusText, tint: Color.copilotPrimaryText.opacity(0.16), textColor: Color.copilotPrimaryText)
        }
    }

    private var statusText: String {
        switch initialCase.status {
        case .collectingInformation: "Collecting Information"
        case .readyToFinalize: "Ready to Finalize"
        case .completed: "Completed"
        }
    }

    private func nextQuestionCard(_ question: ConferenceQuestion) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Next Question")
                .font(.subheadline.weight(.semibold))
            Text(question.text)
                .font(.body)
            HStack(spacing: 8) {
                StatusBadge(text: question.category, tint: Color(.systemGray5), textColor: Color.slateText)
            }
            Text(question.reason)
                .font(.footnote)
                .foregroundStyle(Color.slateText)
        }
        .polishedCard()
    }

    private var noQuestionCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("No follow-up question")
                .font(.subheadline.weight(.semibold))
            Text("The backend has enough information to finalize this case.")
                .font(.footnote)
                .foregroundStyle(Color.slateText)
        }
        .polishedCard()
    }
}

#Preview("With question") {
    NavigationStack {
        ConferenceCaseDebugView(
            caseId: "abc123",
            initialCase: ConferenceCase(
                id: "abc123",
                status: .collectingInformation,
                nextQuestion: ConferenceQuestion(
                    id: "q1",
                    text: "Has the patient's religious objection to blood products been discussed with the team, and is a bloodless-surgery protocol in place?",
                    category: "patient goals",
                    reason: "As a Jehovah's Witness with three-vessel disease and reduced LVEF, transfusion avoidance materially shapes the operative plan."
                )
            )
        )
    }
}

#Preview("Ready to finalize") {
    NavigationStack {
        ConferenceCaseDebugView(
            caseId: "abc123",
            initialCase: ConferenceCase(id: "abc123", status: .readyToFinalize, nextQuestion: nil)
        )
    }
}
