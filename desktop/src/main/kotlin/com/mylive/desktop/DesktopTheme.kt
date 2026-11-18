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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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

internal val DesktopButtonShape = RoundedCornerShape(9.dp)
internal val DesktopChipShape = RoundedCornerShape(999.dp)
internal val DesktopFieldShape = RoundedCornerShape(10.dp)
internal val DesktopHairlineWidth = 0.5.dp
internal val DesktopLiveEmerald = Color(0xff34c759)
internal val DesktopSystemRed = Color(0xffff3b30)

private val DesktopShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

internal data class DesktopAccentOption(val value: Long, val name: String, val light: Color, val dark: Color)

internal val DesktopAccentOptions = listOf(
    DesktopAccentOption(0L, "系统蓝", Color(0xff007aff), Color(0xff0a84ff)),
    DesktopAccentOption(0xff1777b8L, "晴空蓝", Color(0xff0077ed), Color(0xff5ac8fa)),
    DesktopAccentOption(0xff8a63d2L, "星云紫", Color(0xff5856d6), Color(0xffbf5af2)),
    DesktopAccentOption(0xff28805aL, "薄荷绿", Color(0xff28cd41), Color(0xff30d158)),
    DesktopAccentOption(0xffb66124L, "暖阳橙", Color(0xffff9500), Color(0xffff9f0a))
)

internal fun desktopAccentColor(value: Long, dark: Boolean): Color =
    DesktopAccentOptions.firstOrNull { it.value == value }?.let { if (dark) it.dark else it.light } ?: Color(value)

internal fun desktopPlatformAccentColor(siteId: String, dark: Boolean): Color? = null

internal fun desktopDarkColorScheme(accent: Color = DesktopAccentOptions.first().dark): ColorScheme {
    val onAccent = if (accent.luminance() > .55f) Color.Black else Color.White
    val background = Color(0xff0c0c0e)
    val surface = Color(0xff1c1c1e)
    val elevated = Color(0xff2c2c2e)
    val ink = Color(0xfff5f5f7)
    val muted = Color(0xff8e8e93)
    return darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = .18f),
        onPrimaryContainer = ink,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accent.copy(alpha = .15f),
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accent.copy(alpha = .12f),
        onTertiaryContainer = ink,
        error = Color(0xffff453a),
        onError = Color.White,
        errorContainer = Color(0x33ff453a),
        onErrorContainer = Color(0xffffd6d2),
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = elevated,
        onSurfaceVariant = muted,
        outline = Color(0xff3a3a3c),
        outlineVariant = Color(0xff2c2c2e),
        surfaceContainer = elevated,
        surfaceContainerHigh = Color(0xff363639),
        surfaceContainerHighest = Color(0xff424246),
        surfaceContainerLow = surface,
        surfaceContainerLowest = background
    )
}

internal fun desktopLightColorScheme(accent: Color = DesktopAccentOptions.first().light): ColorScheme {
    val onAccent = if (accent.luminance() > .55f) Color.Black else Color.White
    val background = Color(0xfff5f5f7)
    val surface = Color.White
    val elevated = Color(0xffe9e9ee)
    val ink = Color(0xff1d1d1f)
    val muted = Color(0xff86868b)
    return lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = .11f),
        onPrimaryContainer = ink,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accent.copy(alpha = .10f),
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accent.copy(alpha = .08f),
        onTertiaryContainer = ink,
        error = DesktopSystemRed,
        onError = Color.White,
        errorContainer = Color(0x1fff3b30),
        onErrorContainer = Color(0xff991b14),
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = elevated,
        onSurfaceVariant = muted,
        outline = Color(0xffd2d2d7),
        outlineVariant = Color(0xffe5e5ea),
        surfaceContainer = elevated,
        surfaceContainerHigh = Color(0xffe2e2e8),
        surfaceContainerHighest = Color(0xffd6d6dd),
        surfaceContainerLow = Color.White,
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
internal val UiSidebar: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < .5f) {
        Color(0xff141416)
    } else {
        Color(0xffececf1)
    }
internal val UiFieldFill: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < .5f) {
        Color(0xff252528)
    } else {
        Color(0xffefeff4)
    }
