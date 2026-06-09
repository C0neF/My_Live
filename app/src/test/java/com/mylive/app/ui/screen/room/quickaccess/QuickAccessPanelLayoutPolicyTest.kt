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
}
