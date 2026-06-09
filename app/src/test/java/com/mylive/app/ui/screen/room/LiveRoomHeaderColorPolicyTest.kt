package com.mylive.app.ui.screen.room

import com.mylive.app.ui.theme.md_theme_dark_onSurface
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRoomHeaderColorPolicyTest {

    @Test
    fun refreshIconUsesDarkThemeForegroundColor() {
        assertEquals(
            md_theme_dark_onSurface,
            resolveLiveRoomRefreshIconTint(md_theme_dark_onSurface)
        )
    }
}
