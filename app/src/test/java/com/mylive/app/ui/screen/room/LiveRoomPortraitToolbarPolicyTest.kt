package com.mylive.app.ui.screen.room

import com.mylive.app.ui.theme.md_theme_dark_onSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomPortraitToolbarPolicyTest {

    @Test
    fun toolbarIconsUseForegroundTintInDarkMode() {
        assertEquals(
            md_theme_dark_onSurface,
            resolveLiveRoomToolbarIconTint(md_theme_dark_onSurface)
        )
    }

    @Test
    fun portraitHeaderPlacesSettingsBetweenRefreshAndQuickAccess() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        val refreshIndex = source.indexOf("imageVector = Icons.Default.Refresh")
        val settingsIndex = source.indexOf("imageVector = Icons.Default.Settings")
        val quickAccessIndex = source.indexOf("imageVector = Icons.Default.PlaylistAdd")

        assertTrue(refreshIndex >= 0)
        assertTrue(settingsIndex > refreshIndex)
        assertTrue(quickAccessIndex > settingsIndex)
        assertTrue(source.contains("onSettingsClick = { activeAuxiliaryPanel = PortraitLiveRoomPanel.SETTINGS }"))
    }

    @Test
    fun portraitHeaderDoesNotRenderSecondaryActionRow() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertFalse(source.contains("PortraitSecondaryActionRow("))
    }

    @Test
    fun portraitHeaderOnlyDisplaysHostName() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val headerSource = source.substringAfter("private fun CompactPortraitRoomHeader(")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")

        assertFalse(headerSource.contains("detail.title"))
        assertFalse(headerSource.contains("onlineCount"))
        assertFalse(headerSource.contains(" 在线"))
    }
}
