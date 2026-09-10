//
//  LegalLinks.swift
//  CardiacSurgeryCopilot
//
//  The app's real, publicly accessible legal document URLs -- one source
//  of truth shared by every screen that links to them (WelcomeView,
//  PaywallView) rather than each declaring its own copy.
//

import Foundation

enum LegalLinks {
    static let termsOfUse = URL(string: "https://heartteam.net/terms-of-use.html")!
    static let privacyPolicy = URL(string: "https://heartteam.net/privacy-policy.html")!
}
