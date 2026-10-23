package com.mylive.app.ui.screen.room.quickaccess

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.local.entity.playbackRoomId
import com.mylive.app.ui.component.NetImage
import com.mylive.app.ui.component.FollowRefreshProgressLine
import com.mylive.app.ui.component.followRefreshHint
import com.mylive.app.ui.component.rememberFollowRefreshEntry
import com.mylive.app.ui.theme.Icons
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun FollowQuickPanel(viewModel: QuickAccessViewModel, library: QuickAccessLibrary,
    isCurrent: (String, String) -> Boolean,
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit) {
    val filters by viewModel.followFilters.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.updatingFollowStatus.collectAsStateWithLifecycle()
    val refreshProgress by viewModel.followRefreshProgress.collectAsStateWithLifecycle()
    val filteredFollows = remember(library.follows, library.tags, library.remarks, filters) {
        filterQuickAccessFollows(library.follows, library.tags, library.remarks, filters)
    }
    val listState = rememberQuickAccessListState(viewModel, "follow")
    ResetQuickAccessScrollOnFilterChange(listState, filters)
    Column(Modifier.fillMaxSize()) {
        QuickAccessSearch(filters, viewModel::setFollowFilters, library.follows.map { it.siteId }, library.tags, "follow")
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAccessStatus.entries.forEach { status ->
                    FilterChip(selected = filters.status == status,
                        onClick = { viewModel.setFollowFilters(filters.copy(status = status)) },
                        label = { Text(status.label) }, modifier = Modifier.testTag("quick_access_status_${status.name}"),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = quickAccessAccentColor().copy(alpha = 0.14f),
                            selectedLabelColor = quickAccessAccentColor()))
                }
            }
            IconButton(onClick = { viewModel.updateFollowStatus() }, enabled = !isRefreshing,
                modifier = Modifier.testTag("quick_access_refresh_follow")) {
                if (isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "刷新关注状态", Modifier.size(20.dp))
            }
        }
        QuickAccessFilterSummary("${filteredFollows.size} 位主播", filters, library.tags) { viewModel.setFollowFilters(QuickAccessFilters()) }
        FollowRefreshProgressLine(refreshProgress)
        when {
            library.loading -> QuickAccessLoadingRows()
            filteredFollows.isEmpty() -> QuickAccessEmpty(Icons.Default.FavoriteBorder,
                if (library.follows.isEmpty()) "还没有关注主播" else "没有符合条件的主播",
                if (library.follows.isEmpty()) "关注主播后，可以在这里快速切换直播间" else "试试其他关键词，或清除筛选条件",
                actionLabel = if (filters != QuickAccessFilters()) "清除筛选" else null,
                onAction = { viewModel.setFollowFilters(QuickAccessFilters()) })
            else -> LazyColumn(Modifier.fillMaxSize().testTag("quick_access_follow_list"), state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filteredFollows, key = { it.id }, contentType = { "follow" }) { user ->
                    val remark = quickAccessRemark(library.remarks, user.siteId, user.userName)
                    QuickAccessRoomRow(
                        name = remark ?: user.userName, face = user.face,
                        subtitle = when {
                            remark != null -> "${quickAccessSiteName(user.siteId)} · ${user.userName}"
                            user.siteId == "douyin" -> quickAccessSiteName(user.siteId)
                            else -> "${quickAccessSiteName(user.siteId)} · 房间 ${user.roomId}"
                        },
                        status = when (user.liveStatus) { 1 -> "直播中"; 2 -> "未开播"; else -> "待更新" },
                        live = user.liveStatus == 1, special = user.isSpecialFollow,
                        refreshHint = followRefreshHint(rememberFollowRefreshEntry(viewModel.followRefreshEntries, user.id)),
                        current = isCurrent(user.siteId, user.playbackRoomId) || isCurrent(user.siteId, user.roomId),
                        onClick = { onNavigateToRoom(user.siteId, user.playbackRoomId, true) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryQuickPanel(viewModel: QuickAccessViewModel, library: QuickAccessLibrary,
    isCurrent: (String, String) -> Boolean,
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit) {
    val filters by viewModel.historyFilters.collectAsStateWithLifecycle()
    val history = remember(library.history, library.remarks, filters) { filterQuickAccessHistory(library.history, library.remarks, filters) }
    val groups = remember(history) { history.groupBy { quickAccessHistoryDay(it.updateTime) } }
    val followed = remember(library.follows) {
        library.follows.flatMap { listOf(it.siteId to it.roomId, it.siteId to it.playbackRoomId) }.toHashSet()
    }
    var deleteItem by remember { mutableStateOf<HistoryEntity?>(null) }
    val listState = rememberQuickAccessListState(viewModel, "history")
    ResetQuickAccessScrollOnFilterChange(listState, filters)
    Column(Modifier.fillMaxSize()) {
        QuickAccessSearch(filters, viewModel::setHistoryFilters, library.history.map { it.siteId }, tag = "history")
        QuickAccessFilterSummary("${history.size} 条记录", filters) { viewModel.setHistoryFilters(QuickAccessFilters()) }
        when {
            library.loading -> QuickAccessLoadingRows()
            history.isEmpty() -> QuickAccessEmpty(Icons.Default.History,
                if (library.history.isEmpty()) "还没有观看记录" else "没有找到相关记录",
                if (library.history.isEmpty()) "看过的直播间会按时间保存在这里" else "试试主播名称、备注或房间号",
                if (filters != QuickAccessFilters()) "清除筛选" else null,
                onAction = { viewModel.setHistoryFilters(QuickAccessFilters()) })
            else -> LazyColumn(Modifier.fillMaxSize().testTag("quick_access_history_list"), state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { (day, entries) ->
                    stickyHeader(key = "day_$day") {
                        Text(day, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(entries, key = { it.id }, contentType = { "history" }) { item ->
                        val remark = quickAccessRemark(library.remarks, item.siteId, item.userName)
                        QuickAccessRoomRow(name = remark ?: item.userName, face = item.face,
                            subtitle = "${quickAccessSiteName(item.siteId)} · ${formatQuickAccessRelativeTime(item.updateTime)}",
                            current = isCurrent(item.siteId, item.roomId),
                            onClick = { onNavigateToRoom(item.siteId, item.roomId, (item.siteId to item.roomId) in followed) },
                            onLongClick = { deleteItem = item }, onDelete = { deleteItem = item })
                    }
                }
            }
        }
    }
    deleteItem?.let { item ->
        AlertDialog(onDismissRequest = { deleteItem = null }, title = { Text("删除观看记录") },
            text = { Text("删除「${item.userName}」的观看记录？不会影响关注。") },
            confirmButton = { TextButton(onClick = { viewModel.removeHistory(item.id); deleteItem = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("取消") } })
    }
}

@Composable
internal fun RecommendationQuickPanel(viewModel: QuickAccessViewModel, library: QuickAccessLibrary,
    currentSiteId: String, currentRoomId: String, resolvedRoomId: String?, active: Boolean,
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit) {
    val state by viewModel.recommendations.collectAsStateWithLifecycle()
    val rooms = remember(state.rooms, currentSiteId, currentRoomId, resolvedRoomId) {
        state.rooms.filterNot { isCurrentQuickAccessRoom(currentSiteId, it.roomId, currentSiteId, currentRoomId, resolvedRoomId) }
    }
    val listState = rememberQuickAccessListState(viewModel, "recommendation")
    val followed = remember(library.follows) { library.follows.mapTo(hashSetOf()) { it.siteId to it.roomId } }
    LaunchedEffect(active, rooms.size, state.loading, state.hasMore, state.error) {
        if (!active || state.loading || !state.hasMore || state.error != null || rooms.isEmpty()) return@LaunchedEffect
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= rooms.lastIndex - 2 }
            .distinctUntilChanged().collect { nearEnd -> if (nearEnd) viewModel.loadMoreRecommendations() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (state.source?.categoryId != null) "发现同类直播" else "${quickAccessSiteName(currentSiteId)}热门直播",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("为你隐藏当前直播间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = viewModel::refreshRecommendations, enabled = !state.loading,
                modifier = Modifier.testTag("quick_access_refresh_recommendation")) {
                if (state.loading && rooms.isNotEmpty()) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "刷新推荐", Modifier.size(20.dp))
            }
        }
        when {
            state.loading && rooms.isEmpty() -> QuickAccessLoadingRows()
            state.error != null && rooms.isEmpty() -> QuickAccessEmpty(Icons.Default.ErrorOutline, "推荐暂时未加载",
                state.error.orEmpty(), "重试", onAction = viewModel::retryRecommendations)
            rooms.isEmpty() -> QuickAccessEmpty(Icons.Default.Category, "暂时没有其他直播间", "可以刷新推荐，或从关注和历史中选择",
                if (state.hasMore && state.hasLoaded) "继续查找" else "刷新推荐",
                onAction = if (state.hasMore && state.hasLoaded) viewModel::loadMoreRecommendations else viewModel::refreshRecommendations)
            else -> LazyColumn(Modifier.fillMaxSize().testTag("quick_access_recommendation_list"), state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rooms, key = { it.roomId }, contentType = { "recommendation" }) { room ->
                    RecommendationQuickItem(room) {
                        onNavigateToRoom(currentSiteId, room.roomId, (currentSiteId to room.roomId) in followed)
                    }
                }
                item(key = "recommendation_footer") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            state.error != null -> TextButton(onClick = viewModel::retryRecommendations) { Text("加载失败，点击重试") }
                            state.hasMore -> TextButton(onClick = viewModel::loadMoreRecommendations) { Text("加载更多") }
                            else -> Text("已展示全部推荐", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationQuickItem(room: LiveRoomItem, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(104.dp).height(64.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                NetImage(room.cover, modifier = Modifier.fillMaxSize(), size = 104.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(room.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(room.userName, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (room.online > 0) Text(quickAccessPopularity(room.online), style = MaterialTheme.typography.labelSmall,
                    color = quickAccessAccentColor())
            }
        }
    }
}
