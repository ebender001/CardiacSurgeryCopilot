//
//  WwydHomeView.swift
//  CardiacSurgeryCopilot
//
//  Root of the What Would You Do tab's own NavigationStack (see
//  HomeTabView). Mirrors ConferenceHomeView's structure.
//

import SwiftUI

struct WwydHomeView: View {
    @StateObject private var viewModel: WwydHomeViewModel
    @State private var path: [WwydRoute] = []
    let onSignOut: () -> Void

    init(onSignOut: @escaping () -> Void, viewModel: WwydHomeViewModel? = nil) {
        self.onSignOut = onSignOut
        _viewModel = StateObject(wrappedValue: viewModel ?? WwydHomeViewModel())
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
                        icon: "bubble.left.and.bubble.right.fill",
                        title: "Start a New Discussion",
                        subtitle: "Dictate or type a case to discuss"
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
                    Text("Recent Discussions")
                        .font(.headline)
                        .foregroundStyle(.primary)
                } header: { EmptyView() }
                    .listRowSeparator(.hidden)
                    .listRowInsets(rowInsets(top: 0, bottom: 4))
                    .listRowBackground(Color.clear)

                if viewModel.recentCases.isEmpty, let errorMessage = viewModel.recentCasesErrorMessage {
                    Section {
                        ListErrorState(title: "Couldn't load Recent Discussions", message: errorMessage) {
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
                            WwydEmptyState()
                        }
                    }
                    .listRowSeparator(.hidden)
                    .listRowInsets(rowInsets(top: 0, bottom: 4))
                    .listRowBackground(Color.clear)
                } else {
                    Section {
                        ForEach(viewModel.recentCases) { record in
                            Button {
                                path.append(.detail(caseId: record.id))
                            } label: {
                                WwydCaseRow(record: record)
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
            .onAppear { Task { await viewModel.refresh() } }
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: WwydRoute.self) { route in
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
            Text("What Would You Do")
                .font(.title2.weight(.bold))
                .foregroundStyle(.primary)
            Text("Clinical judgment practice")
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
    private func destination(for route: WwydRoute) -> some View {
        switch route {
        case .newCase:
            ComingSoonView(title: "New Discussion", detail: "Case intake (dictation) isn't built yet.")
        case .detail(let caseId):
            ComingSoonView(title: "Discussion", detail: "Case \(caseId) -- the conversation view isn't built yet.")
        }
    }
}

#if DEBUG
private enum WwydHomeViewPreviewFactory {
    static func populated() -> WwydHomeView {
        WwydHomeView(onSignOut: {}, viewModel: WwydHomeViewModel(previewRecentCases: [
            WwydCaseSummary(id: "1", title: "58-year-old man, acute type A dissection", createdAt: Date(), status: .active),
            WwydCaseSummary(id: "2", title: "66-year-old woman, infective endocarditis", createdAt: Date().addingTimeInterval(-86_400), status: .archived)
        ]))
    }
}

#Preview("Empty") {
    WwydHomeView(onSignOut: {})
}

#Preview("Populated") {
    WwydHomeViewPreviewFactory.populated()
}
#endif
