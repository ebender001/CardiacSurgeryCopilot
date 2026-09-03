//
//  LaunchSplashView.swift
//  CardiacSurgeryCopilot
//
//  Animated continuation of the static LaunchScreen.storyboard. A
//  storyboard launch screen is a system-rendered snapshot shown before any
//  app code runs, so it can't itself animate -- this view picks up the
//  instant it can, on the same background color the storyboard used, and
//  scales/fades the app icon in so the two together read as one
//  continuous reveal rather than a static screen that's replaced by an
//  unrelated animation. See RootView, which overlays this for a beat
//  before showing the real signed-in/signed-out content underneath.
//

import SwiftUI

struct LaunchSplashView: View {
    @State private var iconOpacity = 0.0
    @State private var iconScale = 0.72
    private let onFinished: () -> Void

    init(onFinished: @escaping () -> Void = {}) {
        self.onFinished = onFinished
    }

    var body: some View {
        ZStack {
            // Same fixed color as LaunchScreen.storyboard's background --
            // deliberately not `Color.warmBackground` (which is dark-mode
            // adaptive) since the storyboard snapshot this hands off from
            // has no dark-mode variant either.
            Color(red: 247 / 255, green: 245 / 255, blue: 240 / 255)
                .ignoresSafeArea()

            Image("LaunchIcon")
                .resizable()
                .scaledToFit()
                .frame(width: 148, height: 148)
                .clipShape(RoundedRectangle(cornerRadius: 32, style: .continuous))
                .shadow(color: .black.opacity(0.12), radius: 16, y: 8)
                .opacity(iconOpacity)
                .scaleEffect(iconScale)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) {
                iconOpacity = 1
                iconScale = 1
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
