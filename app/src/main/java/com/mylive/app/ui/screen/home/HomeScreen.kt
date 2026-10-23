package com.mylive.app.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.Route
import com.mylive.app.R
import com.mylive.app.ui.component.BackToTopButton
import com.mylive.app.ui.component.LiveRoomGridMinCellWidth
import com.mylive.app.ui.component.LiveRoomCard
import com.mylive.app.ui.component.backToTopButtonMetrics
import com.mylive.app.ui.component.status.EmptyState
import com.mylive.app.ui.component.status.ErrorState
import com.mylive.app.ui.component.status.LiveRoomCardSkeleton
import com.mylive.app.ui.component.status.LiveRoomGridSkeleton
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.screen.backToTopButtonVisible
import com.mylive.app.ui.screen.isScrollableContentAtTop
import com.mylive.app.ui.theme.livePlatformAccentColor
import com.mylive.app.ui.theme.livePlatformOnAccentColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal data class HomePlatformSelectorLayout(
    val primarySiteIndex: Int?,
    val secondarySiteIndices: List<Int>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navigator: Navigator,
    suppressInitialLoadingEffect: Boolean = false,
    refreshSignal: Int = 0,
    onInitialLoadingEffectSettled: () -> Unit = {},
    onPlatformAccentColorChange: (Color?) -> Unit = {},
    onRevealBottomBar: () -> Unit = {},
    contentBottomPadding: Dp = 96.dp,
    homeLiveRoomGridColumns: Int? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val motionPolicy = LocalAppMotionPolicy.current
    val headerScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        snapAnimationSpec = motionPolicy.contentSpec()
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val siteTabs by viewModel.siteTabs.collectAsStateWithLifecycle()
    val selectedTab = homeSelectedPlatformIndex(siteTabs = siteTabs, siteId = uiState.siteId)
    val platformItems = remember(siteTabs) {
        siteTabs.map { HomePlatformItem(it.id, homePlatformDisplayName(it.name)) }
    }
    val activePlatformAccentColor = homePlatformAccentColor(siteTabs.getOrNull(selectedTab)?.id.orEmpty())

    LaunchedEffect(activePlatformAccentColor) {
        onPlatformAccentColorChange(activePlatformAccentColor)
    }

    LaunchedEffect(suppressInitialLoadingEffect, uiState.isLoading, uiState.rooms.size) {
        if (suppressInitialLoadingEffect && (!uiState.isLoading || uiState.rooms.isNotEmpty())) {
            onInitialLoadingEffectSettled()
        }
    }

    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) {
            headerScrollBehavior.state.heightOffset = 0f
            viewModel.refresh()
        }
    }

    // Restore search if loading ends without rooms and the scrollable skeleton disappears.
    LaunchedEffect(uiState.rooms.isEmpty(), uiState.isLoading, uiState.error) {
        if (uiState.rooms.isEmpty()) headerScrollBehavior.state.heightOffset = 0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(headerScrollBehavior.nestedScrollConnection)
    ) {
        HomeSearchHeader(
            scrollState = headerScrollBehavior.state,
            onSearchClick = { navigator.navigate(Route.Search) }
        )

        // Platform selector: show all platforms and let the content pager handle swipes.
        val pagerState = rememberPagerState(initialPage = selectedTab, pageCount = { siteTabs.size })
        val coroutineScope = rememberCoroutineScope()

        fun selectHomeSite(index: Int) {
            if (siteTabs.isEmpty()) return
            val boundedIndex = index.coerceIn(0, siteTabs.lastIndex)
            if (selectedTab == boundedIndex && uiState.siteId == siteTabs[boundedIndex].id) return
            viewModel.selectSite(boundedIndex)
        }

        LaunchedEffect(siteTabs, uiState.siteId) {
            val restoredTab = homeSelectedPlatformIndex(siteTabs = siteTabs, siteId = uiState.siteId)
            if (siteTabs.isNotEmpty() && pagerState.currentPage != restoredTab) {
                pagerState.scrollToPage(restoredTab)
            }
        }

        LaunchedEffect(pagerState, siteTabs.size) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { page ->
                    selectHomeSite(page)
                }
        }

        LaunchedEffect(siteTabs.size) {
            if (siteTabs.isNotEmpty() && pagerState.currentPage > siteTabs.lastIndex) {
                val lastIndex = siteTabs.lastIndex
                pagerState.scrollToPage(lastIndex)
                selectHomeSite(lastIndex)
            }
        }

        if (siteTabs.isNotEmpty()) {
            HomePlatformBar(
                items = platformItems,
                selectedIndex = selectedTab,
                onSelect = { siteIndex ->
                    coroutineScope.launch {
                        val targetIndex = siteIndex.coerceIn(0, siteTabs.lastIndex)
                        if (pagerState.currentPage == targetIndex) {
                            selectHomeSite(targetIndex)
                            return@launch
                        }
                        if (motionPolicy.reducedMotion) {
                            pagerState.scrollToPage(targetIndex)
                        } else {
                            homePlatformPreJumpPageForTarget(
                                currentPage = pagerState.currentPage,
                                targetPage = targetIndex
                            )?.let { preJumpPage ->
                                pagerState.scrollToPage(preJumpPage)
                            }
                            pagerState.animateScrollToPage(
                                page = targetIndex,
                                animationSpec = AppMotion.pagerSpec()
                            )
                        }
                    }
                }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).testTag("home_rooms_pager"),
            userScrollEnabled = siteTabs.size > 1
        ) { page ->
            val pageState = if (page == selectedTab) {
                uiState
            } else {
                siteTabs.getOrNull(page)
                    ?.let { HomeStateCache.get(it.id) }
                    ?: HomeUiState(isLoading = true)
            }
            HomeRoomsPage(
                uiState = pageState,
                suppressInitialLoadingEffect = suppressInitialLoadingEffect && page == selectedTab,
                onRevealBottomBar = {
                    headerScrollBehavior.state.heightOffset = 0f
                    onRevealBottomBar()
                },
                onRefresh = {
                    if (page == selectedTab) {
                        viewModel.refresh()
                    }
                },
                onRetry = {
                    if (page == selectedTab) {
                        viewModel.retry()
                    }
                },
                onLoadMore = {
                    if (page == selectedTab) {
                        viewModel.loadMore()
                    }
                },
                contentBottomPadding = contentBottomPadding,
                homeLiveRoomGridColumns = homeLiveRoomGridColumns,
                onRoomClick = { roomId ->
                    navigator.navigate(
                        homeLiveRoomRoute(
                            siteTabs = siteTabs,
                            selectedTab = page,
                            roomId = roomId
                        )
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRoomsPage(
    uiState: HomeUiState,
    suppressInitialLoadingEffect: Boolean,
    onRevealBottomBar: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    contentBottomPadding: Dp,
    homeLiveRoomGridColumns: Int?,
    onRoomClick: (String) -> Unit
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    var isAtTop by remember { mutableStateOf(true) }
    val liveRoomGridColumns = homeLiveRoomGridColumns?.let { GridCells.Fixed(it) }
        ?: GridCells.Adaptive(LiveRoomGridMinCellWidth)
    val initialSkeletonColumns = homeLiveRoomGridColumns ?: 2
    val initialSkeletonItemCount = homeLiveRoomGridColumns?.let { it * 3 } ?: 8

    LaunchedEffect(uiState.rooms.isEmpty()) {
        if (uiState.rooms.isEmpty()) {
            isAtTop = true
        }
    }

    LaunchedEffect(gridState, uiState.rooms.isNotEmpty()) {
        if (uiState.rooms.isNotEmpty()) {
            snapshotFlow {
                isScrollableContentAtTop(
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset
                )
            }
                .distinctUntilChanged()
                .collect {
                    isAtTop = it
                }
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            shouldShowHomeInitialLoading(uiState, suppressInitialLoadingEffect) -> {
                LiveRoomGridSkeleton(
                    columns = initialSkeletonColumns,
                    itemCount = initialSkeletonItemCount,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.error != null && uiState.rooms.isEmpty() -> {
                ErrorState(
                    message = uiState.error ?: "",
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.rooms.isEmpty() && !uiState.isLoading && !uiState.isRefreshing -> {
                EmptyState(
                    message = stringResource(R.string.home_no_recommendations),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = liveRoomGridColumns,
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = contentBottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.rooms,
                        key = { it.roomId },
                        contentType = { "home_room" }
                    ) { room ->
                        LiveRoomCard(
                            title = room.title,
                            userName = room.userName,
                            faceUrl = room.faceUrl,
                            online = room.online,
                            coverUrl = room.cover,
                            siteName = null,
                            onClick = { onRoomClick(room.roomId) }
                        )
                    }
                    if (uiState.hasMore && !uiState.isLoading && uiState.error == null) {
                        item(contentType = "home_load_more_trigger") {
                            LaunchedEffect(uiState.currentPage, uiState.rooms.size) {
                                onLoadMore()
                            }
                        }
                    }
                    if (uiState.error != null && !uiState.isLoading) {
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "home_load_more_error"
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = uiState.error.orEmpty(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(onClick = onRetry) { Text("重试") }
                            }
                        }
                    }
                    val loadMoreSkeletonItemCount = homeLoadMoreSkeletonItemCount(uiState)
                    if (loadMoreSkeletonItemCount > 0) {
                        items(
                            count = loadMoreSkeletonItemCount,
                            key = { "home-load-more-skeleton-$it" },
                            contentType = { "home_load_more_skeleton" }
                        ) {
                            LiveRoomCardSkeleton()
                        }
                    }
                }
            }
        }

        if (homeBackToTopButtonVisible(isAtTop = isAtTop, hasRooms = uiState.rooms.isNotEmpty())) {
            val metrics = backToTopButtonMetrics()
            BackToTopButton(
                onClick = {
                    onRevealBottomBar()
                    coroutineScope.launch {
                        gridState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = metrics.endPaddingDp.dp, bottom = metrics.bottomPaddingDp.dp)
            )
        }
    }
}

internal fun shouldShowHomeInitialLoading(
    uiState: HomeUiState,
    suppressInitialLoadingEffect: Boolean
): Boolean {
    return uiState.isLoading && uiState.rooms.isEmpty() && !suppressInitialLoadingEffect
}

internal fun homeLoadMoreSkeletonItemCount(uiState: HomeUiState): Int {
    return if (uiState.isLoading && uiState.rooms.isNotEmpty()) 4 else 0
}

internal fun homeBackToTopButtonVisible(isAtTop: Boolean, hasRooms: Boolean): Boolean {
    return backToTopButtonVisible(isAtTop = isAtTop, hasItems = hasRooms)
}

internal fun homePlatformSelectorLayout(
    siteTabs: List<com.mylive.app.core.site.LiveSite>,
    selectedIndex: Int
): HomePlatformSelectorLayout {
    if (siteTabs.isEmpty()) {
        return HomePlatformSelectorLayout(primarySiteIndex = null, secondarySiteIndices = emptyList())
    }
    val primaryIndex = selectedIndex.coerceIn(0, siteTabs.lastIndex)
    return HomePlatformSelectorLayout(
        primarySiteIndex = primaryIndex,
        secondarySiteIndices = siteTabs.indices.toList()
    )
}

internal fun homePlatformPreJumpPageForTarget(currentPage: Int, targetPage: Int): Int? {
    return AppMotion.preJumpPageForTarget(currentPage = currentPage, targetPage = targetPage)
}

internal fun homeSelectedPlatformIndex(
    siteTabs: List<com.mylive.app.core.site.LiveSite>,
    siteId: String
): Int {
    if (siteTabs.isEmpty()) {
        return 0
    }
    val selectedIndex = siteTabs.indexOfFirst { it.id == siteId }
    return selectedIndex.takeIf { it >= 0 } ?: 0
}

internal fun homePlatformDisplayName(siteName: String): String {
    if (siteName == "哔哩哔哩直播") {
        return "哔哩哔哩"
    }
    return siteName
}

internal fun homePlatformAccentColor(platformId: String): Color? {
    return livePlatformAccentColor(platformId)
}

internal fun homePlatformChipContainerColor(
    platformId: String,
    selectedContainerColor: Color,
    unselectedContainerColor: Color,
    isSelected: Boolean
): Color {
    if (!isSelected) {
        return unselectedContainerColor
    }
    return homePlatformAccentColor(platformId) ?: selectedContainerColor
}

internal fun homePlatformChipContentColor(
    platformId: String,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    isSelected: Boolean
): Color {
    if (!isSelected) {
        return unselectedContentColor
    }
    return livePlatformOnAccentColor(platformId, selectedContentColor)
}

internal fun homeLiveRoomRoute(
    siteTabs: List<com.mylive.app.core.site.LiveSite>,
    selectedTab: Int,
    roomId: String
): Route.LiveRoomDetail {
    return Route.LiveRoomDetail(
        roomId = roomId,
        siteId = siteTabs.getOrNull(selectedTab)?.id.orEmpty()
    )
}
