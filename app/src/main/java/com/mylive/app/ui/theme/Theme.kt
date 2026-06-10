package com.mylive.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Every neutral M3 role is set explicitly so the fixed palette fully replaces the
// Material defaults (which would otherwise leak a tinted gray for surfaceVariant /
// onSurfaceVariant / outlineVariant — roles the app reads in hundreds of places).
private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color.Black,
    primaryContainer = DarkElevated,
    onPrimaryContainer = DarkInk,
    secondary = DarkMuted,
    onSecondary = DarkBg,
    secondaryContainer = DarkElevated,
    onSecondaryContainer = DarkInk,
    tertiary = DarkAccent,
    onTertiary = Color.Black,
    tertiaryContainer = DarkElevated,
    onTertiaryContainer = DarkInk,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkElevated,
    onSurfaceVariant = DarkMuted,
    outline = DarkHairlineStrong,
    outlineVariant = DarkHairline,
    error = DangerDark,
    onError = OnDangerDark,
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = LightSurface,
    onPrimaryContainer = LightInk,
    secondary = LightMuted,
    onSecondary = Color.White,
    secondaryContainer = LightSurface,
    onSecondaryContainer = LightInk,
    tertiary = LightAccent,
    onTertiary = Color.White,
    tertiaryContainer = LightSurface,
    onTertiaryContainer = LightInk,
    background = LightBg,
    onBackground = LightInk,
    surface = Color.White,
    onSurface = LightInk,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightMuted,
    outline = LightHairlineStrong,
    outlineVariant = LightHairline,
    error = DangerLight,
    onError = OnDangerLight,
)

@Composable
fun MyLiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Color = LightAccent,
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    // The accent follows the user's seed; pick legible on-color from its luminance
    // so button/label text stays readable for any chosen color.
    val onSeed = if (seedColor.luminance() > 0.5f) Color.Black else Color.White
    val colorScheme = base.copy(
        primary = seedColor,
        onPrimary = onSeed,
        onPrimaryContainer = seedColor,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
