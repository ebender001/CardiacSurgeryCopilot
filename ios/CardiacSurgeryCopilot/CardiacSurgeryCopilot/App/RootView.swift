//
//  RootView.swift
//  CardiacSurgeryCopilot
//
//  App root: decides between the Welcome/authentication flow and the
//  signed-in app based on `AuthenticationViewModel.state`.
//

import SwiftUI

struct RootView: View {
    @StateObject private var authViewModel = AuthenticationViewModel()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Shown for a brief beat at launch on top of everything below (see
    /// LaunchSplashView) -- dismissed by its own `onFinished` callback,
    /// independent of whether `authViewModel.refreshSession()` has
    /// resolved yet (the `.checkingSession` loading view covers that gap
    /// if the session check happens to run long).
    @State private var isShowingLaunchSplash = true

    var body: some View {
        ZStack {
            Group {
                switch authViewModel.state {
                case .checkingSession, .endingSession:
                    loadingView
                case .signedOut:
                    WelcomeView(viewModel: authViewModel)
                case .signedIn(let user):
                    ConferenceHomeView(onSignOut: {
                        Task { await authViewModel.signOut() }
                    })
                }
            }
            // Without this, switching between the cases above is an instant
            // cut -- SwiftUI has no reason to animate a plain `switch` on
            // its own. This crossfades every state change, including
            // sign-in and the initial checkingSession -> Welcome transition.
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: authViewModel.state)

            if isShowingLaunchSplash {
                LaunchSplashView {
                    withAnimation(.easeOut(duration: 0.35)) {
                        isShowingLaunchSplash = false
                    }
                }
                .transition(.opacity)
            }
        }
        .task { await authViewModel.refreshSession() }
    }

    private var loadingView: some View {
        ZStack {
            Color.warmBackground.ignoresSafeArea()
            ProgressView()
                .tint(Color.copilotPrimaryText)
        }
    }
}

#Preview {
    RootView()
}
