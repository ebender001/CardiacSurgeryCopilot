//
//  WwydEmptyState.swift
//  CardiacSurgeryCopilot
//
//  Shown in place of the Recent Discussions list before the trainee has
//  started their first WWYD discussion.
//

import SwiftUI

struct WwydEmptyState: View {
    private let steps: [(icon: String, title: String, description: String)] = [
        ("mic.fill", "Describe a case", "Dictate or type a real case you saw."),
        ("text.bubble.fill", "Get a starting question", "A short presentation plus one opening question."),
        ("bubble.left.and.bubble.right.fill", "Talk it through", "Discuss it live with an AI attending that pushes on your reasoning.")
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text("No discussions yet")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)

                Text("Bring a case you saw. Cardiac Surgery Copilot will condense it into a short presentation and starting question, then discuss it with you.")
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
    WwydEmptyState()
        .padding()
        .background(Color.warmBackground)
}
