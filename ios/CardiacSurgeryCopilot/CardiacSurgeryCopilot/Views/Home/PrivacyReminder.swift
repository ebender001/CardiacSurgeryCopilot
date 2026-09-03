//
//  PrivacyReminder.swift
//  CardiacSurgeryCopilot
//
//  Subdued helper text below the "Start New" action, reminding the
//  trainee to keep the dictated/typed summary de-identified. Intentionally
//  quiet styling -- it must stay visible without competing with the
//  primary action above it.
//

import SwiftUI

struct PrivacyReminder: View {
    var body: some View {
        Label {
            Text("Do not include patient identifiers, dates, or locations.")
        } icon: {
            Image(systemName: "lock.fill")
        }
        .font(.caption)
        .foregroundStyle(Color.slateText)
        .labelStyle(.titleAndIcon)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    PrivacyReminder()
        .padding()
        .background(Color.warmBackground)
}
