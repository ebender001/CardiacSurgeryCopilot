//
//  ConferenceHomeView.swift
//  CardiacSurgeryCopilot
//
//  Root of the Heart Team tab's own NavigationStack (see HomeTabView).
//  Everything in this workflow (intake, interview, report) belongs here as
//  pushes onto this stack, mirroring MMCoach's single-workflow HomeView --
//  just scoped to one of the app's two tabs instead of the whole app.
//

import SwiftUI

struct ConferenceHomeView: View {
    @StateObject private var viewModel: ConferenceHomeViewModel
    @State private var path: [ConferenceRoute] = []
    let onSignOut: () -> Void

    init(onSignOut: @escaping () -> Void, viewModel: ConferenceHomeViewModel? = nil) {
        self.onSignOut = onSignOut
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
                        path.append(.newCase)
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
                                path.append(.detail(caseId: record.id, initialCase: nil))
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
                        onSignOut()
                    } label: {
                        Image(systemName: "person.crop.circle")
                    }
                    .accessibilityLabel("Account")
                }
                #if DEBUG
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        path.append(.heartTeamResponses(caseId: HeartTeamResponsesView.debugPreviewCaseId))
                    } label: {
                        Image(systemName: "ladybug.fill")
                    }
                    .accessibilityLabel("Debug: skip to Heart Team")
                }
                #endif
                if !viewModel.recentCases.isEmpty {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        EditButton()
                    }
                }
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Heart Team")
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
        case .detail(let caseId, let initialCase):
            if let initialCase {
                ConferenceCaseDebugView(caseId: caseId, initialCase: initialCase)
            } else {
                ComingSoonView(title: "Case", detail: "Case \(caseId) -- the report view isn't built yet.")
            }
        case .heartTeamResponses(let caseId):
            HeartTeamResponsesView(caseId: caseId)
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
