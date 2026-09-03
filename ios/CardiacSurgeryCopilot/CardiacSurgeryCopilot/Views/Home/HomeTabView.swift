//
//  HomeTabView.swift
//  CardiacSurgeryCopilot
//
//  The signed-in app's root: two independent workflows (Heart Team,
//  What Would You Do), each owning its own NavigationStack -- unlike
//  MMCoach's single-workflow app (one NavigationStack off a plain
//  HomeView), this app has two genuinely separate workflows, so a
//  TabView is the right top-level container rather than forcing both
//  into one stack.
//
//  Sign-out currently lives behind each tab's toolbar "person.crop.circle"
//  button as a direct action (no confirmation, no full account screen
//  yet) -- replace with a real AccountView (sign out + delete account +
//  subscription management) once that's built.
//

import SwiftUI

struct HomeTabView: View {
    let currentUser: AuthenticatedUser
    let onSignOut: () -> Void
    @State private var isConfirmingSignOut = false

    var body: some View {
        TabView {
            ConferenceHomeView(onSignOut: { isConfirmingSignOut = true })
                .tabItem {
                    Label("Heart Team", systemImage: "person.3.fill")
                }

            WwydHomeView(onSignOut: { isConfirmingSignOut = true })
                .tabItem {
                    Label("What Would You Do", systemImage: "bubble.left.and.bubble.right.fill")
                }
        }
        .tint(Color.copilotPrimaryText)
        .confirmationDialog(
            "Sign Out",
            isPresented: $isConfirmingSignOut,
            titleVisibility: .visible
        ) {
            Button("Sign Out", role: .destructive, action: onSignOut)
            Button("Cancel", role: .cancel) {}
        } message: {
            if let email = currentUser.email {
                Text(email)
            }
        }
    }
}

#if DEBUG
#Preview {
    HomeTabView(
        currentUser: AuthenticatedUser(id: "preview", email: "trainee@example.edu", isEmailVerified: true, signInMethod: .email),
        onSignOut: {}
    )
}
#endif
