package com.mylive.app.ui.screen.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoomSettingsPanelPolicyTest {

    @Test
    fun strictDedupeLabelUsesCorrectWording() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertTrue(source.contains("严格去重模式"))
        assertFalse(source.contains("严父去重模式"))
    }

    @Test
    fun roomSettingsUsesStyledSliderWrapper() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val panelSource = source.substringAfter("fun RoomSettingsPanel(")
            .substringBefore("private fun RoomSettingsSlider(")

        assertTrue(source.contains("private fun RoomSettingsSlider("))
        assertTrue(panelSource.contains("RoomSettingsSlider("))
        assertFalse(panelSource.contains("\n                        Slider("))
        assertFalse(panelSource.contains("\n                            Slider("))

        val sliderSource = source.substringAfter("private fun RoomSettingsSlider(")
        assertTrue(sliderSource.contains("Canvas("))
        assertTrue(sliderSource.contains("StrokeCap.Round"))
        assertFalse(sliderSource.contains("\n    Slider("))
    }

    @Test
    fun roomSettingsIncludesPlayerDanmakuControls() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val panelSource = source.substringAfter("fun RoomSettingsPanel(")
            .substringBefore("private fun RoomSettingsSlider(")

        assertTrue(panelSource.contains("播放器弹幕设置"))
        assertTrue(panelSource.contains("liveRoomPreferences: LiveRoomPreferences"))
        assertTrue(panelSource.contains("val danmuEnable = liveRoomPreferences.danmuEnable"))
        assertTrue(panelSource.contains("val danmuRenderEmoji = liveRoomPreferences.danmuRenderEmoji"))
        assertTrue(panelSource.contains("val danmuSize = liveRoomPreferences.danmuSize"))
        assertTrue(panelSource.contains("val danmuSpeed = liveRoomPreferences.danmuSpeed"))
        assertTrue(panelSource.contains("val danmuArea = liveRoomPreferences.danmuArea"))
        assertTrue(panelSource.contains("val danmuOpacity = liveRoomPreferences.danmuOpacity"))
        assertTrue(panelSource.contains("val danmuStrokeWidth = liveRoomPreferences.danmuStrokeWidth"))
        assertFalse(panelSource.contains(".collectAsState("))

        assertTrue(panelSource.contains("viewModel.setDanmuEnable(it)"))
        assertTrue(panelSource.contains("viewModel.setDanmuRenderEmoji(it)"))
        assertTrue(panelSource.contains("viewModel.setDanmuSize(it.toDouble())"))
        assertTrue(panelSource.contains("viewModel.setDanmuSpeed(it.toDouble())"))
        assertTrue(panelSource.contains("viewModel.setDanmuArea(it.toDouble())"))
        assertTrue(panelSource.contains("viewModel.setDanmuOpacity(it.toDouble())"))
        assertTrue(panelSource.contains("viewModel.setDanmuStrokeWidth(it.toDouble())"))
    }

    @Test
    fun roomSettingsIncludesShieldAndLiveBehaviorControlsWithoutPlayerSc() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val panelSource = source.substringAfter("fun RoomSettingsPanel(")
            .substringBefore("private fun RoomSettingsSlider(")

        assertTrue(panelSource.contains("val danmuShieldEnable = liveRoomPreferences.danmuShieldEnable"))
        assertTrue(panelSource.contains("val danmuKeywordShieldEnable = liveRoomPreferences.danmuKeywordShieldEnable"))
        assertTrue(panelSource.contains("val danmuUserShieldEnable = liveRoomPreferences.danmuUserShieldEnable"))
        assertTrue(panelSource.contains("弹幕屏蔽设置"))
        assertTrue(panelSource.contains("启用弹幕屏蔽"))
        assertTrue(panelSource.contains("关键词屏蔽"))
        assertTrue(panelSource.contains("用户屏蔽"))
        assertTrue(panelSource.contains("viewModel.setDanmuShieldEnable(it)"))
        assertTrue(panelSource.contains("viewModel.setDanmuKeywordShieldEnable(it)"))
        assertTrue(panelSource.contains("viewModel.setDanmuUserShieldEnable(it)"))

        assertTrue(panelSource.contains("val autoFullScreen = liveRoomPreferences.autoFullScreen"))
        assertTrue(panelSource.contains("val autoPipOnExit = liveRoomPreferences.autoPipOnExit"))
        assertTrue(panelSource.contains("val pipHideDanmu = liveRoomPreferences.pipHideDanmu"))
        assertTrue(panelSource.contains("直播间行为设置"))
        assertTrue(panelSource.contains("进入直播间自动全屏"))
        assertTrue(panelSource.contains("退出时自动小窗"))
        assertTrue(panelSource.contains("进入小窗隐藏弹幕"))
        assertTrue(panelSource.contains("viewModel.setAutoFullScreen(it)"))
        assertTrue(panelSource.contains("viewModel.setAutoPipOnExit(it)"))
        assertTrue(panelSource.contains("viewModel.setPipHideDanmu(it)"))

        assertFalse(panelSource.contains("播放器中显示SC"))
        assertFalse(panelSource.contains("setPlayerShowSuperChat"))
    }

    @Test
    fun roomSettingsIncludesAdvancedDanmakuControlsAndShieldManagerEntry() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val panelSource = source.substringAfter("fun RoomSettingsPanel(")
            .substringBefore("private fun RoomSettingsSlider(")

        assertTrue(panelSource.contains("onOpenShieldSettings"))
        assertTrue(panelSource.contains("打开屏蔽管理"))
        assertTrue(panelSource.contains("val danmuLineCount = liveRoomPreferences.danmuLineCount"))
        assertTrue(panelSource.contains("val danmuDelay = liveRoomPreferences.danmuDelay"))
        assertTrue(panelSource.contains("val danmuTopMargin = liveRoomPreferences.danmuTopMargin"))
        assertTrue(panelSource.contains("val danmuBottomMargin = liveRoomPreferences.danmuBottomMargin"))
        assertTrue(panelSource.contains("val danmuDedupeStep = liveRoomPreferences.danmuDedupeStep"))
        assertTrue(panelSource.contains("显示几行"))
        assertTrue(panelSource.contains("全局弹幕延迟"))
        assertTrue(panelSource.contains("顶部安全边距"))
        assertTrue(panelSource.contains("底部安全边距"))
        assertTrue(panelSource.contains("过滤步长"))
        assertTrue(panelSource.contains("viewModel.setDanmuLineCount(it.toInt())"))
        assertTrue(panelSource.contains("viewModel.setDanmuDelay(it.toDouble())"))
        assertTrue(panelSource.contains("viewModel.setDanmuTopMargin(it.toDouble())"))
        assertTrue(panelSource.contains("viewModel.setDanmuBottomMargin(it.toDouble())"))
        assertTrue(panelSource.contains("viewModel.setDanmuDedupeStep(it.toInt())"))
    }

    @Test
    fun roomSettingsUsesCollapsibleSectionsToReduceSliderMisTouches() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val panelSource = source.substringAfter("fun RoomSettingsPanel(")
            .substringBefore("private fun RoomSettingsSlider(")

        assertTrue(source.contains("enum class RoomSettingsSectionKey"))
        assertTrue(source.contains("defaultExpandedRoomSettingsSections()"))
        assertTrue(source.contains("private fun RoomSettingsCollapsibleSection("))
        assertTrue(panelSource.contains("RoomSettingsCollapsibleSection("))
        assertTrue(panelSource.contains("RoomSettingsSectionKey.PLAYER_DANMAKU"))
        assertTrue(panelSource.contains("RoomSettingsSectionKey.LIVE_BEHAVIOR"))
        assertTrue(panelSource.contains("RoomSettingsSectionKey.CHAT"))
        assertTrue(panelSource.contains("RoomSettingsSectionKey.SHIELD"))
        assertTrue(panelSource.contains("RoomSettingsSectionKey.FILTER"))
    }

    @Test
    fun roomSettingsDefaultExpansionKeepsOnlyPlayerDanmakuOpen() {
        assertTrue(defaultExpandedRoomSettingsSections().contains(RoomSettingsSectionKey.PLAYER_DANMAKU))
        assertFalse(defaultExpandedRoomSettingsSections().contains(RoomSettingsSectionKey.LIVE_BEHAVIOR))
        assertFalse(defaultExpandedRoomSettingsSections().contains(RoomSettingsSectionKey.CHAT))
        assertFalse(defaultExpandedRoomSettingsSections().contains(RoomSettingsSectionKey.SHIELD))
        assertFalse(defaultExpandedRoomSettingsSections().contains(RoomSettingsSectionKey.FILTER))
    }

    @Test
    fun roomSettingsCollapsibleSectionsUseStateDrivenMotion() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val sectionSource = source.substringAfter("private fun RoomSettingsCollapsibleSection(")
            .substringBefore("@Composable\nprivate fun RoomSettingsSwitchRow(")

        assertTrue(source.contains("import androidx.compose.animation.animateContentSize"))
        assertTrue(source.contains("import androidx.compose.animation.expandVertically"))
        assertTrue(source.contains("import androidx.compose.animation.shrinkVertically"))
        assertTrue(sectionSource.contains(".animateContentSize("))
        assertTrue(sectionSource.contains("enter = expandVertically("))
        assertTrue(sectionSource.contains("exit = shrinkVertically("))
        assertTrue(sectionSource.contains("animateFloatAsState("))
        assertTrue(sectionSource.contains("rotationZ = arrowRotation"))
    }
}
