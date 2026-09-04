//
//  ConferenceRoute.swift
//  CardiacSurgeryCopilot
//
//  Typed navigation destinations for the Heart Team tab's own
//  NavigationStack (see ConferenceHomeView).
//

import Foundation

enum ConferenceRoute: Hashable {
    case newCase
    /// The follow-up-question loop (see ConferenceInterviewView).
    /// `initialCase` is the just-created case when reached from
    /// ConferenceIntakeView's submit (avoids a redundant fetch, same
    /// reasoning as MMCoach's `AppRoute.interview(caseId:initialCase:)`)
    /// -- `nil` when reached by tapping an existing Recent Cases row,
    /// since only its id/title/status are known client-side at that
    /// point. A case with no pending question never lands here at all --
    /// both ConferenceIntakeView and ConferenceInterviewView route
    /// straight to `.heartTeamResponses` instead once `nextQuestion`
    /// becomes nil, per the app's design (see HeartTeamResponsesView).
    case interview(caseId: String, initialCase: ConferenceCase?)
    /// Reached once a case needs no more follow-up questions (see
    /// ConferenceIntakeView's submit success handler and
    /// ConferenceInterviewView's own redirect once the last question is
    /// answered).
    case heartTeamResponses(caseId: String)
    /// Reached from HeartTeamResponsesView's "Evidence" button, for
    /// whichever role was selected when it was tapped.
    case heartTeamEvidence(caseId: String, role: HeartTeamRole)
    /// The nine-section report (see ConferenceReportView). Always fetches
    /// on its own -- finalizing the case first if it hasn't been yet --
    /// rather than carrying a precomputed report, since it's reached both
    /// from an already-`completed` Recent Cases row and from
    /// HeartTeamResponsesView's "View Full Report" button on a case that
    /// hasn't been finalized yet.
    case report(caseId: String)
    /// Reached from HeartTeamResponsesView's follow-up-question summary
    /// when the case had at least one -- a read-only list of every
    /// question actually asked and how it was answered. Carries the
    /// entries already loaded by HeartTeamResponsesViewModel rather than
    /// re-fetching (same reasoning as `.articleDetail`).
    case followUpQuestions(caseId: String, entries: [ConferenceConversationEntry])
    /// Reached by tapping an evidence/guideline topic in
    /// ConferenceReportView -- a live, on-demand PubMed search for that
    /// topic (see ConferenceReferenceLookupView).
    case referenceLookup(caseId: String, topic: String, searchIntent: String)
    /// Reached by tapping an article row in HeartTeamEvidenceView or
    /// ConferenceReferenceLookupView. Carries the full article (not just a
    /// pmid) since it's already in memory -- no reason to re-fetch it.
    case articleDetail(PubMedArticle)
}
