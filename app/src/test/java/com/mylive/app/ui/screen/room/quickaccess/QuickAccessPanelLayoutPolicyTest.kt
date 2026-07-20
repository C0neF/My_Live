package com.mylive.app.ui.screen.room.quickaccess

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuickAccessPanelLayoutPolicyTest {

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
        assertTrue(source.contains("val initialPage = remember(orderedKeys, initialSelectedKey)"))
        assertTrue(source.contains("orderedKeys.indexOf(initialSelectedKey).takeIf { it >= 0 } ?: 0"))
        assertTrue(source.contains("pagerState.scrollToPage(initialPage)"))
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
        val source = File("src/main/java/com/mylive/app/ui/screen/room/quickaccess/QuickAccessPanel.kt").readText()
        val panelSource = source.substringAfter("private fun FollowQuickPanel(")
            .substringBefore("// ─── History Quick Panel")

        assertFalse(panelSource.contains("PullToRefreshBox("))
        assertFalse(panelSource.contains("LaunchedEffect(Unit)"))
        assertTrue(panelSource.contains("listOf(\"全部\", \"直播中\", \"未开播\")"))
        assertTrue(panelSource.contains("viewModel.updatingFollowStatus.collectAsStateWithLifecycle()"))
        assertTrue(panelSource.contains("viewModel.updateFollowStatus()"))
        assertTrue(panelSource.contains("contentDescription = \"刷新关注状态\""))
        assertTrue(panelSource.contains("Icons.Default.Refresh"))

        val chipRow = panelSource.substringAfter("listOf(\"全部\", \"直播中\", \"未开播\")")
            .substringBefore("if (filteredFollows.isEmpty())")
        assertTrue(chipRow.contains("updateFollowStatus()"))
        assertTrue(chipRow.contains("Icons.Default.Refresh"))
    }
}
