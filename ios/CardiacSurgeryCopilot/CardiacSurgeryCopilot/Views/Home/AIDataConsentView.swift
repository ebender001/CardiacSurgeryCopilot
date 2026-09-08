//
//  AIDataConsentView.swift
//  CardiacSurgeryCopilot
//
//  One-time gate presented before the trainee's first case: discloses
//  exactly what case data is sent to OpenAI, a third-party AI service, and
//  requires an explicit "I Agree" before proceeding. Presented as a sheet
//  from ConferenceHomeView, gated by ConferenceHomeViewModel.startNewCase()
//  -- every network call that carries case text (dictation correction,
//  case creation, interview answers, finalization, reference lookups) is
//  only reachable after passing through this gate. Ported from MMCoach's
//  AIDataConsentView, with copy grounded in this app's actual data flow
//  (see privacy-policy.html's "AI processing and third parties" section
//  and PHIFilterService).
//

import SwiftUI

struct AIDataConsentView: View {
    let onAgree: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    headerIcon

                    Text("How Your Case Data Is Used")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.primary)

                    Text("To help you prepare each case, Heart Team Prep sends parts of it to OpenAI, a third-party AI service, to extract case details, generate follow-up questions, build your structured report, and generate the three heart-team perspectives.")
                        .font(.body)
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)

                    VStack(alignment: .leading, spacing: 12) {
                        Text("What's sent to OpenAI")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)

                        dataRow("Your dictated or typed case description")
                        dataRow("Your answers to follow-up questions")
                        dataRow("Case details used to generate your report and heart-team perspectives")
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .polishedCard()

                    Text("When you look up literature for a case topic, a short search query derived from that topic -- not your full case narrative -- is also sent to the National Library of Medicine's PubMed service.")
                        .font(.footnote)
                        .foregroundStyle(Color.slateText)
                        .fixedSize(horizontal: false, vertical: true)

                    Text("Before anything reaches our servers, Heart Team Prep automatically screens your text on-device and removes what looks like patient or staff names, hospital or institution names, specific dates, and geographic locations. This is a safety net, not full de-identification -- never dictate or type real patient or staff identifiers.")
                        .font(.footnote)
                        .foregroundStyle(Color.slateText)
                        .fixedSize(horizontal: false, vertical: true)

                    Link("Read our full Privacy Policy", destination: LegalLinks.privacyPolicy)
                        .font(.footnote.weight(.semibold))
                }
                .padding(.horizontal, 24)
                .padding(.top, 24)
                .padding(.bottom, 12)
            }

            VStack(spacing: 12) {
                Button("I Agree & Continue", action: onAgree)
                    .buttonStyle(.copilotProminent)
                Button("Not Now", action: onCancel)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.slateText)
            }
            .padding(.horizontal, 24)
            .padding(.top, 8)
            .padding(.bottom, 20)
        }
        .background(Color.warmBackground.ignoresSafeArea())
    }

    private var headerIcon: some View {
        ZStack {
            Circle()
                .fill(Color.copilotPrimaryTint)
                .frame(width: 56, height: 56)
            Image(systemName: "shield.lefthalf.filled")
                .font(.system(size: 22, weight: .medium))
                .foregroundStyle(Color.copilotPrimaryText)
        }
        .accessibilityHidden(true)
    }

    private func dataRow(_ text: String) -> some View {
        Label {
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.primary)
        } icon: {
            Image(systemName: "arrow.up.forward")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.copilotPrimaryText)
        }
        .labelStyle(.titleAndIcon)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    Color.warmBackground
        .sheet(isPresented: .constant(true)) {
            AIDataConsentView(onAgree: {}, onCancel: {})
        }
}
