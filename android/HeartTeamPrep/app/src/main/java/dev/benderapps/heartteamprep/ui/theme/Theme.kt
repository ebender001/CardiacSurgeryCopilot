package dev.benderapps.heartteamprep.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5EC7BA),
    onPrimary = Color(0xFF00201C),
    secondary = Color(0xFFE09664),
    background = Color(0xFF121212),
    surface = Color(0xFF121212)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0E4F49),
    onPrimary = Color.White,
    secondary = Color(0xFFB5622C),
    background = Color(0xFFF7F5F0),
    surface = Color(0xFFF7F5F0)
)

/**
 * App theme built on the fixed clinical-teal brand palette (see [Color.kt]
 * / [heartTeamPrepColors]) rather than Android 12+'s dynamic/Material You
 * color extraction -- brand consistency with the iOS client and website
 * matters more here than matching the person's wallpaper.
 */
@Composable
fun HeartTeamPrepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
