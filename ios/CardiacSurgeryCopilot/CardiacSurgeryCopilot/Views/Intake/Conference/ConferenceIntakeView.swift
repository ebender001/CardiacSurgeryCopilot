//
//  ConferenceIntakeView.swift
//  CardiacSurgeryCopilot
//
//  The trainee describes the case naturally -- by typing (dictation isn't
//  ported into this app yet, see ConferenceIntakeViewModel) -- rather than
//  filling out structured clinical fields. Reached from ConferenceHomeView's
//  "Start a New Case".
//

import SwiftUI

struct ConferenceIntakeView: View {
    @StateObject private var viewModel: ConferenceIntakeViewModel
    @Binding var path: [ConferenceRoute]
    @FocusState private var isEditorFocused: Bool

    init(path: Binding<[ConferenceRoute]>, viewModel: ConferenceIntakeViewModel? = nil) {
        _path = path
        _viewModel = StateObject(wrappedValue: viewModel ?? ConferenceIntakeViewModel())
    }

    /// DEBUG-only seed text used solely by ConferenceHomeView's ladybug
    /// shortcut, which creates a real backend case directly (bypassing
    /// this screen's text field) so the create-case round trip can be
    /// exercised without retyping a full narrative every time. This
    /// screen itself no longer pre-fills its editor with it -- a new case
    /// always starts blank. Empty in Release builds -- never ships
    /// clinical-sounding text anywhere.
    static var debugSeedNarrative: String {
        #if DEBUG
        return "A 64-year-old male presents with an NSTEMI. Echocardiogram shows LVEF 35%, moderate mitral regurgitation, and mild aortic stenosis. He is an insulin-dependent diabetic, admitted yesterday. Cardiac catheterization shows three-vessel coronary artery disease: 90% proximal LAD stenosis, 70% stenosis of a large OM1 branch, and 100% occlusion of the RCA with good left-to-right collaterals to a moderate-sized PDA. He is currently asymptomatic on IV heparin and nitroglycerin. He is a Jehovah's Witness."
        #else
        return ""
        #endif
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

    private var editorCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("\(Image(systemName: "keyboard")) Type the case summary.")
                .font(.footnote.weight(.medium))
                .foregroundStyle(Color.slateText)

            TextEditor(text: $viewModel.narrativeText)
                .focused($isEditorFocused)
                .scrollContentBackground(.hidden)
                .frame(minHeight: 200)
                .font(.body)
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
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { isEditorFocused = false }
            }
        }
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
            isEditorFocused = false
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
