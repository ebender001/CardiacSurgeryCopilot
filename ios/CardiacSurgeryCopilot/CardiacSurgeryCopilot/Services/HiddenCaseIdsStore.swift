//
//  HiddenCaseIdsStore.swift
//  CardiacSurgeryCopilot
//
//  Each recent-cases list (Heart Team, What Would You Do) is sourced
//  entirely from the backend (see BackendService.listConferenceCases /
//  listWwydCases) -- this store holds only a small local denylist of case
//  ids the trainee has swiped away, so "removing" an item from a list
//  stays a per-device UI preference rather than deleting the case's
//  backend record. One store per workflow, distinguished by `namespace`,
//  since the two case id spaces are otherwise unrelated (a conference
//  case id and a WWYD case id could theoretically collide as raw
//  strings).
//

import Foundation

struct HiddenCaseIdsStore {
    private let defaults: UserDefaults
    private let storageKey: String

    init(namespace: String, defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.storageKey = "dev.benderapps.CardiacSurgeryCopilot.hiddenCaseIds.\(namespace)"
    }

    func all() -> Set<String> {
        Set(defaults.stringArray(forKey: storageKey) ?? [])
    }

    func hide(id: String) {
        var ids = all()
        ids.insert(id)
        defaults.set(Array(ids), forKey: storageKey)
    }
}
