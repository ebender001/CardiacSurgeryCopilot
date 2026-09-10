//
//  PubMedArticleDetailView.swift
//  CardiacSurgeryCopilot
//
//  Reached by tapping an article row in HeartTeamEvidenceView.
//

import SwiftUI

struct PubMedArticleDetailView: View {
    let article: PubMedArticle

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(article.title)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)

                metadataRow

                if !article.abstractSections.isEmpty {
                    Divider()
                    abstractView
                }

                if let url = URL(string: article.url) {
                    Link(destination: url) {
                        Label("View on PubMed", systemImage: "arrow.up.right.square")
                    }
                    .font(.subheadline.weight(.semibold))
                    .padding(.top, 4)
                }
            }
            .padding(20)
        }
        .background(Color.warmBackground)
        .navigationTitle("Article")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var metadataRow: some View {
        VStack(alignment: .leading, spacing: 4) {
            if !article.authors.isEmpty {
                Text(article.authors.joined(separator: ", "))
                    .font(.footnote)
                    .foregroundStyle(Color.slateText)
            }
            let journalYear = [article.journal, article.year].compactMap { $0 }.joined(separator: " • ")
            if !journalYear.isEmpty {
                Text(journalYear)
                    .font(.footnote)
                    .foregroundStyle(Color.slateText)
            }
            Text("PMID: \(article.pmid)")
                .font(.caption)
                .foregroundStyle(Color.slateText)
                .textSelection(.enabled)
        }
    }

    private var abstractView: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array(article.abstractSections.enumerated()), id: \.offset) { _, section in
                VStack(alignment: .leading, spacing: 4) {
                    if let label = section.label {
                        Text(label)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                    }
                    Text(section.text)
                        .font(.body)
                        .foregroundStyle(.primary)
                }
            }
        }
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        PubMedArticleDetailView(article: HeartTeamRoleEvidence.debugExample.pro.results[0])
    }
}
#endif
