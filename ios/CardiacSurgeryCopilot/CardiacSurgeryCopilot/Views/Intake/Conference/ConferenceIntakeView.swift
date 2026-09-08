//
//  ConferenceIntakeView.swift
//  CardiacSurgeryCopilot
//
//  The trainee describes the case naturally -- by dictating or typing --
//  rather than filling out structured clinical fields. Reached from
//  ConferenceHomeView's "Start a New Case".
//

import SwiftUI

struct ConferenceIntakeView: View {
    @StateObject private var viewModel: ConferenceIntakeViewModel
    @Binding var path: [ConferenceRoute]

    init(path: Binding<[ConferenceRoute]>, viewModel: ConferenceIntakeViewModel? = nil) {
        _path = path
        _viewModel = StateObject(wrappedValue: viewModel ?? ConferenceIntakeViewModel())
    }

    private static let analyzingMessages = [
        "Reading the case details…",
        "Identifying what's already known…",
        "Checking what the heart team will want to know…"
    ]

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 20) {
                    header
                    editorCard
                        .disabled(viewModel.isSubmitting)

                    if !viewModel.spellingSuggestions.isEmpty {
                        Text("Double-check spelling: \(viewModel.spellingSuggestions.joined(separator: ", "))")
                            .font(.footnote)
                            .foregroundStyle(Color.slateText)
                    }

                    if let errorMessage = viewModel.errorMessage {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 16)
                .frame(maxHeight: .infinity, alignment: .top)

                Divider()
                    .opacity(0.5)

                continueFooter
            }
            .background(Color.warmBackground)

            if viewModel.isSubmitting {
                AnalyzingOverlay(title: "Analyzing your case", messages: Self.analyzingMessages)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.isSubmitting)
        .navigationTitle("New Case")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Dictation Unavailable",
               isPresented: dictationErrorBinding,
               presenting: viewModel.dictationErrorMessage) { _ in
            Button("OK") { viewModel.dictationErrorMessage = nil }
        } message: { message in
            Text(message)
        }
        .alert("Possible Patient Information Removed",
               isPresented: phiNoticeBinding,
               presenting: viewModel.phiNoticeMessage) { _ in
            Button("OK") { viewModel.phiNoticeMessage = nil }
        } message: { message in
            Text(message)
        }
    }

    private var dictationErrorBinding: Binding<Bool> {
        Binding(
            get: { viewModel.dictationErrorMessage != nil },
            set: { isPresented in
                if !isPresented { viewModel.dictationErrorMessage = nil }
            }
        )
    }

    private var phiNoticeBinding: Binding<Bool> {
        Binding(
            get: { viewModel.phiNoticeMessage != nil },
            set: { isPresented in
                if !isPresented { viewModel.phiNoticeMessage = nil }
            }
        )
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Describe the case")
                .font(.title3.weight(.semibold))
                .foregroundStyle(.primary)
            Text("Present it as you would to the heart team. Do not include protected health information, including patient names, hospital or institution names, dates of service, or geographic locations.")
                .font(.subheadline)
                .foregroundStyle(Color.slateText)
        }
    }

    /// Boxes the dictate-or-type hint together with the editor and its mic
    /// control so the whole "how to give me the case" unit reads as one
    /// intentional surface.
    private var editorCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            inputModeHint

            DictationEditorView(
                text: $viewModel.narrativeText,
                phase: viewModel.dictationPhase,
                placeholder: "A 64-year-old man presents with an NSTEMI…",
                minHeight: 160,
                onToggleDictation: { Task { await viewModel.toggleDictation() } }
            )
        }
        .padding(16)
        .frame(maxHeight: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(.secondarySystemGroupedBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(Color.primary.opacity(0.06), lineWidth: 1)
        )
    }

    /// A single flowing sentence so it wraps naturally as one paragraph on narrow screens.
    private var inputModeHint: some View {
        Text("\(Image(systemName: "mic.fill")) Dictate or \(Image(systemName: "keyboard")) type -- whichever is easier.")
            .font(.footnote.weight(.medium))
            .foregroundStyle(Color.slateText)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("You can dictate or type the case summary, whichever is easier.")
    }

    private var continueFooter: some View {
        continueButton
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 16)
            .background(Color.warmBackground)
    }

    private var continueButton: some View {
        Button {
            Task {
                guard let created = await viewModel.submit() else { return }
                if created.nextQuestion == nil {
                    // Nothing more needed -- skip straight to the heart
                    // team responses.
                    path.append(.heartTeamResponses(caseId: created.id))
                } else {
                    path.append(.interview(caseId: created.id, initialCase: created))
                }
            }
        } label: {
            if viewModel.isSubmitting {
                ProgressView()
                    .tint(.white)
            } else {
                Text("Continue")
            }
        }
        .buttonStyle(.copilotProminent)
        .disabled(!viewModel.canContinue)
    }
}

#Preview {
    NavigationStack {
        ConferenceIntakeView(path: .constant([]))
    }
}
