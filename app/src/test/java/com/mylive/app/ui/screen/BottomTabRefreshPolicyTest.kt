package com.mylive.app.ui.screen

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    @Test
    fun tabletWidthUsesFixedLeftNavigationRailForMainTabs() {
        val source = readMainSource("com/mylive/app/ui/screen/IndexScreen.kt")

        assertTrue(source.contains("NavigationRail("))
        assertTrue(source.contains("NavigationRailItem("))
        assertTrue(source.contains("val useSideNavigation = indexUseSideNavigation("))
        assertTrue(source.contains("if (useSideNavigation)"))
    }

    @Test
    fun sideNavigationUsesCompactBottomPaddingForTopLevelTabs() {
        val source = readMainSource("com/mylive/app/ui/screen/IndexScreen.kt")

        assertTrue(source.contains("val contentBottomPadding = indexTopLevelContentBottomPaddingDp(useSideNavigation).dp"))
        assertTrue(source.contains("contentBottomPadding = contentBottomPadding"))
        assertTrue(source.contains("val revealBottomBar = if (useSideNavigation)"))
        assertTrue(source.contains("bottomBarOffsetPx = indexBottomBarOffsetAfterRevealRequest"))
    }

    @Test
    fun sideNavigationStartsAtTabletWidth() {
        assertFalse(indexUseSideNavigation(screenWidthDp = 599))
        assertTrue(indexUseSideNavigation(screenWidthDp = 600))
        assertTrue(indexUseSideNavigation(screenWidthDp = 840))
    }

    @Test
    fun sideNavigationDoesNotReservePhoneBottomBarSpace() {
        assertEquals(96, indexTopLevelContentBottomPaddingDp(useSideNavigation = false))
        assertEquals(24, indexTopLevelContentBottomPaddingDp(useSideNavigation = true))
    }

    @Test
    fun tabletHomeLiveRoomGridUsesFiveColumnsAndThreeSkeletonRows() {
        val indexSource = readMainSource("com/mylive/app/ui/screen/IndexScreen.kt")
        val homeSource = readMainSource("com/mylive/app/ui/screen/home/HomeScreen.kt")

        assertTrue(indexSource.contains("val homeLiveRoomGridColumns = indexHomeLiveRoomGridColumns(useSideNavigation)"))
        assertTrue(indexSource.contains("homeLiveRoomGridColumns = homeLiveRoomGridColumns"))
        assertTrue(indexSource.contains("internal fun indexHomeLiveRoomGridColumns(useSideNavigation: Boolean): Int?"))
        assertTrue(homeSource.contains("homeLiveRoomGridColumns: Int? = null"))
        assertTrue(homeSource.contains("homeLiveRoomGridColumns?.let { GridCells.Fixed(it) }"))
        assertTrue(homeSource.contains("homeLiveRoomGridColumns?.let { it * 3 } ?: 8"))
    }

    @Test
    fun sideNavigationItemsEvenlyFillRailHeight() {
        val source = readMainSource("com/mylive/app/ui/screen/IndexScreen.kt")
        val railBlock = source.substringAfter("private fun IndexSideNavigationRail(")

        assertTrue(source.contains(".fillMaxHeight()"))
        assertTrue(source.contains(".weight(indexSideNavigationItemWeight(items.size))"))
        assertFalse(railBlock.contains("Column("))
        assertFalse(railBlock.contains(".fillMaxWidth(),"))
        assertEquals(1f, indexSideNavigationItemWeight(itemCount = 4), 0.001f)
    }

    private fun readMainSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java", relativePath),
            File("My_Live/app/src/main/java", relativePath)
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Cannot find source file for $relativePath")
        return file.readText()
    }
}
