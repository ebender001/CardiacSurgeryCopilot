//
//  CardiacSurgeryCopilotApp.swift
//  CardiacSurgeryCopilot
//

import SwiftUI

@main
struct CardiacSurgeryCopilotApp: App {
    init() {
        BackendConfig.configureParse()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .tint(.copilotPrimaryText)
        }
    }
}
