package com.mylive.app.ui.screen.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SearchScreenPolicyTest {

    @Test
    fun searchScreenUsesCompactHeaderLikeFollowScreen() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchScreen.kt").readText()

        assertTrue(source.contains("// Compact Premium Header"))
        assertFalse(source.contains("Scaffold("))
        assertFalse(source.contains("TopAppBar("))
    }

    @Test
    fun searchFilterBarUsesTypeDropdownAndNonScrollablePlatformRow() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchScreen.kt").readText()
        val filterSource = source.substringAfter("// Compact search filter row")
            .substringBefore("AnimatedContent(")

        assertTrue(filterSource.contains("CompactSearchTypeMenu("))
        assertTrue(filterSource.contains("viewModel.siteTabs.forEachIndexed"))
        assertTrue(filterSource.contains("modifier = Modifier.weight(1f)"))
        assertFalse(filterSource.contains("LazyRow"))
        assertFalse(filterSource.contains("horizontalScroll"))
    }

    @Test
    fun searchPlatformDisplayNameRemovesLiveSuffixOnlyForBilibili() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchScreen.kt").readText()

        assertTrue(source.contains("internal fun searchPlatformDisplayName(siteName: String): String"))
        assertTrue(source.contains("siteName == \"哔哩哔哩直播\""))
        assertTrue(source.contains("\"哔哩哔哩\""))
    }

    @Test
    fun searchPlatformSwipeSwitchesAdjacentPlatformWithoutWrapping() {
        assertTrue(searchPlatformSwipeTargetIndex(currentIndex = 1, siteCount = 4, dragX = -96f) == 2)
        assertTrue(searchPlatformSwipeTargetIndex(currentIndex = 1, siteCount = 4, dragX = 96f) == 0)
        assertTrue(searchPlatformSwipeTargetIndex(currentIndex = 0, siteCount = 4, dragX = 96f) == 0)
        assertTrue(searchPlatformSwipeTargetIndex(currentIndex = 3, siteCount = 4, dragX = -96f) == 3)
        assertTrue(searchPlatformSwipeTargetIndex(currentIndex = 1, siteCount = 4, dragX = -24f) == 1)
    }

    @Test
    fun searchResultsContentRegistersHorizontalSwipeGesture() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchScreen.kt").readText()

        assertTrue(source.contains(".pointerInput(selectedTab, viewModel.siteTabs.size)"))
        assertTrue(source.contains("detectHorizontalDragGestures("))
        assertTrue(source.contains("searchPlatformSwipeTargetIndex("))
    }
}
