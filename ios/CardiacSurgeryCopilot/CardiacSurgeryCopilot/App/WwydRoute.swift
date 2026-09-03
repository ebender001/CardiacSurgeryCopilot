//
//  WwydRoute.swift
//  CardiacSurgeryCopilot
//
//  Typed navigation destinations for the What Would You Do tab's own
//  NavigationStack (see WwydHomeView). Minimal for now -- grows a
//  `.conversation` case once that workflow's UI is built.
//

import Foundation

enum WwydRoute: Hashable {
    case newCase
    case detail(caseId: String)
}
