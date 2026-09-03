//
//  ComingSoonView.swift
//  CardiacSurgeryCopilot
//
//  Temporary destination for routes that don't have a real screen yet
//  (case intake, the interview/report/conversation views). Delete each
//  usage as the real destination is built -- see ConferenceHomeView/
//  WwydHomeView's `destination(for:)`.
//

import SwiftUI

struct ComingSoonView: View {
    let title: String
    var detail: String = "This part of the app isn't built yet."

    var body: some View {
        ZStack {
            Color.warmBackground.ignoresSafeArea()

            VStack(spacing: 12) {
                Image(systemName: "hammer.fill")
                    .font(.system(size: 36))
                    .foregroundStyle(Color.copilotPrimaryText)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(Color.slateText)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    NavigationStack {
        ComingSoonView(title: "New Case")
    }
}
