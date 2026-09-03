//
//  CaseActionCard.swift
//  CardiacSurgeryCopilot
//
//  Each Home tab's primary call to action ("Start a New Case" /
//  "Start a New Discussion"). Deliberately not built on `.copilotProminent`
//  (a full-width filled button meant for form submission) -- this needs a
//  two-line title/subtitle plus a leading icon, so it gets its own
//  compact card-style button instead. Generic over its copy/icon so both
//  Home tabs share one implementation.
//

import SwiftUI

struct CaseActionCard: View {
    let icon: String
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(Color.white.opacity(0.16)))

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.body.weight(.semibold))
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(.white.opacity(0.8))
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.copilotPrimary)
            )
        }
        .buttonStyle(CaseActionCardButtonStyle())
        .accessibilityLabel(title)
        .accessibilityHint(subtitle)
    }
}

/// A clear, modest pressed state (slight dim + scale) without a heavy
/// shadow -- this card sits directly on the warm background, so a big
/// drop shadow would read as "generic dashboard tile" rather than calm
/// and editorial.
private struct CaseActionCardButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

#Preview {
    CaseActionCard(icon: "mic.fill", title: "Start a New Case", subtitle: "Dictate or type a clinical case summary", action: {})
        .padding()
        .background(Color.warmBackground)
}
