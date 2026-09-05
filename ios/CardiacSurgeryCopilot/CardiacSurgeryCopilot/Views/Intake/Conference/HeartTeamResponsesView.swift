//
//  HeartTeamResponsesView.swift
//  CardiacSurgeryCopilot
//
//  Reached once a Heart Team case needs no more follow-up questions (see
//  ConferenceIntakeView). Presents three independently-generated,
//  in-character responses (surgeon, non-interventional cardiologist,
//  aggressive interventional cardiologist) and lets the trainee pick
//  which one to read -- freely, and repeatedly, as a deliberate practice
//  exercise in weighing different specialty perspectives, not a
//  one-shot choice.
//

import SwiftUI

struct HeartTeamResponsesView: View {
    @StateObject private var viewModel: HeartTeamResponsesViewModel
    @Binding var path: [ConferenceRoute]
    private let caseId: String

    init(caseId: String, path: Binding<[ConferenceRoute]>, viewModel: HeartTeamResponsesViewModel? = nil) {
        self.caseId = caseId
        _path = path
        _viewModel = StateObject(wrappedValue: viewModel ?? HeartTeamResponsesViewModel(caseId: caseId))
    }

    private static let loadingMessages = [
        "Reviewing the case as a surgeon…",
        "Reviewing the case as a cardiologist…",
        "Weighing a catheter-based approach…"
    ]

