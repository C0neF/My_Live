package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayerGestureIndicatorPolicyTest {

    @Test
    fun gestureIndicatorUsesStableWidthForThreeDigitPercent() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val indicatorSource = source.substringAfter("private fun IndicatorOverlay(")

        assertTrue(indicatorSource.contains(".width(72.dp)"))
        assertTrue(indicatorSource.contains(".fillMaxWidth()"))
        assertTrue(indicatorSource.contains("textAlign = TextAlign.Center"))
        assertTrue(indicatorSource.contains("maxLines = 1"))
    }

    @Test
    fun volumeAndBrightnessGestureIndicatorsAppearAtPlayerCenter() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val gestureIndicatorSource = source.substringAfter("// Volume indicator overlay")
            .substringBefore("// Controls overlay")

        assertTrue(gestureIndicatorSource.contains("modifier = Modifier.align(Alignment.Center)"))
        assertFalse(gestureIndicatorSource.contains("Alignment.CenterStart"))
        assertFalse(gestureIndicatorSource.contains("Alignment.CenterEnd"))
    }

    @Test
    fun volumeGestureUsesAccumulatedDragWithHigherSensitivity() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val policySource = source.substringAfter("internal fun playerVolumeForVerticalDrag(")
            .substringBefore("@kotlin.OptIn")

        assertTrue(source.contains("internal const val PlayerVolumeGestureSensitivity = 2.5f"))
        assertTrue(policySource.contains("val delta = -totalDragY / heightPx.coerceAtLeast(1f) * PlayerVolumeGestureSensitivity"))
        assertTrue(policySource.contains("return (startVolume + delta).coerceIn(0f, 1f)"))
    }

    @Test
    fun volumeGestureAppliesDirectVolumeFromGestureStart() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val gestureSource = source.substringAfter("var gestureStartVolume")
            .substringBefore("} else if (isHorizontal)")

        assertTrue(gestureSource.contains("playerVolumeForVerticalDrag("))
        assertTrue(gestureSource.contains("playerController?.setVolumeDirect(volumeValue)"))
        assertFalse(gestureSource.contains("playerController?.setVolume(delta)"))
    }
}
