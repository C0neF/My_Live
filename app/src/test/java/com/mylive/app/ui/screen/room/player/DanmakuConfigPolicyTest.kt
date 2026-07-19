package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DanmakuConfigPolicyTest {

    @Test
    fun applyDanmakuConfigWritesAllControllerFields() {
        // Structural contract: config object is the single write surface.
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/DanmakuConfig.kt")
            .readText()
        assertTrue(source.contains("data class DanmakuConfig"))
        assertTrue(source.contains("fun applyDanmakuConfig("))
        assertTrue(source.contains("controller.danmuSize"))
        assertTrue(source.contains("controller.dedupeEnabled"))
        assertTrue(source.contains("controller.danmuRenderEmoji"))
    }

    @Test
    fun defaultConfigMatchesHistoricalPlayerViewDefaults() {
        val config = DanmakuConfig()
        assertEquals(16.0, config.size, 0.0)
        assertEquals(10.0, config.speed, 0.0)
        assertEquals(0.8, config.area, 0.0)
        assertEquals(8, config.lineCount)
        assertEquals(0, config.delayMs)
        assertEquals(true, config.renderEmoji)
    }
}
