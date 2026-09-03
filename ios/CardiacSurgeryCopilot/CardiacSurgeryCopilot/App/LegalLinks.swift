//
//  LegalLinks.swift
//  CardiacSurgeryCopilot
//
//  The app's real, publicly accessible legal document URLs -- one source
//  of truth shared by every screen that links to them (WelcomeView,
//  PaywallView) rather than each declaring its own copy.
//
//  TODO: no custom domain is configured yet (see website/README.md) --
//  these point at the GitHub Pages default URL for this repo's website/
//  directory. Update once a real domain (e.g. cardiacsurgerycopilot.app)
//  is chosen and the website's CNAME is set.
//

import Foundation

enum LegalLinks {
    static let termsOfUse = URL(string: "https://ebender001.github.io/CardiacSurgeryCopilot/terms-of-use.html")!
    static let privacyPolicy = URL(string: "https://ebender001.github.io/CardiacSurgeryCopilot/privacy-policy.html")!
}
