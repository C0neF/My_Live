package com.mylive.app.ui.screen.room.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DanmakuSettingsRenderingTest {
    @Test
    fun scaleSettingsReachTheRendererAsFontSizeRatherThanTinyMultiplierPixels() {
        val controller = DanmakuController(ApplicationProvider.getApplicationContext<Context>())
        applyDanmakuConfig(controller, DanmakuConfig(size = 1.4, speed = 1.2, area = 0.8, opacity = 0.7))
        assertEquals(22.4f, controller.danmuSize, 0.001f)
        assertEquals(1.2f, controller.danmuSpeed, 0.0f)
        assertEquals(0.8f, controller.danmuArea, 0.0f)
        assertEquals(0.7f, controller.danmuOpacity, 0.0f)
    }

    @Test
    fun defaultAndLegacySettingsStillRenderAtNormalSizeAndSpeed() {
        val controller = DanmakuController(ApplicationProvider.getApplicationContext<Context>())
        applyDanmakuConfig(controller, DanmakuConfig())
        assertEquals(16f, controller.danmuSize, 0.0f)
        assertEquals(1f, controller.danmuSpeed, 0.0f)
        applyDanmakuConfig(controller, DanmakuConfig(size = 20.0, fontWeight = 6))
        assertEquals(20f, controller.danmuSize, 0.0f)
        assertEquals(600, controller.danmuFontWeight)
    }
}
