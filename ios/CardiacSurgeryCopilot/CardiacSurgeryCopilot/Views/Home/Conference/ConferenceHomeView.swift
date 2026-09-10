//
//  ConferenceHomeView.swift
//  CardiacSurgeryCopilot
//
//  Root of the signed-in app's single NavigationStack (see RootView).
//  Everything in this workflow (intake, interview, report) belongs here as
//  pushes onto this stack, mirroring MMCoach's single-workflow HomeView.
//

import SwiftUI

struct ConferenceHomeView: View {
    @StateObject private var viewModel: ConferenceHomeViewModel
    @State private var path: [ConferenceRoute] = []
    @State private var isPresentingAccount = false
    /// The signed-in user and an account-deletion action, supplied by
    /// RootView -- optional (rather than a required non-Optional
    /// `AuthenticatedUser`) so `ConferenceHomeView(onSignOut:)` keeps
    /// working unchanged in previews/tests that don't care about the
    /// account area. Mirrors MMCoach's HomeView.
    let currentUser: AuthenticatedUser?
    let onSignOut: () -> Void
    let onDeleteAccount: (() async throws -> Void)?

    init(currentUser: AuthenticatedUser? = nil,
         onSignOut: @escaping () -> Void,
         onDeleteAccount: (() async throws -> Void)? = nil,
         viewModel: ConferenceHomeViewModel? = nil) {
        self.currentUser = currentUser
        self.onSignOut = onSignOut
        self.onDeleteAccount = onDeleteAccount
        _viewModel = StateObject(wrappedValue: viewModel ?? ConferenceHomeViewModel())
    }

