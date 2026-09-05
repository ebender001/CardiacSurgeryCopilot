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
    @State private var isConfirmingSkip = false

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
    /// region + a flexible, top-anchored content region) so a long
    /// dictated/typed answer scrolls internally instead of extending off
    /// the bottom of the screen -- same layout reasoning as
    /// ConferenceIntakeView.
    private func interviewContent(question: ConferenceQuestion) -> some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 20) {
                questionCard(question)

                DictationEditorView(
                    text: $viewModel.answerText,
                    phase: viewModel.dictationPhase,
                    placeholder: "Answer as you would explain it out loud…",
                    minHeight: 120,
                    onToggleDictation: { Task { await viewModel.toggleDictation() } }
                )
                .frame(maxHeight: .infinity)
            }
            .padding(20)
            .frame(maxHeight: .infinity, alignment: .top)

            VStack(alignment: .leading, spacing: 12) {
                if !viewModel.spellingSuggestions.isEmpty {
                    Text("Double-check spelling: \(viewModel.spellingSuggestions.joined(separator: ", "))")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                Button {
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

                skipButton
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 16)
            .background(Color.warmBackground)
        }
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
        .confirmationDialog(
            "Stop Answering Questions?",
            isPresented: $isConfirmingSkip,
            titleVisibility: .visible
        ) {
            Button("Stop and Continue", role: .destructive) {
                Task {
                    await viewModel.skipRemainingQuestions()
                    navigateIfReady()
                }
            }
            Button("Keep Answering", role: .cancel) {}
        } message: {
            Text("The heart team's analysis may be less reliable without the information this and any other unanswered questions were asking for. You can still finalize the case, but some sections may rely on assumptions rather than confirmed details.")
        }
    }

    /// A deliberately lower-emphasis affordance than "Submit Answer" --
    /// always reachable at every question, but never competing with it,
    /// since answering is still the expected path and this is an
    /// escape hatch, not an equally-weighted alternative.
    private var skipButton: some View {
        Button {
            isConfirmingSkip = true
        } label: {
            if viewModel.isSkippingRemainingQuestions {
                ProgressView()
            } else {
                Text("Stop Asking Questions")
                    .font(.footnote.weight(.medium))
            }
        }
        .buttonStyle(.plain)
        .foregroundStyle(Color.slateText)
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
        .disabled(viewModel.isSubmittingAnswer || viewModel.isSkippingRemainingQuestions)
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

    /// Moves on to the heart team responses once there's no pending
    /// question and the case isn't still loading -- covers both a
    /// just-answered last question and a resumed case that was already
    /// `ready_to_finalize`. A no-op while a question is still pending or
    /// still loading.
    ///
    /// Replaces this view's own entry in `path` rather than pushing on top
    /// of it -- this same view instance handles every question in the
    /// loop in place (see the type doc above), so once it's done it has
    /// nothing left to show. Pushing on top of it would leave it sitting
    /// in the stack as a dead end: popping back from Heart Team Responses
    /// would land on this now-empty "Follow-Up Question" screen instead of
    /// wherever the interview was actually reached from.
    private func navigateIfReady() {
        guard !viewModel.isLoadingCase, viewModel.currentQuestion == nil, viewModel.errorMessage == nil else { return }
        let next = ConferenceRoute.heartTeamResponses(caseId: viewModel.caseId)
        if path.isEmpty {
            path.append(next)
        } else {
            path[path.count - 1] = next
        }
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
