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
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.R
import com.mylive.app.ui.component.LiveRoomCard
import com.mylive.app.ui.component.LiveRoomGridMinCellWidth
import com.mylive.app.ui.component.NetImage
import com.mylive.app.ui.component.status.EmptyState
import com.mylive.app.ui.component.status.ErrorState
import com.mylive.app.ui.component.status.SearchAnchorListSkeleton
import com.mylive.app.ui.component.status.SearchRoomGridSkeleton
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
    val uiState by viewModel.uiState.collectAsState()
    val siteTabs by viewModel.siteTabs.collectAsState()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.search_title))
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
        ) {
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
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
                        IconButton(onClick = {
                            searchText = ""
                            viewModel.clearSearch()
                        }) {
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
                        if (searchText.trim().isNotEmpty()) {
                            viewModel.search(searchText.trim())
                        }
                    }
                )
            )

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
                        searchType = uiState.searchType,
                        expanded = showSearchTypeMenu,
                        onExpandedChange = { showSearchTypeMenu = it },
                        onTypeSelected = viewModel::setSearchType,
                        metrics = compactFilterMetrics
                    )

                    siteTabs.forEachIndexed { index, site ->
                        CompactSearchFilterPill(
                            modifier = Modifier.weight(1f),
                            platformId = site.id,
                            text = searchPlatformDisplayName(site.name),
                            selected = selectedTab == index,
                            onClick = {
                                viewModel.selectSite(index)
                            },
                            metrics = compactFilterMetrics
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = selectedTab to uiState.searchType,
                modifier = Modifier
                    .weight(1f)
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
                                    viewModel.selectSite(targetIndex)
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
                                onRetry = { viewModel.search(searchText) },
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
                                items(uiState.rooms) { room ->
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
                                            viewModel.loadMore()
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
                                items(uiState.anchors) { anchor ->
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
                                        shape = RoundedCornerShape(16.dp)
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
                                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)
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
                                            viewModel.loadMore()
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
    val selectedContainerColor = if (platformId.isNullOrBlank()) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val selectedContentColor = if (platformId.isNullOrBlank()) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
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
        color = containerColor,
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        }
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
