package com.mylive.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class DesktopAppearancePreferences(
    val theme: Int = 2,
    val accent: Long = 0,
    val platformOrder: List<String> = listOf("bilibili", "douyu", "huya", "douyin"),
    val textScalePercent: Int = 100
) {
    fun validate() {
        require(theme in 0..2 && accent in 0..0xffffffffL)
        require(platformOrder.size == 4 && platformOrder.toSet() == setOf("bilibili", "douyu", "huya", "douyin"))
        require(textScalePercent in 90..130)
    }
}

internal fun defaultWindowsFontsDir(): File =
    File(System.getenv("WINDIR")?.takeIf { it.isNotBlank() } ?: "C:\\Windows", "Fonts")

@OptIn(ExperimentalTextApi::class)
internal fun createDesktopFontFamily(fontsDir: File = defaultWindowsFontsDir()): FontFamily {
    val chineseFamily = when {
        File(fontsDir, "msyh.ttc").isFile || File(fontsDir, "msyh.ttf").isFile -> "Microsoft YaHei UI"
        File(fontsDir, "Deng.ttf").isFile -> "DengXian"
        File(fontsDir, "simhei.ttf").isFile -> "SimHei"
        else -> null
    }
    val families = buildList {
        if (File(fontsDir, "segoeui.ttf").isFile) add("Segoe UI")
        chineseFamily?.let(::add)
    }
    if (families.isEmpty()) return FontFamily.SansSerif
    return runCatching {
        FontFamily(*families.flatMap { family ->
            listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
                .map { weight -> SystemFont(family, weight = weight) }
        }.toTypedArray())
    }.getOrDefault(FontFamily.SansSerif)
}

internal val DesktopFontFamily: FontFamily by lazy { createDesktopFontFamily() }

private val DesktopBodyLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment(topRatio = 0.15f),
    trim = LineHeightStyle.Trim.None
)

private val DesktopControlLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment(topRatio = 0f),
    trim = LineHeightStyle.Trim.FirstLineTop
)

internal fun createDesktopTypography(fontFamily: FontFamily = DesktopFontFamily): Typography =
    Typography().let { base ->
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = fontFamily, fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopBodyLineHeightStyle),
            displayMedium = base.displayMedium.copy(fontFamily = fontFamily, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopBodyLineHeightStyle),
            displaySmall = base.displaySmall.copy(fontFamily = fontFamily, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopBodyLineHeightStyle),
            headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopBodyLineHeightStyle),
            headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopBodyLineHeightStyle),
            headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopBodyLineHeightStyle),
            titleLarge = base.titleLarge.copy(fontFamily = fontFamily, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopBodyLineHeightStyle),
            titleMedium = base.titleMedium.copy(fontFamily = fontFamily, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopBodyLineHeightStyle),
            titleSmall = base.titleSmall.copy(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopControlLineHeightStyle),
            bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopBodyLineHeightStyle),
            bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopBodyLineHeightStyle),
            bodySmall = base.bodySmall.copy(fontFamily = fontFamily, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopBodyLineHeightStyle),
            labelLarge = base.labelLarge.copy(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopControlLineHeightStyle),
            labelMedium = base.labelMedium.copy(fontFamily = fontFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopControlLineHeightStyle),
            labelSmall = base.labelSmall.copy(fontFamily = fontFamily, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, fontSynthesis = FontSynthesis.Weight, letterSpacing = 0.sp, lineHeightStyle = DesktopControlLineHeightStyle)
        )
    }

internal val DesktopTypography: Typography by lazy { createDesktopTypography(DesktopFontFamily) }

internal val DesktopButtonShape = RoundedCornerShape(10.dp)
internal val DesktopChipShape = RoundedCornerShape(9.dp)

private val DesktopShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

internal data class DesktopAccentOption(val value: Long, val name: String, val light: Color, val dark: Color)

