package com.mylive.app.ui.screen.room.player

import com.mylive.app.testing.playerViewSource

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayerGestureIndicatorPolicyTest {

    @Test
    fun gestureIndicatorUsesStableWidthForThreeDigitPercent() {
        val source = playerViewSource()
        val indicatorSource = source.substringAfter("internal fun IndicatorOverlay(")

        assertTrue(indicatorSource.contains(".width(72.dp)"))
        assertTrue(indicatorSource.contains(".fillMaxWidth()"))
        assertTrue(indicatorSource.contains("textAlign = TextAlign.Center"))
        assertTrue(indicatorSource.contains("maxLines = 1"))
    }

    @Test
    fun volumeAndBrightnessGestureIndicatorsAppearAtPlayerCenter() {
        val source = playerViewSource()
        val gestureIndicatorSource = source.substringAfter("// Volume indicator overlay")
            .substringBefore("// Controls overlay")

        assertTrue(gestureIndicatorSource.contains("modifier = Modifier.align(Alignment.Center)"))
        assertFalse(gestureIndicatorSource.contains("Alignment.CenterStart"))
        assertFalse(gestureIndicatorSource.contains("Alignment.CenterEnd"))
    }

    @Test
    fun systemVolumeGestureUsesReferenceScalingAndFivePercentSteps() {
        val source = playerViewSource()
        val policySource = source.substringAfter("internal fun systemVolumeForVerticalDrag(")
            .substringBefore("@kotlin.OptIn")

        assertTrue(source.contains("internal const val SystemVolumeGestureSensitivity = 2f"))
        assertTrue(policySource.contains("val delta = -totalDragY / heightPx.coerceAtLeast(1f) * SystemVolumeGestureSensitivity"))
        assertTrue(policySource.contains("return (startVolume + delta).coerceIn(0f, 1f)"))
        assertTrue(source.contains("internal fun quantizeSystemVolumeGesture("))
        assertTrue(source.contains("/ 5f"))
    }

    @Test
    fun volumeGestureAppliesSystemVolumeFromGestureStart() {
        val source = playerViewSource()
        val gestureSource = source.substringAfter("var gestureStartVolume")
            .substringBefore("} else if (isHorizontal)")

        assertTrue(gestureSource.contains("systemVolumeForVerticalDrag("))
        assertTrue(gestureSource.contains("setSystemVolumeDirect(volumeValue)"))
        assertTrue(gestureSource.contains("currentSystemMediaVolume()"))
        assertFalse(gestureSource.contains("playerController?.setVolumeDirect(volumeValue)"))
    }

    @Test
    fun verticalGestureStartsOnlyInTheMiddleOfThePlayer() {
        val source = playerViewSource()

        assertTrue(source.contains("size.height * 0.25f"))
        assertTrue(source.contains("size.height * 0.75f"))
    }

    @Test
    fun systemVolumeGestureThrottlesNativeUpdatesButKeepsIndicatorResponsive() {
        val source = playerViewSource()

        assertTrue(source.contains("SystemVolumeGestureThrottleMs = 200L"))
        assertTrue(source.contains("SystemClock.uptimeMillis()"))
        assertTrue(source.contains("pendingSystemVolume"))
    }
}
