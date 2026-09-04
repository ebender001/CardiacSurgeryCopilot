//
//  HiddenCaseIdsStore.swift
//  CardiacSurgeryCopilot
//
//  The Recent Cases list is sourced entirely from the backend (see
//  BackendService.listConferenceCases) -- this store holds only a small
//  local denylist of case ids the trainee has swiped away, so "removing"
//  an item from the list stays a per-device UI preference rather than
//  deleting the case's backend record. Takes a `namespace` (currently
//  always "conference") so a future second workflow's case ids -- an
//  unrelated id space -- can't collide with this one's as raw strings.
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
