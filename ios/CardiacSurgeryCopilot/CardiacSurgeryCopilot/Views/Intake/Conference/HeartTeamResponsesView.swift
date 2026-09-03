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

    #if DEBUG
    /// Reaching this screen with this sentinel case id skips both AI
    /// calls (case creation + heart-team generation) entirely and loads
    /// canned responses instead -- see ConferenceHomeView's debug toolbar
    /// button. Lets this screen's UI be iterated on without waiting on
    /// the network every time. Never reachable in a Release build.
    static let debugPreviewCaseId = "debug-preview"
    #endif

    init(caseId: String, viewModel: HeartTeamResponsesViewModel? = nil) {
        #if DEBUG
        if viewModel == nil, caseId == Self.debugPreviewCaseId {
            _viewModel = StateObject(wrappedValue: HeartTeamResponsesViewModel(caseId: caseId, previewResponses: .debugExample))
            return
        }
        #endif
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
                        roleSelector
                        responseCard(for: viewModel.selectedRole, response: responses[viewModel.selectedRole])
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
            rationale: "Three-vessel disease with a reduced ejection fraction of 35% is a class I surgical indication -- this is exactly the population where CABG has shown a mortality benefit over PCI in the major trials. The RCA is a chronic total occlusion, which is durably bypassable but a poor long-term PCI target even in experienced hands. Given his Jehovah's Witness status, I'd want a bloodless-surgery protocol confirmed preoperatively -- cell salvage, minimizing hemodilution, and a low threshold for iron/EPO optimization -- but that doesn't change the indication."
        ),
        nonInterventionalCardiologist: HeartTeamResponse(
            recommendation: "Optimize medically and confirm viability before committing to a strategy.",
            rationale: "Before we lock in a revascularization strategy, I want to know how much of that reduced EF is ischemic versus something else contributing -- viability imaging would change my recommendation meaningfully. He's asymptomatic on heparin and nitroglycerin right now, which buys us time to do this properly rather than rushing to the OR or the cath lab. I'd also want his transfusion-avoidance plan worked out with anesthesia and blood bank before we're committed to a bleeding-risk procedure, surgical or not."
        ),
        interventionalCardiologist: HeartTeamResponse(
            recommendation: "Staged multivessel PCI, starting with the LAD.",
            rationale: "I'd start with the proximal LAD given it's the dominant ischemic territory, then stage the OM1, and take on the RCA CTO last once we've confirmed adequate collateral flow isn't masking a bailout need. Yes, this is a CTO with two other significant lesions, but CTO-PCI success rates at an experienced center are well over 85% now, and avoiding a sternotomy in a Jehovah's Witness patient with this bleeding-risk profile is a real advantage, not just a convenience. I'd rather manage staged procedural risk than a single large intraoperative one."
        )
    )
}

#Preview {
    NavigationStack {
        HeartTeamResponsesView(
            caseId: "abc123",
            viewModel: HeartTeamResponsesViewModel(caseId: "abc123", previewResponses: .debugExample)
        )
    }
}
#endif
