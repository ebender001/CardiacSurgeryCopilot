//
//  Theme.swift
//  CardiacSurgeryCopilot
//
//  Shared color palette and button/card/badge styles, so every screen
//  looks like one polished app rather than each view inventing its own
//  treatment. Matches the marketing website's clinical-teal palette
//  (website/styles.css) for brand consistency: deep clinical teal as the
//  primary color, a warm copper accent used sparingly, warm off-white
//  background.
//

import SwiftUI
import UIKit

extension Color {
    /// Primary brand color -- deep clinical teal (#0E4F49), fixed. Use only
    /// as an OPAQUE fill (button backgrounds, solid badge fills) where
    /// contrast comes from the fixed white text/icon on top of it, not from
    /// whatever's behind the view. For text, icons, or anything drawn *on*
    /// an adaptive background, use `copilotPrimaryText`.
    static let copilotPrimary = Color(red: 14 / 255, green: 79 / 255, blue: 73 / 255)

    /// Primary brand color, adapted for use as icon/text color in both
    /// appearances: the deep teal in light mode, a brighter/lighter teal in
    /// dark mode (the deep teal nearly disappears against the near-black
    /// dark-mode background otherwise).
    static let copilotPrimaryText = Color(UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 94 / 255, green: 199 / 255, blue: 186 / 255, alpha: 1)
            : UIColor(red: 14 / 255, green: 79 / 255, blue: 73 / 255, alpha: 1)
    })

    /// Warm copper accent (#B5622C). Used sparingly as a secondary
    /// highlight -- active/in-progress states, a single icon tint -- never
    /// as body text (too low-contrast on white).
    static let copilotAccent = Color(UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 224 / 255, green: 150 / 255, blue: 100 / 255, alpha: 1)
            : UIColor(red: 181 / 255, green: 98 / 255, blue: 44 / 255, alpha: 1)
    })

    /// A soft copper wash safe as a fill behind dark text/icons.
    static let copilotAccentTint = Color.copilotAccent.opacity(0.18)

    /// A soft teal wash safe as a fill behind teal text/icons.
    static let copilotPrimaryTint = Color.copilotPrimary.opacity(0.1)

    /// Warm off-white app background for the clinical-editorial screens
    /// (Home). Adapts to the system background in dark mode rather than
    /// forcing a fixed cream tone, which would look muddy at night.
    static let warmBackground = Color(UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor.systemBackground
            : UIColor(red: 247 / 255, green: 245 / 255, blue: 240 / 255, alpha: 1)
    })

    /// Slate-gray secondary text -- quieter than `.secondary` in most
    /// system fonts, used for subtitles and metadata.
    static let slateText = Color(UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 160 / 255, green: 168 / 255, blue: 176 / 255, alpha: 1)
            : UIColor(red: 87 / 255, green: 97 / 255, blue: 107 / 255, alpha: 1)
    })
}

/// Full-width, filled primary action button (Continue, Submit Answer,
/// Start New Case).
struct CopilotProminentButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.copilotPrimary)
            )
            .shadow(color: isEnabled ? Color.copilotPrimary.opacity(0.25) : .clear, radius: 8, y: 4)
            // Dim the whole composed button (fill + text together) rather
            // than just the fill -- fading only the fill blends with
            // whatever's behind it, which can make a "disabled" button
            // nearly disappear against a dark-mode background instead of
            // reading as disabled.
            .opacity(isEnabled ? 1 : 0.4)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

/// Outlined secondary action button (Try Again, and other non-primary
/// actions that still need clear affordance).
struct CopilotBorderedButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(Color.copilotPrimaryText)
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(Color.copilotPrimaryText, lineWidth: 1.5)
            )
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == CopilotProminentButtonStyle {
    static var copilotProminent: CopilotProminentButtonStyle { CopilotProminentButtonStyle() }
}

extension ButtonStyle where Self == CopilotBorderedButtonStyle {
    static var copilotBordered: CopilotBorderedButtonStyle { CopilotBorderedButtonStyle() }
}

/// A small colored capsule label (case status, category tags).
struct StatusBadge: View {
    let text: String
    let tint: Color
    var textColor: Color = .white

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(textColor)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Capsule().fill(tint))
    }
}

/// The consistent card treatment used for report sections, message
/// bubbles, and similar content blocks: rounded corners, a soft border,
/// and a faint shadow for depth rather than a flat fill.
struct PolishedCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(Color.copilotPrimary.opacity(0.08), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.04), radius: 6, y: 2)
    }
}

extension View {
    func polishedCard() -> some View {
        modifier(PolishedCardModifier())
    }
}
