package dev.benderapps.heartteamprep.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Brand palette, mirroring the iOS client's `Theme.swift`: deep clinical
 * teal as the primary color, a warm copper accent used sparingly, warm
 * off-white background. Matches the marketing website's clinical-teal
 * palette (website/styles.css).
 */
private val ClinicalTeal = Color(0xFF0E4F49)
private val ClinicalTealLightText = Color(0xFF0E4F49)
private val ClinicalTealDarkText = Color(0xFF5EC7BA)
private val CopperAccentLight = Color(0xFFB5622C)
private val CopperAccentDark = Color(0xFFE09664)
private val WarmBackgroundLight = Color(0xFFF7F5F0)
private val WarmBackgroundDark = Color(0xFF121212)
private val SlateTextLight = Color(0xFF57616B)
private val SlateTextDark = Color(0xFFA0A8B0)

/** Resolved, theme-aware brand colors for the current composition. */
data class HeartTeamPrepColors(
    /**
     * Primary brand color -- deep clinical teal, fixed. Use only as an
     * OPAQUE fill (button backgrounds, solid badge fills) where contrast
     * comes from fixed white text/icons on top of it. For text/icons on an
     * adaptive background, use [primaryText].
     */
    val primary: Color,
    /** [primary], adapted for use as icon/text color in both light and dark. */
    val primaryText: Color,
    /** Warm copper accent -- used sparingly as a secondary highlight, never as body text. */
    val accent: Color,
    val accentTint: Color,
    val primaryTint: Color,
    /** Warm off-white app background, adapting to the system background in dark mode. */
    val warmBackground: Color,
    /** Slate-gray secondary text for subtitles and metadata. */
    val slateText: Color
)

@Composable
fun heartTeamPrepColors(): HeartTeamPrepColors {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) ClinicalTealDarkText else ClinicalTealLightText
    val accent = if (isDark) CopperAccentDark else CopperAccentLight
    return HeartTeamPrepColors(
        primary = ClinicalTeal,
        primaryText = primaryText,
        accent = accent,
        accentTint = accent.copy(alpha = 0.18f),
        primaryTint = ClinicalTeal.copy(alpha = 0.1f),
        warmBackground = if (isDark) WarmBackgroundDark else WarmBackgroundLight,
        slateText = if (isDark) SlateTextDark else SlateTextLight
    )
}
