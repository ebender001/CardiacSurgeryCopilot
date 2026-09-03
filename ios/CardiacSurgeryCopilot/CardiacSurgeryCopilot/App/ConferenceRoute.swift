//
//  ConferenceRoute.swift
//  CardiacSurgeryCopilot
//
//  Typed navigation destinations for the Heart Team tab's own
//  NavigationStack (see ConferenceHomeView). Grows a real `.interview`/
//  `.report` case (mirroring MMCoach's AppRoute) once those workflow
//  screens are built -- `.detail` is a placeholder/debug stand-in for both
//  today (see ConferenceHomeView.destination(for:)).
//

import Foundation

enum ConferenceRoute: Hashable {
    case newCase
    /// `initialCase` is the just-created case when reached from
    /// ConferenceIntakeView's submit (avoids a redundant fetch, same
    /// reasoning as MMCoach's `AppRoute.interview(caseId:initialCase:)`)
    /// -- `nil` when reached by tapping an existing Recent Cases row,
    /// since only its id/title/status are known client-side at that point.
    case detail(caseId: String, initialCase: ConferenceCase?)
    /// Reached once a case needs no more follow-up questions (see
    /// ConferenceIntakeView's submit success handler).
    case heartTeamResponses(caseId: String)
}
