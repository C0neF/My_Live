package com.mylive.app.ui.screen.room.player

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
}
