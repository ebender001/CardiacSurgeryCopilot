//
//  ConferenceEmptyState.swift
//  CardiacSurgeryCopilot
//
//  Shown in place of the Recent Cases list before the trainee has started
//  their first conference case. Doubles as a brief first-use explanation
//  of the workflow rather than a generic "nothing here" placeholder.
//

import SwiftUI

struct ConferenceEmptyState: View {
    private let steps: [(icon: String, title: String, description: String)] = [
        ("mic.fill", "Describe the case", "Dictate or type the clinical summary."),
        ("text.bubble.fill", "Answer focused questions", "Add the details the heart team will ask about."),
        ("doc.text.fill", "Review your report", "Diagnosis through postoperative concerns, plus evidence and guidelines.")
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text("No cases yet")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)

                Text("Start with a brief case summary. Heart Team Copilot will help you clarify the details and prepare a structured report for the heart team conference.")
                    .font(.footnote)
                    .foregroundStyle(Color.slateText)
                    .fixedSize(horizontal: false, vertical: true)
            }

            VStack(spacing: 12) {
                ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                    HowItWorksStep(icon: step.icon, title: step.title, description: step.description)

                    if index < steps.count - 1 {
                        Divider()
                            .overlay(Color.primary.opacity(0.06))
                    }
                }
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(.secondarySystemGroupedBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(Color.primary.opacity(0.06), lineWidth: 1)
        )
    }
}

#Preview {
    ConferenceEmptyState()
        .padding()
        .background(Color.warmBackground)
}
