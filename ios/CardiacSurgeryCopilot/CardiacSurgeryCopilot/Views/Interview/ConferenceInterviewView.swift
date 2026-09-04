//
//  ConferenceInterviewView.swift
//  CardiacSurgeryCopilot
//
//  Presents the follow-up-question loop: one AI question, one typed
//  answer, one submit action -- repeated until the backend reports the
//  case is ready to finalize. This view never decides that on its own; it
//  only reacts to `nextQuestion` becoming nil by redirecting straight to
//  HeartTeamResponsesView (see navigateIfReady()), same as
//  ConferenceIntakeView does when a case needs no follow-up at all -- a
//  case with no pending question is never shown a static "ready" screen
//  here.
//

import SwiftUI

struct ConferenceInterviewView: View {
    @StateObject private var viewModel: ConferenceInterviewViewModel
    @Binding var path: [ConferenceRoute]
    @FocusState private var isEditorFocused: Bool

    init(caseId: String, initialCase: ConferenceCase?, path: Binding<[ConferenceRoute]>, viewModel: ConferenceInterviewViewModel? = nil) {
        _path = path
        _viewModel = StateObject(wrappedValue: viewModel ?? ConferenceInterviewViewModel(caseId: caseId, initialCase: initialCase))
    }

    var body: some View {
        Group {
            if viewModel.isLoadingCase {
                loadingView
            } else if let question = viewModel.currentQuestion {
                interviewContent(question: question)
            } else {
                // Between load/submit completing and navigateIfReady()'s
                // push landing -- effectively never visible.
                Color.clear
            }
        }
        .background(Color.warmBackground)
        .navigationTitle("Follow-Up Question")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await viewModel.loadIfNeeded()
            navigateIfReady()
        }
    }

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text("Loading your case…")
                .font(.subheadline)
                .foregroundStyle(Color.slateText)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// The editor needs a genuine upper bound (a fixed bottom submit
    /// region + a flexible, top-anchored content region) so a long typed
    /// answer scrolls internally instead of extending off the bottom of
    /// the screen -- same layout reasoning as ConferenceIntakeView.
    private func interviewContent(question: ConferenceQuestion) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    questionCard(question)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("\(Image(systemName: "keyboard")) Type your answer.")
                            .font(.footnote.weight(.medium))
                            .foregroundStyle(Color.slateText)

                        TextEditor(text: $viewModel.answerText)
                            .focused($isEditorFocused)
                            .scrollContentBackground(.hidden)
                            .frame(minHeight: 120)
                            .font(.body)
                            .padding(10)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(Color(.secondarySystemGroupedBackground))
                            )
                    }
                }
                .padding(20)
            }

            Divider()
                .opacity(0.5)

            VStack(alignment: .leading, spacing: 12) {
                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                Button {
                    isEditorFocused = false
                    Task {
                        await viewModel.submitAnswer()
                        navigateIfReady()
                    }
                } label: {
                    if viewModel.isSubmittingAnswer {
                        ProgressView().tint(.white)
                    } else {
                        Text("Submit Answer")
                    }
                }
                .buttonStyle(.copilotProminent)
                .disabled(!viewModel.canSubmitAnswer)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 16)
            .background(Color.warmBackground)
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { isEditorFocused = false }
            }
        }
    }

    private func questionCard(_ question: ConferenceQuestion) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(question.text)
                .font(.body.weight(.medium))
                .foregroundStyle(.primary)

            StatusBadge(text: question.category, tint: Color(.systemGray5), textColor: Color.slateText)

            Text(question.reason)
                .font(.footnote)
                .foregroundStyle(Color.slateText)
        }
        .polishedCard()
    }

    /// Pushes straight to the heart team responses once there's no pending
    /// question and the case isn't still loading -- covers both a
    /// just-answered last question and a resumed case that was already
    /// `ready_to_finalize`. A no-op while a question is still pending or
    /// still loading.
    private func navigateIfReady() {
        guard !viewModel.isLoadingCase, viewModel.currentQuestion == nil, viewModel.errorMessage == nil else { return }
        path.append(.heartTeamResponses(caseId: viewModel.caseId))
    }
}

#Preview("Question") {
    NavigationStack {
        ConferenceInterviewView(
            caseId: "abc123",
            initialCase: ConferenceCase(
                id: "abc123",
                status: .collectingInformation,
                nextQuestion: ConferenceQuestion(
                    id: "q1",
                    text: "Has the patient's religious objection to blood products been discussed with the team, and is a bloodless-surgery protocol in place?",
                    category: "patient goals",
                    reason: "As a Jehovah's Witness with three-vessel disease and reduced LVEF, transfusion avoidance materially shapes the operative plan."
                )
            ),
            path: .constant([])
        )
    }
}
