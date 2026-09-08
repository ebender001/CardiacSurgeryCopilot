//
//  PaywallBenefitRow.swift
//  CardiacSurgeryCopilot
//
//  One compact benefit line on PaywallView -- a restrained SF Symbol plus
//  a title/detail pair, matching the app's existing icon-in-a-column card
//  language (see CaseActionCard) rather than a decorative illustration.
//

import SwiftUI

struct PaywallBenefitRow: View {
    let icon: String
    let title: String
    let detail: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.headline)
                .foregroundStyle(Color.copilotPrimaryText)
                .frame(width: 26, height: 26)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(Color.slateText)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 14) {
        PaywallBenefitRow(icon: "list.bullet.clipboard",
                           title: "Structured conference reports",
                           detail: "Diagnosis through postoperative concerns, organized the way you'd present it.")
        PaywallBenefitRow(icon: "person.3.fill",
                           title: "Three heart-team perspectives",
                           detail: "See how a surgeon, a non-interventional cardiologist, and an interventional cardiologist would each approach your case.")
        PaywallBenefitRow(icon: "magnifyingglass",
                           title: "Evidence and guidelines",
                           detail: "Automatically search PubMed for abstracts relevant to your case.")
    }
    .padding()
    .background(Color.warmBackground)
}
