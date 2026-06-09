package com.mylive.app.ui.screen.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DanmuSettingsScreenPolicyTest {

    @Test
    fun danmuSettingsExposeShieldGroupAndManagementEntry() {
        val source = File("src/main/java/com/mylive/app/ui/screen/settings/DanmuSettingsScreen.kt").readText()

        assertTrue(source.contains("弹幕屏蔽"))
        assertTrue(source.contains("val danmuShieldEnable by viewModel.danmuShieldEnable.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("val danmuKeywordShieldEnable by viewModel.danmuKeywordShieldEnable.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("val danmuUserShieldEnable by viewModel.danmuUserShieldEnable.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("viewModel.setDanmuShieldEnable(it)"))
        assertTrue(source.contains("viewModel.setDanmuKeywordShieldEnable(it)"))
        assertTrue(source.contains("viewModel.setDanmuUserShieldEnable(it)"))
        assertTrue(source.contains("打开屏蔽管理"))
        assertTrue(source.contains("navigator.navigate(Route.SettingsDanmuShield)"))
    }

    @Test
    fun danmuSettingsExposeReferenceDisplayOptions() {
        val source = File("src/main/java/com/mylive/app/ui/screen/settings/DanmuSettingsScreen.kt").readText()

        assertTrue(source.contains("val danmuLineCount by viewModel.danmuLineCount.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("val danmuDelay by viewModel.danmuDelay.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("val danmuTopMargin by viewModel.danmuTopMargin.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("val danmuBottomMargin by viewModel.danmuBottomMargin.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("val danmuDedupeStep by viewModel.danmuDedupeStep.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("显示几行"))
        assertTrue(source.contains("全局弹幕延迟"))
        assertTrue(source.contains("顶部安全边距"))
        assertTrue(source.contains("底部安全边距"))
        assertTrue(source.contains("过滤步长"))
        assertTrue(source.contains("viewModel.setDanmuLineCount(it)"))
        assertTrue(source.contains("viewModel.setDanmuDelay(it.toDouble())"))
        assertTrue(source.contains("viewModel.setDanmuTopMargin(it.toDouble())"))
        assertTrue(source.contains("viewModel.setDanmuBottomMargin(it.toDouble())"))
        assertTrue(source.contains("viewModel.setDanmuDedupeStep(it)"))
    }
}
