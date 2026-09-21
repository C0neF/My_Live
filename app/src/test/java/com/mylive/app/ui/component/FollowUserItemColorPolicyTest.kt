package com.mylive.app.ui.component

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class FollowUserItemColorPolicyTest {

    @Test
    fun douyinBadgeTextUsesWhiteInDarkThemeAndBlackInLightTheme() {
        assertEquals(
            Color.White,
            followPlatformBadgeTextColor(
                siteId = "douyin",
                themeBackground = Color.Black,
                defaultColor = Color.Black
            )
        )
        assertEquals(
            Color.Black,
            followPlatformBadgeTextColor(
                siteId = "douyin",
                themeBackground = Color.White,
                defaultColor = Color.Black
            )
        )
    }
}
