package com.mylive.app.ui.screen.room.quickaccess

import androidx.compose.material3.SheetValue
import androidx.compose.material3.ExperimentalMaterial3Api
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class QuickAccessPanelLayoutPolicyTest {

    @Test
    fun onlyTheTopHandleMayMoveTheQuickAccessSheetToHidden() {
        assertFalse(
            quickAccessAllowsSheetValueChange(
                target = SheetValue.Hidden,
                handleDismissAllowed = false
            )
        )
        assertTrue(
            quickAccessAllowsSheetValueChange(
                target = SheetValue.Hidden,
                handleDismissAllowed = true
            )
        )
        assertTrue(
            quickAccessAllowsSheetValueChange(
                target = SheetValue.Expanded,
                handleDismissAllowed = false
            )
        )
    }

    @Test
    fun sheetUsesCustomDismissHandleAndIsolatesContentScrollFromSheetDismissal() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/quickaccess/QuickAccessPanel.kt").readText()
        val panelSource = source.substringAfter("fun QuickAccessPanel(")
            .substringBefore("private fun QuickAccessIslandTabBar(")

        assertTrue(panelSource.contains("confirmValueChange = { target ->"))
        assertTrue(panelSource.contains("handleDismissAllowed = false"))
        assertTrue(panelSource.contains("QuickAccessDismissHandle("))
        assertTrue(panelSource.contains(".roomPanelContentScrollIsolation()"))
        val gestures = File("src/main/java/com/mylive/app/ui/screen/room/RoomPanelGestures.kt").readText()
        assertTrue(gestures.contains("awaitEachGesture"))
        assertTrue(gestures.contains("change.consume()"))
        assertFalse(panelSource.contains("translationY = dragOffset"))
        assertFalse(panelSource.contains("Animatable(startOffset)"))
        assertFalse(panelSource.contains("dragHandle = { BottomSheetDefaults.DragHandle() }"))
    }

    @Test
    fun touchingTheHandleArmsNativeSheetDragButAPlainTapDoesNotDismiss() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/quickaccess/QuickAccessPanel.kt").readText()
        val handleSource = source.substringAfter("private fun QuickAccessDismissHandle(")
            .substringBefore("@Composable")

        assertTrue(handleSource.contains(".roomPanelDismissHandleGesture("))
        val gestureSource = File("src/main/java/com/mylive/app/ui/screen/room/RoomPanelGestures.kt").readText()
            .substringAfter("internal fun Modifier.roomPanelDismissHandleGesture(")
        assertTrue(gestureSource.contains("onDownwardDragStarted()"))
        assertFalse(gestureSource.contains("onClick"))
        assertFalse(gestureSource.contains("onDismiss()"))
        assertTrue(handleSource.contains("关闭快速入口"))
    }

    @Test
    fun collapsingTopIconsKeepsTheOverallSheetHeightFixed() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/quickaccess/QuickAccessPanel.kt").readText()
        val panelSource = source.substringAfter("fun QuickAccessPanel(")
            .substringBefore("private fun QuickAccessIslandTabBar(")
        val pagerSource = panelSource.substringAfter("HorizontalPager(")

        assertEquals(588, resolveQuickAccessPanelHeightDp(screenHeightDp = 800))
        assertEquals(456, resolveQuickAccessPanelHeightDp(screenHeightDp = 600))
        assertEquals(220, resolveQuickAccessPanelHeightDp(screenHeightDp = 300))
        assertTrue(panelSource.contains(".height(panelHeight)"))
        assertTrue(pagerSource.contains(".weight(1f)"))
        assertFalse(pagerSource.contains(".heightIn(min = 300.dp, max = 500.dp)"))
    }

    @Test
    fun topTabsUseFixedIslandBarInsteadOfMaterialTabRow() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/quickaccess/QuickAccessPanel.kt").readText()

        assertTrue(source.contains("QuickAccessIslandTabBar("))
        assertTrue(source.contains("RoundedCornerShape(28.dp)"))
        assertTrue(source.contains(".weight(1f)"))
        assertTrue(source.contains("Color.Transparent"))
        assertFalse(source.contains("TabRow("))
        assertFalse(source.contains("ScrollableTabRow("))
        assertFalse(source.contains("edgePadding"))
    }

    @Test
    fun quickAccessPagesCanBeSwipedHorizontally() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/quickaccess/QuickAccessPanel.kt").readText()

        assertTrue(source.contains("rememberPagerState("))
        assertTrue(source.contains("HorizontalPager("))
        assertTrue(source.contains("pagerState.currentPage"))
        assertTrue(source.contains("animateScrollToPage(index)"))
        assertFalse(source.contains("var selectedTab by remember"))
    }

    @Test
    fun quickAccessPanelCanOpenSpecificInitialTab() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/quickaccess/QuickAccessPanel.kt").readText()

        assertTrue(source.contains("initialSelectedKey: String? = null"))
        assertTrue(source.contains("initialSelectedKey ?: viewModel.selectedTabKey.value"))
        assertTrue(source.contains("orderedKeys.indexOf(requestedKey).takeIf { it >= 0 } ?: 0"))
        assertTrue(source.contains("pagerState.scrollToPage(target)"))
    }

    @Test
    fun disabledPanelDismissesFromAnEffectInsteadOfDuringComposition() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/quickaccess/QuickAccessPanel.kt").readText()
        val disabledGuard = source.substringAfter(
            "val enabled by viewModel.quickAccessEnabled.collectAsStateWithLifecycle()"
        ).substringBefore("val extraTabsByKey")

        assertTrue(disabledGuard.contains("LaunchedEffect(enabled)"))
        assertTrue(disabledGuard.contains("if (!enabled) onDismiss()"))
        assertTrue(disabledGuard.contains("if (!enabled) return"))
        assertFalse(disabledGuard.contains("if (!enabled) {\n        onDismiss()"))
    }

    @Test
    fun followQuickPanelPlacesRefreshBesideFilterChips() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/quickaccess/QuickAccessPages.kt").readText()
        val panelSource = source.substringAfter("internal fun FollowQuickPanel(")
            .substringBefore("internal fun HistoryQuickPanel(")

        assertFalse(panelSource.contains("PullToRefreshBox("))
        assertFalse(panelSource.contains("LaunchedEffect(Unit)"))
        assertTrue(panelSource.contains("QuickAccessStatus.entries"))
        assertTrue(panelSource.contains("viewModel.updatingFollowStatus.collectAsStateWithLifecycle()"))
        assertTrue(panelSource.contains("viewModel.updateFollowStatus()"))
        assertTrue(panelSource.contains("\"刷新关注状态\""))
        assertTrue(panelSource.contains("Icons.Default.Refresh"))

        val chipRow = panelSource.substringAfter("QuickAccessStatus.entries")
            .substringBefore("QuickAccessFilterSummary(")
        assertTrue(chipRow.contains("updateFollowStatus()"))
        assertTrue(chipRow.contains("Icons.Default.Refresh"))
    }
}
