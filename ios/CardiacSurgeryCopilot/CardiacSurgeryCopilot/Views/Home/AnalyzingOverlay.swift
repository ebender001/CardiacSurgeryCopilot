//
//  AnalyzingOverlay.swift
//  CardiacSurgeryCopilot
//
//  A full-screen "working on it" overlay for AI calls that take a few
//  seconds (case creation, finalizing a report, etc.) -- a bare spinner
//  gives no sense that something substantive (an AI extraction/analysis
//  step, not just a network round trip) is happening. Cycles through a
//  caller-supplied list of phrases so it reads as active progress rather
//  than a stuck screen; the phrases are a friendlier approximation of
//  what the backend is doing, not literal real-time step tracking (a
//  single AI call has no finer-grained progress to report).
//

import SwiftUI

struct AnalyzingOverlay: View {
    let title: String
    let messages: [String]

    @State private var messageIndex = 0
    @State private var isPulsing = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let cycleInterval: TimeInterval = 1.8

    var body: some View {
        ZStack {
            Color.warmBackground.opacity(0.94).ignoresSafeArea()

            VStack(spacing: 18) {
                Image(systemName: "waveform.path.ecg")
                    .font(.system(size: 40, weight: .medium))
                    .foregroundStyle(Color.copilotPrimaryText)
                    .scaleEffect(isPulsing ? 1.12 : 0.9)
                    .opacity(isPulsing ? 1 : 0.5)
                    .animation(
                        reduceMotion ? nil : .easeInOut(duration: 0.9).repeatForever(autoreverses: true),
                        value: isPulsing
                    )

                VStack(spacing: 6) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(.primary)

                    if !messages.isEmpty {
                        Text(messages[messageIndex % messages.count])
                            .font(.subheadline)
                            .foregroundStyle(Color.slateText)
                            .multilineTextAlignment(.center)
                            .id(messageIndex)
                            .transition(.opacity)
                            .padding(.horizontal, 32)
                    }
                }
            }
            .padding(28)
        }
        .onAppear { isPulsing = true }
        .task {
            guard messages.count > 1 else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(cycleInterval))
                if Task.isCancelled { return }
                withAnimation(.easeInOut(duration: 0.35)) {
                    messageIndex = (messageIndex + 1) % messages.count
                }
            }
        }
        // One announcement, not one per cycled phrase -- VoiceOver
        // re-reading every 1.8s would be more annoying than helpful.
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title). \(messages.first ?? "")")
    }
}

#Preview {
    AnalyzingOverlay(
        title: "Analyzing your case",
        messages: [
            "Reading the case details…",
            "Identifying what's already known…",
            "Checking what's still needed…"
        ]
    )
}
