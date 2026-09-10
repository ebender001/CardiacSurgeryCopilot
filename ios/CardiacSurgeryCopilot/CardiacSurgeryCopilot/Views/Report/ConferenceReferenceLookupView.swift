//
//  ConferenceReferenceLookupView.swift
//  CardiacSurgeryCopilot
//
//  Reached by tapping an evidence/guideline topic in ConferenceReportView.
//  Searches PubMed live for that topic -- the trainee reviews and picks
//  their own sources; nothing here is written back onto the report.
//

import SwiftUI

struct ConferenceReferenceLookupView: View {
    @StateObject private var viewModel: ConferenceReferenceLookupViewModel
    @Binding var path: [ConferenceRoute]

    init(caseId: String, topic: String, searchIntent: String, path: Binding<[ConferenceRoute]>, viewModel: ConferenceReferenceLookupViewModel? = nil) {
        _path = path
        _viewModel = StateObject(wrappedValue: viewModel ?? ConferenceReferenceLookupViewModel(caseId: caseId, topic: topic, searchIntent: searchIntent))
    }

    var body: some View {
        ZStack {
            content
                .background(Color.warmBackground)

            if viewModel.isLoading {
                AnalyzingOverlay(title: "Searching the literature", messages: [
                    "Searching PubMed for \(viewModel.topic)…",
                    "Filtering to the most relevant results…"
                ])
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.isLoading)
        .navigationTitle("PubMed Results")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.search() }
    }

    @ViewBuilder
    private var content: some View {
        if let results = viewModel.results {
            resultsList(results)
        } else if let errorMessage = viewModel.errorMessage {
            ScrollView {
                ListErrorState(title: "Couldn't search PubMed", message: errorMessage) {
                    Task { await viewModel.search() }
                }
                .padding(20)
            }
        } else {
            Color.clear
        }
    }

    private func resultsList(_ results: [PubMedArticle]) -> some View {
        List {
            Section {
                Text(viewModel.topic)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                if !viewModel.searchIntent.isEmpty {
                    Text(viewModel.searchIntent)
                        .font(.footnote)
                        .foregroundStyle(Color.slateText)
                }
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)

            Section {
                if results.isEmpty {
                    Text("No matching articles found.")
                        .font(.footnote)
                        .foregroundStyle(Color.slateText)
                } else {
                    ForEach(results) { article in
                        Button {
                            path.append(.articleDetail(article))
                        } label: {
                            articleRow(article)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private func articleRow(_ article: PubMedArticle) -> some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                Text(article.title)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.primary)
                    .lineLimit(3)
                Text(articleSubtitle(article))
                    .font(.caption)
                    .foregroundStyle(Color.slateText)
            }
            Spacer(minLength: 8)
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.slateText.opacity(0.6))
        }
        .padding(.vertical, 4)
    }

    private func articleSubtitle(_ article: PubMedArticle) -> String {
        [article.journal, article.year].compactMap { $0 }.joined(separator: " • ")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        ConferenceReferenceLookupView(
            caseId: "abc123",
            topic: "CABG vs. PCI in multivessel coronary artery disease with reduced ejection fraction",
            searchIntent: "Randomized trial or guideline evidence comparing long-term outcomes.",
            path: .constant([]),
            viewModel: ConferenceReferenceLookupViewModel(
                caseId: "abc123",
                topic: "CABG vs. PCI in multivessel coronary artery disease with reduced ejection fraction",
                searchIntent: "Randomized trial or guideline evidence comparing long-term outcomes.",
                previewResults: [HeartTeamRoleEvidence.debugExample.pro.results[0]]
            )
        )
    }
}
#endif
