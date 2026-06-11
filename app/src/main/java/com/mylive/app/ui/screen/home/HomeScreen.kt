package com.mylive.app.ui.screen.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val siteTabs by viewModel.siteTabs.collectAsStateWithLifecycle()
    val selectedTab = homeSelectedPlatformIndex(siteTabs = siteTabs, siteId = uiState.siteId)
    val activePlatformAccentColor = homePlatformAccentColor(siteTabs.getOrNull(selectedTab)?.id.orEmpty())
    val titleColor by animateColorAsState(
        targetValue = activePlatformAccentColor ?: MaterialTheme.colorScheme.primary,
        label = "homeTitleColor"
    )

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
            viewModel.refresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Compact Premium Header with Title "MyLive" & Search Pill side-by-side
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MyLive",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = titleColor
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Modern Search Pill Bar (Compact)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { navigator.navigate(Route.Search) }
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "搜索直播间或主播...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        }

        // Platform selector: show all platforms and let the content pager handle swipes.
        val pagerState = rememberPagerState(initialPage = selectedTab, pageCount = { siteTabs.size })
        val coroutineScope = rememberCoroutineScope()
        val platformLayout = remember(siteTabs, selectedTab) {
            homePlatformSelectorLayout(siteTabs = siteTabs, selectedIndex = selectedTab)
        }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                platformLayout.secondarySiteIndices.forEach { siteIndex ->
                    HomePlatformChip(
                        platformId = siteTabs[siteIndex].id,
                        name = homePlatformDisplayName(siteTabs[siteIndex].name),
                        isSelected = platformLayout.primarySiteIndex == siteIndex,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        onClick = {
                            coroutineScope.launch {
                                val targetIndex = siteIndex.coerceIn(0, siteTabs.lastIndex)
                                if (pagerState.currentPage == targetIndex) {
                                    selectHomeSite(targetIndex)
                                    return@launch
                                }
                                homePlatformPreJumpPageForTarget(
                                    currentPage = pagerState.currentPage,
                                    targetPage = targetIndex
                                )?.let { preJumpPage ->
                                    pagerState.scrollToPage(preJumpPage)
                                }
                                val diff = targetIndex - pagerState.currentPage
                                pagerState.animateScrollToPage(
                                    page = targetIndex,
                                    animationSpec = AppMotion.pagerSpec(diff)
                                )
                            }
                        }
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
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
                onRevealBottomBar = onRevealBottomBar,
                onRefresh = {
                    if (page == selectedTab) {
                        viewModel.refresh()
                    }
                },
                onRetry = {
                    if (page == selectedTab) {
                        viewModel.refresh()
                    }
                },
                onLoadMore = {
                    if (page == selectedTab) {
                        viewModel.loadMore()
                    }
                },
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
    onRoomClick: (String) -> Unit
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    var isAtTop by remember { mutableStateOf(true) }

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
                    columns = 2,
                    itemCount = 8,
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
                    columns = GridCells.Adaptive(LiveRoomGridMinCellWidth),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 96.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.rooms, key = { it.roomId }) { room ->
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
                    if (uiState.hasMore && !uiState.isLoading) {
                        item {
                            LaunchedEffect(uiState.currentPage, uiState.rooms.size) {
                                onLoadMore()
                            }
                        }
                    }
                    val loadMoreSkeletonItemCount = homeLoadMoreSkeletonItemCount(uiState)
                    if (loadMoreSkeletonItemCount > 0) {
                        items(
                            count = loadMoreSkeletonItemCount,
                            key = { "home-load-more-skeleton-$it" }
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

@Composable
private fun HomePlatformChip(
    platformId: String,
    name: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selectedContainerColor = MaterialTheme.colorScheme.primary
    val unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    val containerColor by animateColorAsState(
        targetValue = homePlatformChipContainerColor(
            platformId = platformId,
            selectedContainerColor = selectedContainerColor,
            unselectedContainerColor = unselectedContainerColor,
            isSelected = isSelected
        ),
        label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = homePlatformChipContentColor(
            platformId = platformId,
            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            isSelected = isSelected
        ),
        label = "contentColor"
    )

    Box(
        modifier = modifier
            .background(containerColor, shape = RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = if (isSelected) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            )
        )
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

internal fun homePlatformPreJumpPageForTarget(currentPage: Int, targetPage: Int): Int? {
    return AppMotion.preJumpPageForTarget(currentPage = currentPage, targetPage = targetPage)
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
