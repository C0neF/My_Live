package com.mylive.app.ui.screen

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomTabRefreshPolicyTest {

    @Test
    fun homeAndFollowRepeatClicksRefreshEvenWhenScrolled() {
        assertEquals(
            BottomTabRepeatAction.Refresh,
            bottomTabRepeatAction(currentKey = "recommend", clickedKey = "recommend", isCurrentPageAtTop = false)
        )
        assertEquals(
            BottomTabRepeatAction.Refresh,
            bottomTabRepeatAction(currentKey = "follow", clickedKey = "follow", isCurrentPageAtTop = false)
        )
        assertEquals(
            BottomTabRepeatAction.Refresh,
            bottomTabRepeatAction(currentKey = "recommend", clickedKey = "recommend", isCurrentPageAtTop = true)
        )
        assertEquals(
            BottomTabRepeatAction.Refresh,
            bottomTabRepeatAction(currentKey = "follow", clickedKey = "follow", isCurrentPageAtTop = true)
        )
    }

    @Test
    fun categoryRepeatClickKeepsRefreshBehavior() {
        assertEquals(
            BottomTabRepeatAction.Refresh,
            bottomTabRepeatAction(currentKey = "category", clickedKey = "category", isCurrentPageAtTop = false)
        )
    }

    @Test
    fun switchingTabsHasNoRepeatAction() {
        assertEquals(
            BottomTabRepeatAction.None,
            bottomTabRepeatAction(currentKey = "recommend", clickedKey = "follow", isCurrentPageAtTop = false)
        )
    }

    @Test
    fun scrollableContentAtTopRequiresFirstItemAndZeroOffset() {
        assertTrue(isScrollableContentAtTop(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0))
        assertFalse(isScrollableContentAtTop(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0))
        assertFalse(isScrollableContentAtTop(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 1))
    }

    @Test
    fun repeatsOnRefreshableTabsTriggerRefresh() {
        assertTrue(shouldRefreshBottomTab(currentKey = "recommend", clickedKey = "recommend"))
        assertTrue(shouldRefreshBottomTab(currentKey = "follow", clickedKey = "follow"))
        assertTrue(shouldRefreshBottomTab(currentKey = "category", clickedKey = "category"))
    }

    @Test
    fun switchingTabsDoesNotTriggerRefresh() {
        assertFalse(shouldRefreshBottomTab(currentKey = "recommend", clickedKey = "follow"))
        assertFalse(shouldRefreshBottomTab(currentKey = "follow", clickedKey = "category"))
        assertFalse(shouldRefreshBottomTab(currentKey = "category", clickedKey = "recommend"))
    }

    @Test
    fun mineTabDoesNotRefreshOnRepeatClick() {
        assertFalse(shouldRefreshBottomTab(currentKey = "user", clickedKey = "user"))
    }

    @Test
    fun bottomNavActiveColorUsesHomePlatformAccentWhenAvailable() {
        val defaultActiveColor = Color.Blue

        assertEquals(
            Color(0xFFFF5D23),
            indexBottomNavActiveColor(
                homePlatformAccentColor = Color(0xFFFF5D23),
                defaultActiveColor = defaultActiveColor
            )
        )
        assertEquals(
            defaultActiveColor,
            indexBottomNavActiveColor(
                homePlatformAccentColor = null,
                defaultActiveColor = defaultActiveColor
            )
        )
    }

    @Test
    fun revealBottomBarRequestResetsHiddenOffset() {
        assertEquals(0f, indexBottomBarOffsetAfterRevealRequest(currentOffsetPx = 72f))
        assertEquals(0f, indexBottomBarOffsetAfterRevealRequest(currentOffsetPx = 0f))
    }
}
