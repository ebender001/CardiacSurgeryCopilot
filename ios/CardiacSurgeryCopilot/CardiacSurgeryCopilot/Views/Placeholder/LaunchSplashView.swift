//
//  LaunchSplashView.swift
//  CardiacSurgeryCopilot
//
//  Animated continuation of the static LaunchScreen.storyboard. A
//  storyboard launch screen is a system-rendered snapshot shown before any
//  app code runs, so it can't itself animate -- this view picks up the
//  instant it can, on the same background color the storyboard used, and
//  fades the same LaunchIcon in full-bleed (edge to edge, no scale-up) so
//  the two together read as one continuous reveal rather than a static
//  screen that's replaced by an unrelated animation. See RootView, which
//  overlays this for a beat and cross-fades it away to reveal the real
//  signed-in/signed-out content underneath -- this view only owns the
//  reveal, not the hand-off transition.
//

import SwiftUI

struct LaunchSplashView: View {
    @State private var iconOpacity = 0.0
    private let onFinished: () -> Void

    init(onFinished: @escaping () -> Void = {}) {
        self.onFinished = onFinished
    }

    var body: some View {
        ZStack {
            // Same fixed color as LaunchScreen.storyboard's background AND
            // LaunchIcon's own flat teal fill -- deliberately not
            // `Color.warmBackground` (which is dark-mode adaptive) since
            // the storyboard snapshot this hands off from has no
            // dark-mode variant either. Keeping this teal (not the app's
            // warm cream) means the icon's 0.5s fade-in never reveals a
            // mismatched color underneath it.
            Color(red: 14 / 255, green: 79 / 255, blue: 73 / 255)
                .ignoresSafeArea()

            // Full-bleed: scaled to COVER the entire screen (cropping
            // whichever dimension overflows), not fit within it -- a
            // deliberate change from the previous small, centered,
            // scale-up icon treatment.
            Image("LaunchIcon")
                .resizable()
                .scaledToFill()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
                .ignoresSafeArea()
                .opacity(iconOpacity)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.5)) {
                iconOpacity = 1
            }
            // Hold the fully-revealed icon briefly so the animation reads
            // as a deliberate reveal rather than a flicker, then hand off
            // to the caller (RootView cross-fades this away).
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) {
                onFinished()
            }
        }
    }
}

#Preview {
    LaunchSplashView()
}
