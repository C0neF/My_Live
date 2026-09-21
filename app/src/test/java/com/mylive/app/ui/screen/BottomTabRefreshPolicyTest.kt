package com.mylive.app.ui.screen

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.mylive.app.core.model.AppLayoutMode
import com.mylive.app.ui.layout.resolveAppLayout
import java.io.File

class BottomTabRefreshPolicyTest {

    @Test
    fun hapticRunsOnlyForAnActualPhoneBottomTabSwitch() {
        var hapticCount = 0
        val performHaptic = { hapticCount += 1 }

        performBottomTabSwitchHapticIfNeeded(
            currentKey = "recommend",
            clickedKey = "follow",
            useSideNavigation = false,
            performHaptic = performHaptic
        )
        performBottomTabSwitchHapticIfNeeded(
            currentKey = "follow",
            clickedKey = "follow",
            useSideNavigation = false,
            performHaptic = performHaptic
        )
        performBottomTabSwitchHapticIfNeeded(
            currentKey = "follow",
            clickedKey = "category",
            useSideNavigation = true,
            performHaptic = performHaptic
        )

        assertEquals(1, hapticCount)
    }

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

        assertTrue(source.contains("IndexSideNavigationRail("))
        assertTrue(source.contains("private fun IndexSideNavigationRail("))
        assertTrue(source.contains("RoundedCornerShape(20.dp)"))
        assertTrue(source.contains("color = MaterialTheme.colorScheme.surfaceVariant"))
        assertTrue(source.contains("val useSideNavigation = appLayout.useNavigationRail"))
        assertTrue(source.contains("if (useSideNavigation)"))
    }

    @Test
    fun tabletSideNavigationHonorsSystemBarsAndV2RailWidth() {
        val source = readMainSource("com/mylive/app/ui/screen/IndexScreen.kt")
        val railSource = source.substringAfter("private fun IndexSideNavigationRail(")

        assertTrue(railSource.contains(".width(LocalAppLayout.current.navigationRailWidthDp.dp)"))
        assertTrue(railSource.contains(".statusBarsPadding()"))
        assertTrue(railSource.contains(".navigationBarsPadding()"))
        assertEquals(96, resolveAppLayout(AppLayoutMode.Tablet, 800, 1200, 800, 1f).navigationRailWidthDp)
    }

    @Test
    fun tabletRailProvidesAReachableFixedOrientationSwitch() {
        val source = readMainSource("com/mylive/app/ui/screen/IndexScreen.kt")
        val toggleSource = source.substringAfter("private fun TabletOrientationToggleButton(")

        assertTrue(source.contains("tabletOrientationMode = tabletOrientationMode"))
        assertTrue(source.contains("onToggleTabletOrientation"))
        assertTrue(toggleSource.contains(".size(48.dp)"))
        assertTrue(toggleSource.contains(".testTag(\"tablet_orientation_toggle\")"))
        assertTrue(toggleSource.contains("contentDescription = switchDescription"))
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
        assertFalse(resolveAppLayout(AppLayoutMode.Auto, 599, 1200, 599, 1f).useNavigationRail)
        assertTrue(resolveAppLayout(AppLayoutMode.Auto, 600, 1200, 600, 1f).useNavigationRail)
        assertTrue(resolveAppLayout(AppLayoutMode.Auto, 840, 1200, 840, 1f).useNavigationRail)
    }

    @Test
    fun sideNavigationDoesNotReservePhoneBottomBarSpace() {
        assertEquals(96, indexTopLevelContentBottomPaddingDp(useSideNavigation = false))
        assertEquals(24, indexTopLevelContentBottomPaddingDp(useSideNavigation = true))
    }

    @Test
    fun tabletHomeLiveRoomGridUsesWidthAwareColumnsAndThreeSkeletonRows() {
        val indexSource = readMainSource("com/mylive/app/ui/screen/IndexScreen.kt")
        val homeSource = readMainSource("com/mylive/app/ui/screen/home/HomeScreen.kt")

        assertTrue(indexSource.contains("val homeLiveRoomGridColumns = appLayout.liveRoomColumns"))
        assertTrue(indexSource.contains("homeLiveRoomGridColumns = homeLiveRoomGridColumns"))

        assertEquals(3, resolveAppLayout(AppLayoutMode.Auto, 599, 1200, 599, 1f).liveRoomColumns)
        assertEquals(2, resolveAppLayout(AppLayoutMode.Auto, 600, 1200, 600, 1f).liveRoomColumns)
        assertEquals(3, resolveAppLayout(AppLayoutMode.Auto, 840, 1200, 840, 1f).liveRoomColumns)
        assertEquals(4, resolveAppLayout(AppLayoutMode.Auto, 1200, 1200, 1200, 1f).liveRoomColumns)
        assertTrue(homeSource.contains("homeLiveRoomGridColumns: Int? = null"))
        assertTrue(homeSource.contains("homeLiveRoomGridColumns?.let { GridCells.Fixed(it) }"))
        assertTrue(homeSource.contains("homeLiveRoomGridColumns?.let { it * 3 } ?: 8"))
    }

    @Test
    fun sideNavigationItemsRemainReachableAtShortHeights() {
        val source = readMainSource("com/mylive/app/ui/screen/IndexScreen.kt")
        val railBlock = source.substringAfter("private fun IndexSideNavigationRail(")

        assertTrue(source.contains(".fillMaxHeight()"))
        assertTrue(source.contains(".heightIn(min = 64.dp)"))
        assertTrue(railBlock.contains("Column("))
        assertTrue(railBlock.contains(".fillMaxWidth()"))
        assertTrue(railBlock.contains("IndexNavigationItem("))
        assertTrue(railBlock.contains(".verticalScroll(rememberScrollState())"))
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
