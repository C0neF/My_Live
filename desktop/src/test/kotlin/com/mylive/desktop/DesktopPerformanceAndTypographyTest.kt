package com.mylive.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopPerformanceAndTypographyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun allMaterialTypographyStylesUseConfiguredDesktopFontFamilyAndZeroLetterSpacing() {
        val typography = createDesktopTypography(FontFamily.Monospace)
        val styles = listOf(
            typography.displayLarge,
            typography.displayMedium,
            typography.displaySmall,
            typography.headlineLarge,
            typography.headlineMedium,
            typography.headlineSmall,
            typography.titleLarge,
            typography.titleMedium,
            typography.titleSmall,
            typography.bodyLarge,
            typography.bodyMedium,
            typography.bodySmall,
            typography.labelLarge,
            typography.labelMedium,
            typography.labelSmall
        )
        styles.forEach { style ->
            assertEquals(FontFamily.Monospace, style.fontFamily)
            assertEquals(0.sp, style.letterSpacing)
        }
    }

    @Test fun missingSystemFontDirectoryFallsBackToSansSerif() {
        assertEquals(FontFamily.SansSerif, createDesktopFontFamily(temporary.root))
    }

    @Test fun desktopVersionIsAlignedToThreeZeroZero() {
        assertEquals("3.0.0", DESKTOP_VERSION)
    }

    @Test fun playerChromePointerWakePolicyWakesHiddenControlsImmediatelyAndThrottlesHighRateMovesWhileVisible() {
        val origin = Offset(10f, 10f)
        assertTrue(shouldWakePlayerChromeOnPointerEvent(PointerEventType.Move, Offset(11f, 10f), origin, visible = false, elapsedSinceLastWakeMillis = 1L))
        assertFalse(shouldWakePlayerChromeOnPointerEvent(PointerEventType.Move, origin, origin, visible = false, elapsedSinceLastWakeMillis = 500L))
        assertFalse(shouldWakePlayerChromeOnPointerEvent(PointerEventType.Move, Offset(20f, 20f), origin, visible = true, elapsedSinceLastWakeMillis = 16L))
        assertFalse(shouldWakePlayerChromeOnPointerEvent(PointerEventType.Move, Offset(11f, 11f), origin, visible = true, elapsedSinceLastWakeMillis = 300L))
        assertTrue(shouldWakePlayerChromeOnPointerEvent(PointerEventType.Move, Offset(14f, 10f), origin, visible = true, elapsedSinceLastWakeMillis = PLAYER_CHROME_MOVE_THROTTLE_MS))
        assertTrue(shouldWakePlayerChromeOnPointerEvent(PointerEventType.Press, origin, origin, visible = true, elapsedSinceLastWakeMillis = 0L))
        assertTrue(shouldWakePlayerChromeOnPointerEvent(PointerEventType.Enter, origin, origin, visible = true, elapsedSinceLastWakeMillis = 0L))
    }
}
