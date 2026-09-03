//
//  SignedInPlaceholderView.swift
//  CardiacSurgeryCopilot
//
//  Temporary stand-in for the real Home screen (two sections:
//  Preoperative Case Conference / What Would You Do), so the auth flow
//  is fully testable end-to-end before Home is built. Delete this file
//  once RootView routes to the real Home screen instead.
//

import SwiftUI

struct SignedInPlaceholderView: View {
    let currentUser: AuthenticatedUser
    let onSignOut: () -> Void

    var body: some View {
        NavigationStack {
            ZStack {
                Color.warmBackground.ignoresSafeArea()

                VStack(spacing: 16) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(Color.copilotPrimaryText)

                    Text("You're signed in")
                        .font(.title2.weight(.semibold))

                    if let email = currentUser.email {
                        Text(email)
                            .font(.subheadline)
                            .foregroundStyle(Color.slateText)
                    }

                    Text("Home screen (Case Conference / What Would You Do) isn't built yet.")
                        .font(.footnote)
                        .foregroundStyle(Color.slateText)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                        .padding(.top, 8)

                    Button("Sign Out", action: onSignOut)
                        .buttonStyle(.copilotBordered)
                        .padding(.top, 16)
                }
                .padding()
            }
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

#if DEBUG
#Preview {
    SignedInPlaceholderView(
        currentUser: AuthenticatedUser(id: "preview", email: "trainee@example.edu", isEmailVerified: true, signInMethod: .email),
        onSignOut: {}
    )
}
#endif