    var body: some View {
        NavigationStack(path: $path) {
            List {
                Section {
                    header
                }
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)

                Section {
                    CaseActionCard(
                        icon: "mic.fill",
                        title: "Start a New Case",
                        subtitle: "Dictate or type a case for the heart team conference"
                    ) {
                        Task {
                            if await viewModel.startNewCase() {
                                path.append(.newCase)
                            }
                        }
                    }
                }
                .listRowSeparator(.hidden)
                .listRowInsets(rowInsets(top: 4, bottom: 6))
                .listRowBackground(Color.clear)

                Section {
                    PrivacyReminder()
                }
                .listRowSeparator(.hidden)
                .listRowInsets(rowInsets(top: 0, bottom: 16))
                .listRowBackground(Color.clear)

                Section {
                    Text("Recent Cases")
                        .font(.headline)
                        .foregroundStyle(.primary)
                } header: { EmptyView() }
                    .listRowSeparator(.hidden)
                    .listRowInsets(rowInsets(top: 0, bottom: 4))
                    .listRowBackground(Color.clear)

                if viewModel.recentCases.isEmpty, let errorMessage = viewModel.recentCasesErrorMessage {
                    Section {
                        ListErrorState(title: "Couldn't load Recent Cases", message: errorMessage) {
                            Task { await viewModel.refresh() }
                        }
                    }
                    .listRowSeparator(.hidden)
                    .listRowInsets(rowInsets(top: 0, bottom: 4))
                    .listRowBackground(Color.clear)
                } else if viewModel.recentCases.isEmpty {
                    Section {
                        if viewModel.isLoadingRecentCases {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 24)
                        } else {
                            ConferenceEmptyState()
                        }
                    }
                    .listRowSeparator(.hidden)
                    .listRowInsets(rowInsets(top: 0, bottom: 4))
                    .listRowBackground(Color.clear)
                } else {
                    Section {
                        ForEach(viewModel.recentCases) { record in
                            Button {
                                switch record.status {
                                case .completed:
                                    path.append(.report(caseId: record.id))
                                case .readyToFinalize:
                                    // No pending question by definition --
                                    // go straight to heart team responses
                                    // rather than through ConferenceInterviewView,
                                    // which would otherwise show a blank
                                    // screen for a moment before redirecting.
                                    path.append(.heartTeamResponses(caseId: record.id))
                                case .collectingInformation:
                                    path.append(.interview(caseId: record.id, initialCase: nil))
                                }
                            } label: {
                                ConferenceCaseRow(record: record)
                            }
                            .buttonStyle(.plain)
                            .listRowSeparator(.hidden)
                            .listRowInsets(rowInsets(top: 4, bottom: 4))
                            .listRowBackground(Color.clear)
                        }
                        .onDelete(perform: viewModel.deleteRecentCases)
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Color.warmBackground)
            // Fires every time this tab becomes visible again (not just
            // once at launch), so a case created/progressed elsewhere
            // shows up here without restarting the app.
            .onAppear { Task { await viewModel.refresh() } }
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: ConferenceRoute.self) { route in
                destination(for: route)
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        isPresentingAccount = true
                    } label: {
                        Image(systemName: "person.crop.circle")
                    }
                    .accessibilityLabel("Account")
                }
                if !viewModel.recentCases.isEmpty {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        EditButton()
                    }
                }
            }
            .sheet(isPresented: $isPresentingAccount) {
                AccountView(user: currentUser, onDeleteAccount: onDeleteAccount) {
                    isPresentingAccount = false
                    onSignOut()
                }
            }
            .sheet(isPresented: $viewModel.isPresentingPaywall, onDismiss: {
                // Runs after the sheet has actually finished closing, so
                // this push never races the dismiss animation (see
                // ConferenceHomeViewModel.paywallDidUnlockAccess()).
                if viewModel.consumePaywallUnlock() {
                    path.append(.newCase)
                }
            }) {
                PaywallView {
                    viewModel.paywallDidUnlockAccess()
                }
            }
            .sheet(isPresented: $viewModel.isPresentingAIConsent, onDismiss: {
                // Same race-avoidance pattern as the paywall above: resume
                // startNewCase() only after the sheet has actually finished
                // closing, and only if "I Agree" (not "Not Now"/swipe) is
                // what closed it.
                if viewModel.consumeAIConsentGranted() {
                    Task {
                        if await viewModel.startNewCase() {
                            path.append(.newCase)
                        }
                    }
                }
            }) {
                AIDataConsentView(
                    onAgree: { viewModel.recordAIConsent() },
                    onCancel: { viewModel.isPresentingAIConsent = false }
                )
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Heart Team Prep")
                .font(.title2.weight(.bold))
                .foregroundStyle(.primary)
            Text("Preoperative conference prep")
                .font(.subheadline)
                .foregroundStyle(Color.slateText)
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    private func rowInsets(top: CGFloat, bottom: CGFloat) -> EdgeInsets {
        EdgeInsets(top: top, leading: 20, bottom: bottom, trailing: 20)
    }

    @ViewBuilder
    private func destination(for route: ConferenceRoute) -> some View {
        switch route {
        case .newCase:
            ConferenceIntakeView(path: $path)
        case .interview(let caseId, let initialCase):
            ConferenceInterviewView(caseId: caseId, initialCase: initialCase, path: $path)
        case .heartTeamResponses(let caseId):
            HeartTeamResponsesView(caseId: caseId, path: $path)
        case .heartTeamEvidence(let caseId, let role):
            HeartTeamEvidenceView(caseId: caseId, role: role, path: $path)
        case .report(let caseId):
            ConferenceReportView(caseId: caseId, path: $path)
        case .followUpQuestions(_, let entries):
            ConferenceFollowUpQuestionsView(entries: entries)
        case .referenceLookup(let caseId, let topic, let searchIntent):
            ConferenceReferenceLookupView(caseId: caseId, topic: topic, searchIntent: searchIntent, path: $path)
        case .articleDetail(let article):
            PubMedArticleDetailView(article: article)
        }
    }
}

#if DEBUG
private enum ConferenceHomeViewPreviewFactory {
    static func populated() -> ConferenceHomeView {
        ConferenceHomeView(onSignOut: {}, viewModel: ConferenceHomeViewModel(previewRecentCases: [
            ConferenceCaseSummary(id: "1", title: "68-year-old man, severe aortic stenosis, planned SAVR", createdAt: Date(), status: .collectingInformation),
            ConferenceCaseSummary(id: "2", title: "54-year-old woman, three-vessel CAD, CABG vs. PCI", createdAt: Date().addingTimeInterval(-86_400), status: .readyToFinalize),
            ConferenceCaseSummary(id: "3", title: "72-year-old man, mitral regurgitation, planned repair", createdAt: Date().addingTimeInterval(-172_800), status: .completed)
        ]))
    }
}

#Preview("Empty") {
    ConferenceHomeView(onSignOut: {})
}

#Preview("Populated") {
    ConferenceHomeViewPreviewFactory.populated()
}
#endif
