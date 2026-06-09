package com.mylive.app.ui.screen.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaySettingsScreenPolicyTest {

    @Test
    fun hardwareDecodeUsesSwitchInsteadOfMenuRow() {
        val source = File("src/main/java/com/mylive/app/ui/screen/settings/PlaySettingsScreen.kt").readText()
        val hardwareDecodeSource = source.substringAfter("title = stringResource(R.string.play_hw_decode_title)")
            .substringBefore("HorizontalDivider()", missingDelimiterValue = "")

        assertTrue(source.contains("SettingsSwitch(\n                title = stringResource(R.string.play_hw_decode_title)"))
        assertTrue(hardwareDecodeSource.contains("checked = hardwareDecode"))
        assertTrue(hardwareDecodeSource.contains("onCheckedChange = { viewModel.setHardwareDecode(it) }"))
        assertFalse(source.contains("private val hwDecodeValues"))
        assertFalse(source.contains("play_hw_decode_options"))
    }

    @Test
    fun playSettingsExposeCellularQualityAndRoomBehaviorWithoutPlayerSc() {
        val source = File("src/main/java/com/mylive/app/ui/screen/settings/PlaySettingsScreen.kt").readText()

        assertTrue(source.contains("val qualityLevelCellular by viewModel.qualityLevelCellular.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("数据网络清晰度"))
        assertTrue(source.contains("viewModel.setQualityLevelCellular(it)"))
        assertTrue(source.contains("val autoFullScreen by viewModel.autoFullScreen.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("val autoPipOnExit by viewModel.autoPipOnExit.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("val pipHideDanmu by viewModel.pipHideDanmu.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("进入直播间自动全屏"))
        assertTrue(source.contains("退出时自动小窗"))
        assertTrue(source.contains("进入小窗隐藏弹幕"))
        assertFalse(source.contains("播放器中显示SC"))
        assertFalse(source.contains("setPlayerShowSuperChat"))
    }
}
