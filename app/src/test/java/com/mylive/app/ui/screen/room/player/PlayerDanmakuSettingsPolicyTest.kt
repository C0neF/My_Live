package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayerDanmakuSettingsPolicyTest {

    @Test
    fun playerViewPropagatesAdvancedDanmakuSettingsToController() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()

        assertTrue(source.contains("danmuLineCount: Int = 8"))
        assertTrue(source.contains("danmuDelayMs: Int = 0"))
        assertTrue(source.contains("danmuTopMargin: Double = 0.0"))
        assertTrue(source.contains("danmuBottomMargin: Double = 0.0"))
        assertTrue(source.contains("danmuDedupeStep: Int = 2"))
        assertTrue(source.contains("controller.danmuLineCount = danmuLineCount"))
        assertTrue(source.contains("controller.danmuDelayMs = danmuDelayMs"))
        assertTrue(source.contains("controller.danmuTopMargin = danmuTopMargin.toFloat()"))
        assertTrue(source.contains("controller.danmuBottomMargin = danmuBottomMargin.toFloat()"))
        assertTrue(source.contains("controller.dedupeStepSize = danmuDedupeStep"))
    }

    @Test
    fun danmakuSettingsBottomSheetKeepsTitleAndDividerOutsideScrollableContent() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt").readText()
        val bottomSheetSource = source.substringAfter("private fun DanmakuSettingsBottomSheet(")
            .substringBefore("private fun updateControllerConfig(")

        val titleIndex = bottomSheetSource.indexOf("text = \"弹幕快捷设置\"")
        val dividerIndex = bottomSheetSource.indexOf("HorizontalDivider()")
        val scrollIndex = bottomSheetSource.indexOf(".verticalScroll(rememberScrollState())")
        val settingsIndex = bottomSheetSource.indexOf("// Size Selector")

        assertTrue(titleIndex >= 0)
        assertTrue(dividerIndex > titleIndex)
        assertTrue(scrollIndex > dividerIndex)
        assertTrue(settingsIndex > scrollIndex)
    }
}
