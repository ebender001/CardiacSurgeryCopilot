//
//  HeartTeamEvidenceView.swift
//  CardiacSurgeryCopilot
//
//  Reached from HeartTeamResponsesView's "Evidence" button. Two sections
//  -- evidence supporting the selected role's recommendation, and
//  evidence favoring an alternative to it -- each restricted server-side
//  to the last 10 years (see backend/README.md). Either section (or
//  both) may legitimately be empty; no citation is ever fabricated.
//

import SwiftUI

struct HeartTeamEvidenceView: View {
    @StateObject private var viewModel: HeartTeamEvidenceViewModel
    @Binding var path: [ConferenceRoute]
    private let role: HeartTeamRole

    init(caseId: String, role: HeartTeamRole, path: Binding<[ConferenceRoute]>, viewModel: HeartTeamEvidenceViewModel? = nil) {
        self.role = role
        _path = path
        _viewModel = StateObject(wrappedValue: viewModel ?? HeartTeamEvidenceViewModel(caseId: caseId, role: role))
    }

    private static let loadingMessages = [
        "Searching PubMed for supporting evidence…",
        "Searching PubMed for opposing evidence…",
        "Filtering to the last 10 years…"
    ]

    var body: some View {
        ZStack {
            content
                .background(Color.warmBackground)

            if viewModel.isLoading {
                AnalyzingOverlay(title: "Searching the literature", messages: Self.loadingMessages)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.isLoading)
        // Title/subtitle make the role unambiguous -- this same screen is
        // reachable for any of the three roles, with entirely different
        // results each time.
        .navigationTitle("\(role.displayName) Evidence")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
    }

    @ViewBuilder
    private var content: some View {
        if let evidence = viewModel.evidence {
            evidenceList(evidence)
        } else if let errorMessage = viewModel.errorMessage {
            ScrollView {
                ListErrorState(title: "Couldn't load evidence", message: errorMessage) {
                    Task { await viewModel.load() }
                }
                .padding(20)
            }
        } else {
            Color.clear
        }
    }

    private func evidenceList(_ evidence: HeartTeamRoleEvidence) -> some View {
        List {
            Section {
                Text("Literature for and against the \(role.displayName.lowercased())'s recommendation, limited to the last 10 years.")
                    .font(.subheadline)
                    .foregroundStyle(Color.slateText)
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)

            evidenceSection(
                title: "Supports the \(role.displayName)",
                articles: evidence.pro.results
            )
            evidenceSection(
                title: "Favors an Alternative to the \(role.displayName)",
                articles: evidence.con.results
            )
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    @ViewBuilder
    private func evidenceSection(title: String, articles: [PubMedArticle]) -> some View {
        Section {
            if articles.isEmpty {
                Text("No matching articles found in the last 10 years.")
                    .font(.footnote)
                    .foregroundStyle(Color.slateText)
                    .listRowBackground(Color.clear)
            } else {
                ForEach(articles) { article in
                    Button {
                        path.append(.articleDetail(article))
                    } label: {
                        articleRow(article)
                    }
                    .buttonStyle(.plain)
                    .listRowBackground(Color.clear)
                }
            }
        } header: {
            Text(title)
        }
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
extension HeartTeamRoleEvidence {
    static let debugExample = HeartTeamRoleEvidence(
        caseId: "abc123",
        role: .surgeon,
        pro: PubMedEvidenceSet(
            query: "coronary artery bypass[tiab] AND three-vessel[tiab] AND mortality[tiab]",
            results: [
                PubMedArticle(
                    pmid: "30165975",
                    title: "Ten-year outcomes of a randomized trial of coronary artery bypass grafting versus percutaneous coronary intervention",
                    authors: ["Head SJ", "Milojevic M", "Daemen J"],
                    journal: "Lancet",
                    year: "2018",
                    abstractSections: [
                        PubMedAbstractSection(label: "Background", text: "Coronary artery bypass grafting (CABG) or percutaneous coronary intervention (PCI) are alternative treatments for patients with complex coronary artery disease…"),
                        PubMedAbstractSection(label: "Conclusions", text: "For patients with three-vessel or left main disease, CABG resulted in significantly lower ten-year mortality than PCI.")
                    ],
                    url: "https://pubmed.ncbi.nlm.nih.gov/30165975/"
                )
            ]
        ),
        con: PubMedEvidenceSet(
            query: "percutaneous coronary intervention[tiab] AND multivessel[tiab] AND outcomes[tiab]",
            results: []
        )
    )
}

#Preview {
    NavigationStack {
        HeartTeamEvidenceView(
            caseId: "abc123",
            role: .surgeon,
            path: .constant([]),
            viewModel: HeartTeamEvidenceViewModel(caseId: "abc123", role: .surgeon, previewEvidence: .debugExample)
        )
    }
}
#endif
