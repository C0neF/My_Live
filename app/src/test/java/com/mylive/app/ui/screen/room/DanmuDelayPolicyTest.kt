package com.mylive.app.ui.screen.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DanmuDelayPolicyTest {

    @Test
    fun platformDelayOverridesGlobalDelay() {
        val json = updateDanmuDelayBySite("{}", "huya", 1200)

        assertEquals(1200, resolveDanmuDelayMs(globalDelayMs = 300, delayBySiteJson = json, siteId = "huya"))
        assertEquals(300, resolveDanmuDelayMs(globalDelayMs = 300, delayBySiteJson = json, siteId = "douyu"))
    }

    @Test
    fun invalidDelayJsonFallsBackToGlobalDelay() {
        assertEquals(500, resolveDanmuDelayMs(globalDelayMs = 500, delayBySiteJson = "{", siteId = "bilibili"))
    }

    @Test
    fun roomSettingsExposePlatformDelayAndPlayerUsesResolvedDelay() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertTrue(source.contains("平台补偿延迟"))
        assertTrue(source.contains("resolveDanmuDelayMs("))
        assertTrue(source.contains("viewModel.setDanmuDelayBySiteJson("))
    }
}