internal val DesktopAccentOptions = listOf(
    DesktopAccentOption(0L, "默认蓝", Color(0xff2563eb), Color(0xff89b4ff)),
    DesktopAccentOption(0xff1777b8L, "海蓝", Color(0xff0369a1), Color(0xff7dd3fc)),
    DesktopAccentOption(0xff8a63d2L, "暮紫", Color(0xff7c3aed), Color(0xffc4b5fd)),
    DesktopAccentOption(0xff28805aL, "松绿", Color(0xff147d64), Color(0xff6ee7b7)),
    DesktopAccentOption(0xffb66124L, "琥珀", Color(0xffb45309), Color(0xfffbbf6a))
)

internal fun desktopAccentColor(value: Long, dark: Boolean): Color =
    DesktopAccentOptions.firstOrNull { it.value == value }?.let { if (dark) it.dark else it.light } ?: Color(value)

internal fun desktopDarkColorScheme(accent: Color = DesktopAccentOptions.first().dark): ColorScheme {
    val onAccent = if (accent.luminance() > .179f) Color.Black else Color.White
    val background = Color(0xff0e1219)
    val surface = Color(0xff171e29)
    val elevated = Color(0xff222d3c)
    val ink = Color(0xffedf2fa)
    val muted = Color(0xffa7b4c8)
    return darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = .22f),
        onPrimaryContainer = ink,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accent.copy(alpha = .20f),
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accent.copy(alpha = .16f),
        onTertiaryContainer = ink,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = elevated,
        onSurfaceVariant = muted,
        outline = Color(0xff53627a),
        outlineVariant = Color(0xff303d50),
        surfaceContainer = elevated,
        surfaceContainerHigh = Color(0xff29364a),
        surfaceContainerHighest = Color(0xff35435a),
        surfaceContainerLow = surface,
        surfaceContainerLowest = background
    )
}

internal fun desktopLightColorScheme(accent: Color = DesktopAccentOptions.first().light): ColorScheme {
    val onAccent = if (accent.luminance() > .179f) Color.Black else Color.White
    val background = Color(0xfff3f6fb)
    val surface = Color.White
    val elevated = Color(0xffeaf0f8)
    val ink = Color(0xff192539)
    val muted = Color(0xff58687e)
    return lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = .14f),
        onPrimaryContainer = ink,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accent.copy(alpha = .14f),
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accent.copy(alpha = .12f),
        onTertiaryContainer = ink,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = elevated,
        onSurfaceVariant = muted,
        outline = Color(0xffa9b7ca),
        outlineVariant = Color(0xffd6deea),
        surfaceContainer = elevated,
        surfaceContainerHigh = Color(0xffe2eaf5),
        surfaceContainerHighest = Color(0xffd7e2f0),
        surfaceContainerLow = Color(0xfff8faff),
        surfaceContainerLowest = surface
    )
}

@Composable
fun DesktopTheme(preferences: DesktopAppearancePreferences, content: @Composable () -> Unit) {
    val dark = preferences.theme == 2 || preferences.theme == 0 && isSystemInDarkTheme()
    val accent = desktopAccentColor(preferences.accent, dark)
    val scheme = remember(dark, accent) { if (dark) desktopDarkColorScheme(accent) else desktopLightColorScheme(accent) }
    val density = LocalDensity.current
    val textDensity = remember(density.density, density.fontScale, preferences.textScalePercent) {
        Density(density.density, density.fontScale * preferences.textScalePercent / 100f)
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified, LocalDensity provides textDensity) {
        MaterialTheme(colorScheme = scheme, typography = DesktopTypography, shapes = DesktopShapes, content = content)
    }
}

internal val UiBg: Color @Composable get() = MaterialTheme.colorScheme.background
internal val UiSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
internal val UiElevated: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
internal val UiInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface
internal val UiMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val UiAccent: Color @Composable get() = MaterialTheme.colorScheme.primary
internal val UiHairline: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
internal val UiHairlineStrong: Color @Composable get() = MaterialTheme.colorScheme.outline
