package com.mylive.app.ui.screen.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShieldSettingsScreenPolicyTest {

    @Test
    fun shieldManagementSupportsEditingRules() {
        val screenSource = File("src/main/java/com/mylive/app/ui/screen/settings/ShieldSettingsScreen.kt").readText()
        val viewModelSource = File("src/main/java/com/mylive/app/ui/screen/settings/ShieldSettingsViewModel.kt").readText()

        assertTrue(screenSource.contains("onUpdateKeyword"))
        assertTrue(screenSource.contains("onUpdateUser"))
        assertTrue(screenSource.contains("Icons.Default.Edit"))
        assertTrue(screenSource.contains("点击编辑，点叉移除"))
        assertTrue(viewModelSource.contains("fun updateKeyword"))
        assertTrue(viewModelSource.contains("fun updateUserShield"))
    }

    @Test
    fun shieldManagementSupportsFileAndTextImportExport() {
        val source = File("src/main/java/com/mylive/app/ui/screen/settings/ShieldSettingsScreen.kt").readText()

        assertTrue(source.contains("CreateDocument(\"application/json\")"))
        assertTrue(source.contains("OpenDocument()"))
        assertTrue(source.contains("导出到文件"))
        assertTrue(source.contains("导出为文本"))
        assertTrue(source.contains("从文件导入"))
        assertTrue(source.contains("从文本导入"))
    }

    @Test
    fun presetsCanOverwriteExistingRuleSet() {
        val source = File("src/main/java/com/mylive/app/ui/screen/settings/ShieldSettingsScreen.kt").readText()

        assertTrue(source.contains("覆盖保存"))
        assertTrue(source.contains("onOverwrite"))
    }
}
