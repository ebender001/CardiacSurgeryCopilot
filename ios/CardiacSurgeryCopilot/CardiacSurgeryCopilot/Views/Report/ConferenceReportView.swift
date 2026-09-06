//
//  ConferenceReportView.swift
//  CardiacSurgeryCopilot
//
//  The nine-section preoperative case conference report -- eight prose
//  sections plus a tappable list of evidence/guideline topics. Reached
//  either from an already-`completed` Recent Cases row or from
//  HeartTeamResponsesView's "View Full Report" button; ConferenceReportViewModel
//  finalizes the case on the fly if it hasn't been already, so this view
//  doesn't need to know which path got it here.
//

import SwiftUI

struct ConferenceReportView: View {
    @StateObject private var viewModel: ConferenceReportViewModel
    @Binding var path: [ConferenceRoute]
    private let caseId: String

    init(caseId: String, path: Binding<[ConferenceRoute]>, viewModel: ConferenceReportViewModel? = nil) {
        self.caseId = caseId
        _path = path
        _viewModel = StateObject(wrappedValue: viewModel ?? ConferenceReportViewModel(caseId: caseId))
    }

    private static let loadingMessages = [
        "Reviewing the case as a whole…",
        "Weighing operative strategy and alternatives…",
        "Checking recent evidence and guidelines…"
    ]

    var body: some View {
        ZStack {
            content
                .background(Color.warmBackground)

            if viewModel.isLoading {
                AnalyzingOverlay(title: "Preparing the report", messages: Self.loadingMessages)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.isLoading)
        .navigationTitle("Case Report")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
    }

    @ViewBuilder
    private var content: some View {
        if let report = viewModel.report {
            reportList(report)
        } else if let errorMessage = viewModel.errorMessage {
            ScrollView {
                ListErrorState(title: "Couldn't load the report", message: errorMessage) {
                    Task { await viewModel.load() }
                }
                .padding(20)
            }
        } else {
            Color.clear
        }
    }

    private func reportList(_ report: ConferenceReport) -> some View {
        List {
            section("Diagnosis", report.diagnosis)
            section("Indication", report.indication)
            section("Missing Information", report.missingInformation)
            section("Operative Strategy", report.operativeStrategy)
            section("Alternatives", report.alternatives)
            section("Controversies", report.controversies)
            section("Technical Considerations", report.technicalConsiderations)
            section("Postoperative Concerns", report.postoperativeConcerns)
            evidenceSection(report.evidenceGuidelines)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private func section(_ title: String, _ text: String) -> some View {
        Section {
            Text(text)
                .font(.body)
                .foregroundStyle(.primary)
                .padding(.vertical, 4)
        } header: {
            Text(title)
        }
    }

    private func evidenceSection(_ topics: [ConferenceReferenceTopic]) -> some View {
        Section {
            if topics.isEmpty {
                Text("No evidence or guideline topics identified for this case.")
                    .font(.footnote)
                    .foregroundStyle(Color.slateText)
            } else {
                ForEach(Array(topics.enumerated()), id: \.offset) { _, topic in
                    Button {
                        path.append(.referenceLookup(caseId: caseId, topic: topic.topic, searchIntent: topic.searchIntent))
                    } label: {
                        topicRow(topic)
                    }
                    .buttonStyle(.plain)
                }
            }
        } header: {
            Text("Evidence & Guidelines")
        }
    }

    private func topicRow(_ topic: ConferenceReferenceTopic) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(topic.topic)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.primary)
            if !topic.searchIntent.isEmpty {
                Text(topic.searchIntent)
                    .font(.caption)
                    .foregroundStyle(Color.slateText)
                    .lineLimit(2)
            }
        }
        .padding(.vertical, 4)
    }
}

#if DEBUG
extension ConferenceReport {
    static let debugExample = ConferenceReport(
        diagnosis: "64-year-old man with an NSTEMI, LVEF 35%, moderate MR, mild AS, and angiographically severe three-vessel coronary artery disease (90% proximal LAD, 70% large OM1, 100% RCA with good left-to-right collaterals).",
        indication: "Class I surgical indication: three-vessel disease with reduced LVEF, a pattern with a demonstrated CABG mortality benefit over PCI.",
        missingInformation: "Viability imaging has not been obtained. A bloodless-surgery protocol has not yet been confirmed with anesthesia and blood bank.",
        operativeStrategy: "CABG x3 (LIMA to LAD, SVG to OM1, SVG to PDA), on-pump, with a cell-salvage and hemodilution-minimizing protocol given his Jehovah's Witness status.",
        alternatives: "Staged multivessel PCI was considered but is a less durable option for a chronic total occlusion, and offers no mortality advantage over CABG in this population.",
        controversies: "Whether to attempt CTO-PCI of the RCA first to reduce operative risk is a genuine point of disagreement among the heart team -- see the interventional cardiologist's response.",
        technicalConsiderations: "Bloodless-surgery protocol: preoperative iron/EPO optimization, intraoperative cell salvage, minimized hemodilution, and a low transfusion threshold discussion with the patient in advance.",
        postoperativeConcerns: "Close glycemic control given his insulin-dependent diabetes; monitor for bleeding given the bloodless-surgery constraints; reassess MR severity post-revascularization.",
        evidenceGuidelines: [
            ConferenceReferenceTopic(
                topic: "CABG vs. PCI in multivessel coronary artery disease with reduced ejection fraction",
                searchIntent: "Randomized trial or guideline evidence comparing long-term outcomes.",
                citation: nil,
                verified: false
            ),
            ConferenceReferenceTopic(
                topic: "Bloodless cardiac surgery in Jehovah's Witness patients",
                searchIntent: "Perioperative blood-conservation protocols and outcomes.",
                citation: nil,
                verified: false
            )
        ]
    )
}

#Preview {
    NavigationStack {
        ConferenceReportView(
            caseId: "abc123",
            path: .constant([]),
            viewModel: ConferenceReportViewModel(caseId: "abc123", previewReport: .debugExample)
        )
    }
}
#endif
