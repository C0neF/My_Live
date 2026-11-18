package com.mylive.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.mylive.app.ui.layout.LocalAppLayout

private val LegacyDefaultSeed = Color(0xFF3498DB)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color.White,
    primaryContainer = DarkElevated,
    onPrimaryContainer = DarkInk,
    secondary = DarkMuted,
    onSecondary = DarkBg,
    secondaryContainer = DarkElevated,
    onSecondaryContainer = DarkInk,
    tertiary = DarkAccent,
    onTertiary = Color.White,
    tertiaryContainer = DarkElevated,
    onTertiaryContainer = DarkInk,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkElevated,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkElevated,
    surfaceContainerHigh = Color(0xFF343438),
    surfaceContainerHighest = Color(0xFF3E3E42),
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
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFD8D8DF),
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
    val effectiveAccent = if (seedColor == LegacyDefaultSeed || seedColor == LightAccent) {
        if (darkTheme) DarkAccent else LightAccent
    } else {
        seedColor
    }
    val onSeed = if (effectiveAccent.luminance() > 0.55f) Color.Black else Color.White
    val colorScheme = base.copy(
        primary = effectiveAccent,
        onPrimary = onSeed,
        primaryContainer = effectiveAccent.copy(alpha = if (darkTheme) 0.16f else 0.10f),
        onPrimaryContainer = effectiveAccent,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (LocalAppLayout.current.tablet) TabletTypography else AppTypography,
        shapes = AppShapes,
        content = content
    )
}
