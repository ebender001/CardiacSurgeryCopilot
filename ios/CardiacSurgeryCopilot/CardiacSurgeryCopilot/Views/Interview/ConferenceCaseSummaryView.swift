//
//  ConferenceCaseSummaryView.swift
//  CardiacSurgeryCopilot
//
//  Presented as a bottom sheet from ConferenceInterviewView -- automatically
//  the first time a follow-up question is shown, and on demand afterward
//  via that screen's persistent "Case Summary" toolbar button -- so the
//  trainee has their original dictated/typed case description in front of
//  them while answering. Purely informational, same spirit as
//  ConferenceFollowUpQuestionsView; there's nothing to edit here (the
//  narrative can only be changed by starting a new case).
//

import SwiftUI

struct ConferenceCaseSummaryView: View {
    let narrative: String

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                subtitle

                VStack(alignment: .leading, spacing: 10) {
                    Text(narrative)
                        .font(.body)
                        .foregroundStyle(.primary)
                }
                .polishedCard()
            }
            .padding(20)
        }
        .background(Color.warmBackground)
        .navigationTitle("Case Summary")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Done") { dismiss() }
            }
        }
    }

    /// Tells the trainee this sheet isn't a one-time interruption -- it can
    /// be reopened anytime via ConferenceInterviewView's persistent toolbar
    /// button, so dismissing it here doesn't mean losing access to it.
    private var subtitle: some View {
        Text("You can reopen this anytime using the icon in the upper-right corner.")
            .font(.subheadline)
            .foregroundStyle(Color.slateText)
    }
}

#Preview {
    NavigationStack {
        ConferenceCaseSummaryView(narrative: "A 64-year-old male presents with an NSTEMI. Echocardiogram shows LVEF 35%, moderate mitral regurgitation, and mild aortic stenosis. He is an insulin-dependent diabetic, admitted yesterday. Cardiac catheterization shows three-vessel coronary artery disease: 90% proximal LAD stenosis, 70% stenosis of a large OM1 branch, and 100% occlusion of the RCA with good left-to-right collaterals to a moderate-sized PDA. He is currently asymptomatic on IV heparin and nitroglycerin. He is a Jehovah's Witness.")
    }
}
