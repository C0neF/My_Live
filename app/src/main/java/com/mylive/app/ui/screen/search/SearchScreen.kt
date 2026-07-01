package com.mylive.app.ui.screen.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.R
import com.mylive.app.ui.component.LiveRoomCard
import com.mylive.app.ui.component.LiveRoomGridMinCellWidth
import com.mylive.app.ui.component.NetImage
import com.mylive.app.ui.component.status.EmptyState
import com.mylive.app.ui.component.status.ErrorState
import com.mylive.app.ui.component.status.SearchAnchorListSkeleton
import com.mylive.app.ui.component.status.SearchRoomGridSkeleton
import com.mylive.app.core.site.LiveSite
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.motion.horizontalContentTransform
import com.mylive.app.ui.navigation.navigateToRoom
import com.mylive.app.ui.theme.livePlatformAccentColor
import com.mylive.app.ui.theme.livePlatformOnAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navigator: Navigator,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val siteTabs by viewModel.siteTabs.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    val selectedTab = uiState.selectedSiteIndex
    val selectedPlatformId = siteTabs.getOrNull(selectedTab)?.id
    var showSearchTypeMenu by remember { mutableStateOf(false) }

    var isExiting by remember { mutableStateOf(false) }
    val handleBack: () -> Unit = {
        if (!isExiting) {
            isExiting = true
            navigator.goBack()
        }
    }
    BackHandler(enabled = !isExiting) {
        handleBack()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        val useTabletTwoPane = searchUseTabletTwoPane(maxWidth.value.toInt())
        if (useTabletTwoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                SearchTabletControlPane(
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    selectedTab = selectedTab,
                    selectedPlatformId = selectedPlatformId,
                    searchType = uiState.searchType,
                    showSearchTypeMenu = showSearchTypeMenu,
                    onSearchTypeMenuChange = { showSearchTypeMenu = it },
                    siteTabs = siteTabs,
                    onBack = handleBack,
                    onClearSearch = {
                        searchText = ""
                        viewModel.clearSearch()
                    },
                    onSearch = { keyword -> viewModel.search(keyword) },
                    onTypeSelected = viewModel::setSearchType,
                    onSiteSelected = viewModel::selectSite,
                    modifier = Modifier.width(searchTabletControlPaneWidthDp().dp)
                )

                SearchResultsContent(
                    uiState = uiState,
                    selectedTab = selectedTab,
                    siteTabs = siteTabs,
                    searchText = searchText,
                    navigator = navigator,
                    onSearch = { keyword -> viewModel.search(keyword) },
                    onLoadMore = { viewModel.loadMore() },
                    onSiteSelected = viewModel::selectSite,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchHeader(onBack = handleBack)
                SearchInputField(
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    onClearSearch = {
                        searchText = ""
                        viewModel.clearSearch()
                    },
                    onSearch = { keyword -> viewModel.search(keyword) }
                )
                SearchFilterRow(
                    selectedTab = selectedTab,
                    selectedPlatformId = selectedPlatformId,
                    searchType = uiState.searchType,
                    showSearchTypeMenu = showSearchTypeMenu,
                    onSearchTypeMenuChange = { showSearchTypeMenu = it },
                    siteTabs = siteTabs,
                    onTypeSelected = viewModel::setSearchType,
                    onSiteSelected = viewModel::selectSite
                )
                SearchResultsContent(
                    uiState = uiState,
                    selectedTab = selectedTab,
                    siteTabs = siteTabs,
                    searchText = searchText,
                    navigator = navigator,
                    onSearch = { keyword -> viewModel.search(keyword) },
                    onLoadMore = { viewModel.loadMore() },
                    onSiteSelected = viewModel::selectSite,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SearchTabletControlPane(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    selectedTab: Int,
    selectedPlatformId: String?,
    searchType: Int,
    showSearchTypeMenu: Boolean,
    onSearchTypeMenuChange: (Boolean) -> Unit,
    siteTabs: List<LiveSite>,
    onBack: () -> Unit,
    onClearSearch: () -> Unit,
    onSearch: (String) -> Unit,
    onTypeSelected: (Int) -> Unit,
    onSiteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            SearchHeader(onBack = onBack)
            SearchInputField(
                searchText = searchText,
                onSearchTextChange = onSearchTextChange,
                onClearSearch = onClearSearch,
                onSearch = onSearch
            )
            SearchFilterRow(
                selectedTab = selectedTab,
                selectedPlatformId = selectedPlatformId,
                searchType = searchType,
                showSearchTypeMenu = showSearchTypeMenu,
                onSearchTypeMenuChange = onSearchTypeMenuChange,
                siteTabs = siteTabs,
                onTypeSelected = onTypeSelected,
                onSiteSelected = onSiteSelected
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.search_hint),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchHeader(
    onBack: () -> Unit
) {
    // Compact Premium Header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back)
            )
        }
        Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SearchInputField(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearch: (String) -> Unit
) {
    // Premium Pill Search bar
    TextField(
        value = searchText,
        onValueChange = onSearchTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(52.dp),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        singleLine = true,
        shape = RoundedCornerShape(26.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.cd_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        trailingIcon = {
            if (searchText.isNotEmpty()) {
                IconButton(onClick = onClearSearch) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                val keyword = searchText.trim()
                if (keyword.isNotEmpty()) {
                    onSearch(keyword)
                }
            }
        )
    )
}

@Composable
private fun SearchFilterRow(
    selectedTab: Int,
    selectedPlatformId: String?,
    searchType: Int,
    showSearchTypeMenu: Boolean,
    onSearchTypeMenuChange: (Boolean) -> Unit,
    siteTabs: List<LiveSite>,
    onTypeSelected: (Int) -> Unit,
    onSiteSelected: (Int) -> Unit
) {
    // Compact search filter row
    if (siteTabs.isNotEmpty()) {
        val compactFilterMetrics = searchCompactPlatformFilterMetrics()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = compactFilterMetrics.horizontalPaddingDp.dp,
                    vertical = 2.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(compactFilterMetrics.itemGapDp.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactSearchTypeMenu(
                modifier = Modifier.width(searchCompactModeSelectorWidthDp().dp),
                platformId = selectedPlatformId,
                searchType = searchType,
                expanded = showSearchTypeMenu,
                onExpandedChange = onSearchTypeMenuChange,
                onTypeSelected = onTypeSelected,
                metrics = compactFilterMetrics
            )

            siteTabs.forEachIndexed { index, site ->
                CompactSearchFilterPill(
                    modifier = Modifier.weight(1f),
                    platformId = site.id,
                    text = searchPlatformDisplayName(site.name),
                    selected = selectedTab == index,
                    onClick = { onSiteSelected(index) },
                    metrics = compactFilterMetrics
                )
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    uiState: SearchUiState,
    selectedTab: Int,
    siteTabs: List<LiveSite>,
    searchText: String,
    navigator: Navigator,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSiteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = selectedTab to uiState.searchType,
        modifier = modifier
            .pointerInput(selectedTab, siteTabs.size) {
                var dragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount
                    },
                    onDragEnd = {
                        val targetIndex = searchPlatformSwipeTargetIndex(
                            currentIndex = selectedTab,
                            siteCount = siteTabs.size,
                            dragX = dragX
                        )
                        if (targetIndex != selectedTab) {
                            onSiteSelected(targetIndex)
                        }
                    },
                    onDragCancel = { dragX = 0f }
                )
            },
        transitionSpec = {
            val direction = if (initialState.first != targetState.first) {
                AppMotion.indexDirection(initialState.first, targetState.first)
            } else {
                AppMotion.indexDirection(initialState.second, targetState.second)
            }
            horizontalContentTransform(direction)
        },
        label = "searchResultsContent"
    ) { contentState ->
        val animatedTab = contentState.first
        val animatedSearchType = contentState.second

        // Results
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.rooms.isEmpty() && uiState.anchors.isEmpty() -> {
                    if (animatedSearchType == 1) {
                        SearchAnchorListSkeleton(
                            itemCount = 8,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        SearchRoomGridSkeleton(
                            minCellWidth = LiveRoomGridMinCellWidth,
                            itemCount = 6,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error ?: "",
                        onRetry = { onSearch(searchText) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                animatedSearchType == 0 && uiState.rooms.isEmpty() -> {
                    EmptyState(
                        message = stringResource(R.string.search_no_results),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                animatedSearchType == 1 && uiState.anchors.isEmpty() -> {
                    EmptyState(
                        message = stringResource(R.string.search_no_results),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                animatedSearchType == 0 -> {
                    val currentSiteName = siteTabs.getOrNull(animatedTab)?.name
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(LiveRoomGridMinCellWidth),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.rooms,
                            key = { it.roomId },
                            contentType = { "search_room" }
                        ) { room ->
                            LiveRoomCard(
                                title = room.title,
                                userName = room.userName,
                                faceUrl = room.faceUrl,
                                online = room.online,
                                coverUrl = room.cover,
                                siteName = currentSiteName,
                                onClick = {
                                    val siteId = siteTabs.getOrNull(animatedTab)?.id ?: ""
                                    navigator.navigateToRoom(siteId = siteId, roomId = room.roomId)
                                }
                            )
                        }
                        if (uiState.hasMore && !uiState.isLoading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LaunchedEffect(uiState.currentPage, uiState.rooms.size) {
                                    onLoadMore()
                                }
                            }
                        }
                        if (uiState.isLoading && uiState.rooms.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
                animatedSearchType == 1 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.anchors,
                            key = { it.roomId },
                            contentType = { "search_anchor" }
                        ) { anchor ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val siteId = siteTabs.getOrNull(animatedTab)?.id ?: ""
                                        navigator.navigateToRoom(siteId = siteId, roomId = anchor.roomId)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(48.dp)) {
                                        NetImage(
                                            url = anchor.avatar,
                                            contentDescription = anchor.userName,
                                            size = 48.dp,
                                            isCircle = true
                                        )
                                        if (anchor.liveStatus) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(12.dp),
                                                shape = androidx.compose.foundation.shape.CircleShape,
                                                color = MaterialTheme.colorScheme.error,
                                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)
                                            ) {}
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = anchor.userName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (anchor.liveStatus) stringResource(R.string.follow_group_live)
                                                   else stringResource(R.string.follow_group_offline),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (anchor.liveStatus) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontWeight = if (anchor.liveStatus) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }

                                    if (anchor.liveStatus) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Go to Live",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (uiState.hasMore && !uiState.isLoading) {
                            item {
                                LaunchedEffect(uiState.currentPage, uiState.anchors.size) {
                                    onLoadMore()
                                }
                            }
                        }
                        if (uiState.isLoading && uiState.anchors.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
        }
    }
}
}

@Composable
private fun CompactSearchTypeMenu(
    modifier: Modifier = Modifier,
    platformId: String? = null,
    searchType: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTypeSelected: (Int) -> Unit,
    metrics: SearchCompactPlatformFilterMetrics
) {
    val roomLabel = stringResource(R.string.search_type_room)
    val anchorLabel = stringResource(R.string.search_type_anchor)
    val selectedLabel = if (searchType == 0) roomLabel else anchorLabel

    Box(modifier = modifier) {
        CompactSearchFilterPill(
            modifier = Modifier.fillMaxWidth(),
            platformId = platformId,
            text = selectedLabel,
            selected = true,
            onClick = { onExpandedChange(true) },
            showDropdownIcon = true,
            metrics = metrics
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            listOf(0 to roomLabel, 1 to anchorLabel).forEach { (type, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onExpandedChange(false)
                        onTypeSelected(type)
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactSearchFilterPill(
    modifier: Modifier = Modifier,
    platformId: String? = null,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    showDropdownIcon: Boolean = false,
    metrics: SearchCompactPlatformFilterMetrics
) {
    val selectedContainerColor = MaterialTheme.colorScheme.primary
    val unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val selectedContentColor = MaterialTheme.colorScheme.onPrimary
    val unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor by animateColorAsState(
        targetValue = searchPlatformChipContainerColor(
            platformId = platformId,
            selectedContainerColor = selectedContainerColor,
            unselectedContainerColor = unselectedContainerColor,
            isSelected = selected
        ),
        label = "searchFilterContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = searchPlatformChipContentColor(
            platformId = platformId,
            selectedContentColor = selectedContentColor,
            unselectedContentColor = unselectedContentColor,
            isSelected = selected
        ),
        label = "searchFilterContentColor"
    )

    Surface(
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.pillHorizontalPaddingDp.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = if (showDropdownIcon) Modifier.weight(1f, fill = false) else Modifier.fillMaxWidth(),
                text = text,
                color = contentColor,
                fontSize = metrics.fontSizeSp.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (showDropdownIcon) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(metrics.iconSizeDp.dp)
                )
            }
        }
    }
}

internal data class SearchCompactPlatformFilterMetrics(
    val horizontalPaddingDp: Int,
    val itemGapDp: Int,
    val pillHorizontalPaddingDp: Int,
    val fontSizeSp: Int,
    val iconSizeDp: Int
)

internal fun searchCompactPlatformFilterMetrics(): SearchCompactPlatformFilterMetrics {
    return SearchCompactPlatformFilterMetrics(
        horizontalPaddingDp = 16,
        itemGapDp = 6,
        pillHorizontalPaddingDp = 4,
        fontSizeSp = 12,
        iconSizeDp = 14
    )
}

internal fun searchCompactModeSelectorWidthDp(): Int {
    return 72
}

internal fun searchUseTabletTwoPane(screenWidthDp: Int): Boolean {
    return screenWidthDp >= 840
}

internal fun searchTabletControlPaneWidthDp(): Int {
    return 360
}

internal fun searchPlatformDisplayName(siteName: String): String {
    return if (siteName == "哔哩哔哩直播") {
        "哔哩哔哩"
    } else {
        siteName
    }
}

internal fun searchPlatformAccentColor(platformId: String): Color? {
    return livePlatformAccentColor(platformId)
}

internal fun searchPlatformChipContainerColor(
    platformId: String?,
    selectedContainerColor: Color,
    unselectedContainerColor: Color,
    isSelected: Boolean
): Color {
    if (!isSelected) {
        return unselectedContainerColor
    }
    return platformId?.let { searchPlatformAccentColor(it) } ?: selectedContainerColor
}

internal fun searchPlatformChipContentColor(
    platformId: String?,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    isSelected: Boolean
): Color {
    if (!isSelected) {
        return unselectedContentColor
    }
    return platformId?.let { livePlatformOnAccentColor(it, selectedContentColor) } ?: selectedContentColor
}

internal fun searchPlatformSwipeTargetIndex(
    currentIndex: Int,
    siteCount: Int,
    dragX: Float,
    thresholdPx: Float = 72f
): Int {
    if (siteCount <= 0) return 0
    val clampedIndex = currentIndex.coerceIn(0, siteCount - 1)
    return when {
        dragX <= -thresholdPx -> (clampedIndex + 1).coerceAtMost(siteCount - 1)
        dragX >= thresholdPx -> (clampedIndex - 1).coerceAtLeast(0)
        else -> clampedIndex
    }
}
