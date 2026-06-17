package com.mylive.app.ui.screen.search

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SearchScreenPolicyTest {

    @Test
    fun searchScreenSeparatesTopBarAndSearchFieldWithScaffoldPadding() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchScreen.kt").readText()

        assertTrue(source.contains("Scaffold("))
        assertTrue(source.contains("topBar = {"))
        assertTrue(source.contains("TopAppBar("))
        assertTrue(source.contains(".padding(innerPadding)"))
        assertFalse(source.contains("// Compact Premium Header"))
    }

    @Test
    fun searchFilterBarUsesTypeDropdownAndNonScrollablePlatformRow() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchScreen.kt").readText()
        val filterSource = source.substringAfter("// Compact search filter row")
            .substringBefore("AnimatedContent(")

        assertTrue(source.contains("val selectedPlatformId = viewModel.siteTabs.getOrNull(selectedTab)?.id"))
        assertTrue(filterSource.contains("CompactSearchTypeMenu("))
        assertTrue(filterSource.contains("platformId = selectedPlatformId"))
        assertTrue(filterSource.contains("viewModel.siteTabs.forEachIndexed"))
        assertTrue(filterSource.contains("modifier = Modifier.weight(1f)"))
        assertTrue(filterSource.contains("platformId = site.id"))
        assertFalse(filterSource.contains("LazyRow"))
        assertFalse(filterSource.contains("horizontalScroll"))
    }

    @Test
    fun searchTopBarKeepsDefaultTitleColorWhileTypeDropdownUsesPlatformColor() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchScreen.kt").readText()
        val topBarSource = source.substringAfter("topBar = {")
            .substringBefore("containerColor = MaterialTheme.colorScheme.background")
        val typeMenuSource = source.substringAfter("private fun CompactSearchTypeMenu(")
            .substringBefore("@Composable\nprivate fun CompactSearchFilterPill(")

        assertFalse(topBarSource.contains("titleColor"))
        assertFalse(source.contains("searchTitleColor"))
        assertTrue(typeMenuSource.contains("platformId: String? = null"))
        assertTrue(typeMenuSource.contains("platformId = platformId"))
    }

    @Test
    fun searchPlatformAccentUsesSharedPlatformColors() {
        assertEquals(Color(0xFFFF5D23), searchPlatformAccentColor("douyu"))
        assertEquals(Color(0xFFFFD736), searchPlatformAccentColor("huya"))
        assertEquals(Color(0xFFF07775), searchPlatformAccentColor("bilibili"))
        assertEquals(null, searchPlatformAccentColor("douyin"))
    }

    @Test
    fun searchPlatformChipUsesBrandBackgroundOnlyWhenSelected() {
        val selectedFallback = Color.Blue
        val unselectedFallback = Color.Gray

        assertEquals(
            Color(0xFFFF5D23),
            searchPlatformChipContainerColor("douyu", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            Color(0xFFFFD736),
            searchPlatformChipContainerColor("huya", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            Color(0xFFF07775),
            searchPlatformChipContainerColor("bilibili", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            selectedFallback,
            searchPlatformChipContainerColor("douyin", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            unselectedFallback,
            searchPlatformChipContainerColor("douyu", selectedFallback, unselectedFallback, isSelected = false)
        )
    }

    @Test
    fun searchPlatformChipUsesReadableContentColorOnBrandBackgrounds() {
        val selectedFallback = Color.White
        val unselectedFallback = Color.Gray

        assertEquals(
            Color.Black,
            searchPlatformChipContentColor("douyu", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            Color.Black,
            searchPlatformChipContentColor("huya", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            Color.Black,
            searchPlatformChipContentColor("bilibili", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            selectedFallback,
            searchPlatformChipContentColor("douyin", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            unselectedFallback,
            searchPlatformChipContentColor("huya", selectedFallback, unselectedFallback, isSelected = false)
        )
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

    @Test
    fun searchResultsTriggerLoadMoreAtListEnd() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchScreen.kt").readText()

        assertTrue(source.contains("viewModel.loadMore()"))
        assertTrue(source.contains("GridItemSpan(maxLineSpan)"))
        assertTrue(source.contains("LaunchedEffect(uiState.currentPage, uiState.rooms.size)"))
        assertTrue(source.contains("LaunchedEffect(uiState.currentPage, uiState.anchors.size)"))
    }

    @Test
    fun searchScreenUsesViewModelSelectedSiteAsSingleSourceOfTruth() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchScreen.kt").readText()

        assertTrue(source.contains("val selectedTab = uiState.selectedSiteIndex"))
        assertFalse(source.contains("var selectedTab by remember"))
    }

    @Test
    fun searchInitialLoadingUsesSearchSpecificSkeletonAlignedWithResultsGrid() {
        val source = File("src/main/java/com/mylive/app/ui/screen/search/SearchScreen.kt").readText()
        val loadingSource = source.substringAfter(
            "uiState.isLoading && uiState.rooms.isEmpty() && uiState.anchors.isEmpty() -> {"
        ).substringBefore("uiState.error != null")

        assertTrue(loadingSource.contains("if (animatedSearchType == 1)"))
        assertTrue(loadingSource.contains("SearchAnchorListSkeleton("))
        assertTrue(loadingSource.contains("SearchRoomGridSkeleton("))
        assertTrue(loadingSource.contains("minCellWidth = LiveRoomGridMinCellWidth"))
        assertTrue(loadingSource.contains("modifier = Modifier.fillMaxSize()"))
        assertFalse(loadingSource.contains("LiveRoomGridSkeleton("))
        assertFalse(loadingSource.contains("Alignment.Center"))
    }

    @Test
    fun searchSkeletonCardMirrorsRoomResultMetadataShape() {
        val source = File("src/main/java/com/mylive/app/ui/component/status/SkeletonScreen.kt").readText()

        assertTrue(source.contains("fun SearchRoomCardSkeleton("))
        assertTrue(source.contains("fun SearchRoomGridSkeleton("))

        val cardSource = source.substringAfter("fun SearchRoomCardSkeleton(")
            .substringBefore("fun SearchRoomGridSkeleton(")

        assertTrue(cardSource.contains("aspectRatio(1.777f)"))
        assertTrue(cardSource.contains("width(58.dp)"))
        assertTrue(cardSource.contains("SkeletonCircle(size = 36)"))
        assertTrue(cardSource.contains("SkeletonLine(height = 14)"))
        assertTrue(cardSource.contains("SkeletonLine(widthFraction = 0.6f, height = 12)"))
        assertFalse(cardSource.contains("align(Alignment.TopStart)"))
        assertFalse(cardSource.contains("MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)"))
        assertFalse(cardSource.contains("width(44.dp)"))
    }

    @Test
    fun searchAnchorSkeletonMirrorsAnchorResultListShape() {
        val source = File("src/main/java/com/mylive/app/ui/component/status/SkeletonScreen.kt").readText()

        assertTrue(source.contains("fun SearchAnchorItemSkeleton("))
        assertTrue(source.contains("fun SearchAnchorListSkeleton("))

        val itemSource = source.substringAfter("fun SearchAnchorItemSkeleton(")
            .substringBefore("fun SearchAnchorListSkeleton(")
        val listSource = source.substringAfter("fun SearchAnchorListSkeleton(")

        assertTrue(listSource.contains("LazyColumn("))
        assertTrue(listSource.contains("SearchAnchorItemSkeleton()"))
        assertTrue(itemSource.contains("SkeletonCircle(size = 48)"))
        assertTrue(itemSource.contains("SkeletonLine(widthFraction = 0.45f, height = 16)"))
        assertTrue(itemSource.contains("SkeletonLine(widthFraction = 0.28f, height = 12)"))
        assertFalse(itemSource.contains("aspectRatio(1.777f)"))
        assertFalse(itemSource.contains("SearchRoomCardSkeleton("))
        assertFalse(itemSource.contains("MaterialTheme.colorScheme.error.copy(alpha = 0.18f)"))
        assertFalse(itemSource.contains("align(Alignment.BottomEnd)"))
    }
}
