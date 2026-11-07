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
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(viewModel.topic)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.primary)
                    if !viewModel.searchIntent.isEmpty {
                        Text(viewModel.searchIntent)
                            .font(.subheadline)
                            .foregroundStyle(Color.slateText)
                    }
                    if !results.isEmpty {
                        Text("\(results.count) articles from PubMed")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.copilotPrimaryText)
                            .padding(.top, 2)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.bottom, 4)

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
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 32)
        }
    }

    private func articleRow(_ article: PubMedArticle) -> some View {
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 8) {
                Text(Self.tidy(article.title))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.leading)
                    .lineLimit(4)
                HStack(spacing: 6) {
                    if let journal = article.journal {
                        Text(journal)
                            .font(.caption)
                            .italic()
                            .foregroundStyle(Color.slateText)
                            .lineLimit(1)
                    }
                    if let year = article.year {
                        Text(year)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(Color.copilotPrimaryText)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(Color.copilotPrimaryText.opacity(0.12)))
                    }
                }
            }
            Spacer(minLength: 8)
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.slateText.opacity(0.5))
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(.secondarySystemGroupedBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(Color.primary.opacity(0.06), lineWidth: 1)
        )
        .contentShape(Rectangle())
    }

    /// PubMed titles often carry double spaces where inline markup was
    /// stripped, and a trailing period.
    private static func tidy(_ title: String) -> String {
        var t = title.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
        if t.hasSuffix(".") { t.removeLast() }
        return t
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
