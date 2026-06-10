package com.mylive.app.ui.screen.room

import androidx.compose.ui.graphics.Color
import com.mylive.app.ui.theme.DarkInk
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRoomHeaderColorPolicyTest {

    @Test
    fun refreshIconUsesDarkThemeForegroundColor() {
        assertEquals(
            DarkInk,
            resolveLiveRoomRefreshIconTint(DarkInk)
        )
    }

    @Test
    fun liveRoomPlatformAccentUsesCurrentSiteBrandColor() {
        val fallback = DarkInk

        assertEquals(Color(0xFFFF5D23), resolveLiveRoomPlatformAccentColor("douyu", fallback))
        assertEquals(Color(0xFFFFD736), resolveLiveRoomPlatformAccentColor("huya", fallback))
        assertEquals(fallback, resolveLiveRoomPlatformAccentColor("douyin", fallback))
        assertEquals(Color(0xFFF07775), resolveLiveRoomPlatformAccentColor("bilibili", fallback))
    }

    @Test
    fun liveRoomAccentSiteIdPrefersRouteSiteBeforeViewModelSettles() {
        assertEquals(
            "douyu",
            resolveLiveRoomAccentSiteId(routeSiteId = " douyu ", viewModelSiteId = "")
        )
        assertEquals(
            "douyu",
            resolveLiveRoomAccentSiteId(routeSiteId = "douyu", viewModelSiteId = "huya")
        )
        assertEquals(
            "huya",
            resolveLiveRoomAccentSiteId(routeSiteId = "", viewModelSiteId = " huya ")
        )
    }
}
