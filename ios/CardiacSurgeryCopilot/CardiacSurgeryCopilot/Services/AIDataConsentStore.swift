//
//  AIDataConsentStore.swift
//  CardiacSurgeryCopilot
//
//  Persists whether the trainee has acknowledged that case data is sent
//  to OpenAI (see AIDataConsentView, gated in
//  ConferenceHomeViewModel.startNewCase() before the New Case screen --
//  and everything reachable from it -- is ever shown). Device-local,
//  matching HiddenCaseIdsStore's pattern. Ported from MMCoach's
//  AIDataConsentStore.
//

import Foundation

struct AIDataConsentStore {
    private let defaults: UserDefaults
    private let storageKey = "dev.benderapps.HeartTeamPrep.hasConsentedToAIDataSharing"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var hasConsented: Bool {
        get { defaults.bool(forKey: storageKey) }
        nonmutating set { defaults.set(newValue, forKey: storageKey) }
    }
}