    var body: some View {
        ZStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header

                    if let responses = viewModel.responses {
                        followUpSummary
                        roleSelector
                        responseCard(for: viewModel.selectedRole, response: responses[viewModel.selectedRole])
                        evidenceButton(for: viewModel.selectedRole)
                        reportButton
                    } else if let errorMessage = viewModel.errorMessage {
                        ListErrorState(title: "Couldn't load heart team responses", message: errorMessage) {
                            Task { await viewModel.load() }
                        }
                    }
                }
                .padding(20)
            }
            .background(Color.warmBackground)

            if viewModel.isLoading {
                AnalyzingOverlay(title: "Convening the heart team", messages: Self.loadingMessages)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.isLoading)
        .navigationTitle("Heart Team")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("What would the heart team say?")
                .font(.title3.weight(.semibold))
                .foregroundStyle(.primary)
            Text("Three members reviewed this case independently. Choose whose take to read -- you can switch anytime as part of the exercise.")
                .font(.subheadline)
                .foregroundStyle(Color.slateText)
        }
    }

    /// Explicit either way -- no follow-up questions were needed is a
    /// meaningful, worth-stating fact about this case, not just an absence
    /// of UI. When there were some, this is how the trainee reviews what
    /// was actually asked and how they answered it.
    @ViewBuilder
    private var followUpSummary: some View {
        if viewModel.conversation.isEmpty {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Color.copilotPrimaryText)
                Text("No follow-up questions were needed for this case.")
                    .font(.footnote)
                    .foregroundStyle(Color.slateText)
            }
        } else {
            Button {
                path.append(.followUpQuestions(caseId: caseId, entries: viewModel.conversation))
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "list.bullet.rectangle")
                        .foregroundStyle(Color.copilotPrimaryText)
                    Text("\(viewModel.conversation.count) follow-up question\(viewModel.conversation.count == 1 ? "" : "s") asked")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(.primary)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.slateText.opacity(0.6))
                }
            }
            .buttonStyle(.plain)
        }
    }

    private var roleSelector: some View {
        HStack(spacing: 10) {
            ForEach(HeartTeamRole.allCases) { role in
                roleChip(role)
            }
        }
    }

    private func roleChip(_ role: HeartTeamRole) -> some View {
        let isSelected = viewModel.selectedRole == role
        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                viewModel.selectedRole = role
            }
        } label: {
            VStack(spacing: 6) {
                Image(systemName: role.icon)
                    .font(.title3)
                Text(role.shortName)
                    .font(.caption.weight(.semibold))
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .foregroundStyle(isSelected ? .white : Color.copilotPrimaryText)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(isSelected ? Color.copilotPrimary : Color.copilotPrimaryTint)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }

    private func responseCard(for role: HeartTeamRole, response: HeartTeamResponse) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(role.displayName)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.slateText)
                .textCase(.uppercase)

            Text(response.recommendation)
                .font(.headline)
                .foregroundStyle(.primary)

            if response.classOfRecommendation != nil || response.levelOfEvidence != nil {
                gradeBadges(classOfRecommendation: response.classOfRecommendation, levelOfEvidence: response.levelOfEvidence)
            }

            Divider()

            Text(response.rationale)
                .font(.body)
                .foregroundStyle(.primary)
        }
        .polishedCard()
        // Keys the card to the selected role so switching roles reads as
        // a distinct swap rather than the same view silently mutating.
        .id(role)
        .transition(.opacity)
    }

    /// Either grade can be present without the other (the AI grades them
    /// independently, and either can fail validation on its own -- see
    /// heartTeamResponseSchema.js), so each badge is shown only if its own
    /// value exists rather than requiring both.
    private func gradeBadges(classOfRecommendation: ClassOfRecommendation?, levelOfEvidence: LevelOfEvidence?) -> some View {
        HStack(spacing: 6) {
            if let classOfRecommendation {
                gradeBadge(text: classOfRecommendation.badgeText, color: classOfRecommendation.color)
            }
            if let levelOfEvidence {
                gradeBadge(text: levelOfEvidence.badgeText, color: levelOfEvidence.color)
            }
        }
    }

    private func gradeBadge(text: String, color: Color) -> some View {
        Text(text)
            .font(.caption2.weight(.bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                Capsule(style: .continuous)
                    .fill(color)
            )
    }

    /// Keyed to the currently selected role, same as the card above --
    /// tapping this always searches evidence for whichever role's
    /// response is on screen right now.
    private func evidenceButton(for role: HeartTeamRole) -> some View {
        Button {
            path.append(.heartTeamEvidence(caseId: caseId, role: role))
        } label: {
            Label("Evidence", systemImage: "text.book.closed.fill")
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .buttonStyle(.copilotBordered)
        .id(role)
        .transition(.opacity)
    }

    /// Not role-specific -- the report covers the whole case, so unlike
    /// evidenceButton this doesn't key off the selected role.
    private var reportButton: some View {
        Button {
            path.append(.report(caseId: caseId))
        } label: {
            Label("View Full Report", systemImage: "doc.text.fill")
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .buttonStyle(.copilotBordered)
    }
}

#if DEBUG
extension HeartTeamResponses {
    /// Shared by the SwiftUI preview below and HeartTeamResponsesView's
    /// debug-shortcut init (see `debugPreviewCaseId`) -- one realistic
    /// canned example instead of two copies drifting apart.
    static let debugExample = HeartTeamResponses(
        caseId: "abc123",
        surgeon: HeartTeamResponse(
            recommendation: "Proceed with CABG x3.",
            rationale: "Three-vessel disease with a reduced ejection fraction of 35% is a class I surgical indication -- this is exactly the population where CABG has shown a mortality benefit over PCI in the major trials. The RCA is a chronic total occlusion, which is durably bypassable but a poor long-term PCI target even in experienced hands. Given his Jehovah's Witness status, I'd want a bloodless-surgery protocol confirmed preoperatively -- cell salvage, minimizing hemodilution, and a low threshold for iron/EPO optimization -- but that doesn't change the indication.",
            classOfRecommendation: .i,
            levelOfEvidence: .bRandomized
        ),
        nonInterventionalCardiologist: HeartTeamResponse(
            recommendation: "Optimize medically and confirm viability before committing to a strategy.",
            rationale: "Before we lock in a revascularization strategy, I want to know how much of that reduced EF is ischemic versus something else contributing -- viability imaging would change my recommendation meaningfully. He's asymptomatic on heparin and nitroglycerin right now, which buys us time to do this properly rather than rushing to the OR or the cath lab. I'd also want his transfusion-avoidance plan worked out with anesthesia and blood bank before we're committed to a bleeding-risk procedure, surgical or not.",
            classOfRecommendation: .iia,
            levelOfEvidence: .cExpertOpinion
        ),
        interventionalCardiologist: HeartTeamResponse(
            recommendation: "Staged multivessel PCI, starting with the LAD.",
            rationale: "I'd start with the proximal LAD given it's the dominant ischemic territory, then stage the OM1, and take on the RCA CTO last once we've confirmed adequate collateral flow isn't masking a bailout need. Yes, this is a CTO with two other significant lesions, but CTO-PCI success rates at an experienced center are well over 85% now, and avoiding a sternotomy in a Jehovah's Witness patient with this bleeding-risk profile is a real advantage, not just a convenience. I'd rather manage staged procedural risk than a single large intraoperative one.",
            classOfRecommendation: .iib,
            levelOfEvidence: .bNonrandomized
        )
    )
}

#Preview {
    NavigationStack {
        HeartTeamResponsesView(
            caseId: "abc123",
            path: .constant([]),
            viewModel: HeartTeamResponsesViewModel(caseId: "abc123", previewResponses: .debugExample)
        )
    }
}
#endif
