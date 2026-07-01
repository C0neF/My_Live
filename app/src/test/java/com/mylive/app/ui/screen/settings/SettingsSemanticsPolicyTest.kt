package com.mylive.app.ui.screen.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsSemanticsPolicyTest {

    @Test
    fun visibleLabelsDescribeActualRuntimeBehavior() {
        val playbackPage = File(
            "src/main/java/com/mylive/app/ui/screen/settings/PlaybackPageSettingsScreen.kt"
        ).readText()
        val strings = File("src/main/res/values/strings.xml").readText()

        assertTrue(playbackPage.contains("播放器快捷入口"))
        assertFalse(playbackPage.contains("全屏长按快捷入口"))
        assertTrue(strings.contains("连续无触摸操作多长时间后自动退出直播间"))
        assertTrue(strings.contains("调试级日志"))
        assertFalse(strings.contains("开启后显示调试信息"))
    }

    @Test
    fun forceHttpsLabelMatchesStrictHttpsBehavior() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val player = File(
            "src/main/java/com/mylive/app/ui/screen/room/player/PlayerController.kt"
        ).readText()

        assertTrue(strings.contains("仅使用HTTPS协议加载直播流"))
        assertFalse(player.contains("listOf(httpsUrl, url)"))
    }
}
