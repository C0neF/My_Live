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
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.local.entity.playbackRoomId
import com.mylive.app.ui.component.NetImage
import com.mylive.app.ui.component.FollowRefreshProgressLine
import com.mylive.app.ui.component.followRefreshHint
import com.mylive.app.ui.theme.Icons
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun FollowQuickPanel(
    viewModel: QuickAccessViewModel,
    library: QuickAccessLibrary,
    isCurrent: (String, String) -> Boolean,
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit,
    compact: Boolean = false,
    searching: Boolean = false,
    onSearchingChange: (Boolean) -> Unit = {}
) {
    val filters by viewModel.followFilters.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.updatingFollowStatus.collectAsStateWithLifecycle()
    val refreshProgress by viewModel.followRefreshProgress.collectAsStateWithLifecycle()
    val refreshEntries by viewModel.followRefreshEntries.collectAsStateWithLifecycle()
    val globalSearch by viewModel.globalSearch.collectAsStateWithLifecycle()
    val filteredFollows = remember(library.follows, library.tags, library.remarks, filters) {
        filterQuickAccessFollows(library.follows, library.tags, library.remarks, filters)
    }
    val followRows = remember(filteredFollows) { filteredFollows.chunked(2) }
    val listState = rememberQuickAccessListState(viewModel, "follow")
    ResetQuickAccessScrollOnFilterChange(listState, filters)

    @Composable
    fun FollowItemCard(user: FollowUserEntity) {
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
            refreshHint = followRefreshHint(refreshEntries[user.id]),
            current = isCurrent(user.siteId, user.playbackRoomId) || isCurrent(user.siteId, user.roomId),
            onClick = { onNavigateToRoom(user.siteId, user.playbackRoomId, true) },
            compact = compact
        )
    }

    if (searching) {
        QuickAccessGlobalSearchPanel(
            tag = "follow",
            filters = filters,
            onFiltersChange = viewModel::setFollowFilters,
            availableSites = viewModel.availableSiteIds,
            library = library,
            localFollows = filteredFollows,
            globalSearch = globalSearch,
            isCurrent = isCurrent,
            onNavigateToRoom = onNavigateToRoom,
            onBack = { onSearchingChange(false) },
            compact = compact,
            followCard = { FollowItemCard(it) }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickAccessSearchButton(
                query = filters.query,
                tag = "follow",
                onClick = { onSearchingChange(true) }
            )
            QuickAccessFilterButton(
                filters = filters,
                onFiltersChange = viewModel::setFollowFilters,
                sites = (viewModel.availableSiteIds + library.follows.map { it.siteId }).distinct(),
                tags = library.tags,
                tag = "follow"
            )
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickAccessStatus.entries.forEach { status ->
                    QuickAccessStatusChip(
                        status = status,
                        selected = filters.status == status,
                        onClick = { viewModel.setFollowFilters(filters.copy(status = status)) }
                    )
                }
            }
            IconButton(
                onClick = { viewModel.updateFollowStatus() },
                enabled = !isRefreshing,
                modifier = Modifier.size(28.dp).testTag("quick_access_refresh_follow")
            ) {
                if (isRefreshing) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "刷新关注状态", Modifier.size(16.dp))
            }
            QuickAccessFilterSummary(
                filters = filters,
                tags = library.tags,
                onReset = { viewModel.setFollowFilters(QuickAccessFilters()) }
            )
        }
        FollowRefreshProgressLine(refreshProgress)
        when {
            library.loading -> QuickAccessLoadingRows()
            filteredFollows.isEmpty() -> QuickAccessEmpty(Icons.Default.FavoriteBorder,
                if (library.follows.isEmpty()) "还没有关注主播" else "没有符合条件的主播",
                if (library.follows.isEmpty()) "关注主播后，可以在这里快速切换直播间" else "试试点击左上角搜索全平台主播，或清除筛选条件",
                actionLabel = if (filters != QuickAccessFilters()) "清除筛选" else null,
                onAction = { viewModel.setFollowFilters(QuickAccessFilters()) })
            else -> LazyColumn(Modifier.fillMaxSize().testTag("quick_access_follow_list"), state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (compact) {
                    items(followRows, key = { it.first().id }, contentType = { "follow_row" }) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { user ->
                                Box(Modifier.weight(1f)) { FollowItemCard(user) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    items(filteredFollows, key = { it.id }, contentType = { "follow" }) { user ->
                        FollowItemCard(user)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryQuickPanel(
    viewModel: QuickAccessViewModel,
    library: QuickAccessLibrary,
    isCurrent: (String, String) -> Boolean,
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit,
    compact: Boolean = false,
    searching: Boolean = false,
    onSearchingChange: (Boolean) -> Unit = {}
) {
    val filters by viewModel.historyFilters.collectAsStateWithLifecycle()
    val globalSearch by viewModel.globalSearch.collectAsStateWithLifecycle()
    val history = remember(library.history, library.remarks, filters) { filterQuickAccessHistory(library.history, library.remarks, filters) }
    val groups = remember(history) { history.groupBy { quickAccessHistoryDay(it.updateTime) } }
    val followed = remember(library.follows) {
        library.follows.flatMap { listOf(it.siteId to it.roomId, it.siteId to it.playbackRoomId) }.toHashSet()
    }
    var deleteItem by remember { mutableStateOf<HistoryEntity?>(null) }
    val listState = rememberQuickAccessListState(viewModel, "history")
    ResetQuickAccessScrollOnFilterChange(listState, filters)

    @Composable
    fun HistoryItemCard(item: HistoryEntity) {
        val remark = quickAccessRemark(library.remarks, item.siteId, item.userName)
        QuickAccessRoomRow(
            name = remark ?: item.userName, face = item.face,
            subtitle = "${quickAccessSiteName(item.siteId)} · ${formatQuickAccessRelativeTime(item.updateTime)}",
            current = isCurrent(item.siteId, item.roomId),
            onClick = { onNavigateToRoom(item.siteId, item.roomId, (item.siteId to item.roomId) in followed) },
            onLongClick = { deleteItem = item }, onDelete = { deleteItem = item },
            compact = compact
        )
    }

    if (searching) {
        val matchedFollows = remember(library.follows, library.tags, library.remarks, filters) {
            filterQuickAccessFollows(library.follows, library.tags, library.remarks, filters)
        }
        QuickAccessGlobalSearchPanel(
            tag = "history",
            filters = filters,
            onFiltersChange = viewModel::setHistoryFilters,
            availableSites = viewModel.availableSiteIds,
            library = library,
            localFollows = matchedFollows,
            localHistory = history,
            globalSearch = globalSearch,
            isCurrent = isCurrent,
            onNavigateToRoom = onNavigateToRoom,
            onBack = { onSearchingChange(false) },
            compact = compact,
            followCard = { user ->
                val remark = quickAccessRemark(library.remarks, user.siteId, user.userName)
                QuickAccessRoomRow(
                    name = remark ?: user.userName, face = user.face,
                    subtitle = "${quickAccessSiteName(user.siteId)} · 房间 ${user.roomId}",
                    status = when (user.liveStatus) { 1 -> "直播中"; 2 -> "未开播"; else -> "待更新" },
                    live = user.liveStatus == 1, special = user.isSpecialFollow,
                    current = isCurrent(user.siteId, user.playbackRoomId) || isCurrent(user.siteId, user.roomId),
                    onClick = { onNavigateToRoom(user.siteId, user.playbackRoomId, true) },
                    compact = compact
                )
            },
            historyCard = { HistoryItemCard(it) }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickAccessSearchButton(
                query = filters.query,
                tag = "history",
                onClick = { onSearchingChange(true) }
            )
            QuickAccessFilterButton(
                filters = filters,
                onFiltersChange = viewModel::setHistoryFilters,
                sites = (viewModel.availableSiteIds + library.history.map { it.siteId }).distinct(),
                tag = "history"
            )
            Spacer(Modifier.weight(1f))
            QuickAccessFilterSummary(
                filters = filters,
                onReset = { viewModel.setHistoryFilters(QuickAccessFilters()) }
            )
        }
        when {
            library.loading -> QuickAccessLoadingRows()
            history.isEmpty() -> QuickAccessEmpty(Icons.Default.History,
                if (library.history.isEmpty()) "还没有观看记录" else "没有找到相关记录",
                if (library.history.isEmpty()) "看过的直播间会按时间保存在这里" else "试试主播名称、备注或房间号",
                if (filters != QuickAccessFilters()) "清除筛选" else null,
                onAction = { viewModel.setHistoryFilters(QuickAccessFilters()) })
            else -> LazyColumn(Modifier.fillMaxSize().testTag("quick_access_history_list"), state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                groups.forEach { (day, entries) ->
                    stickyHeader(key = "day_$day") {
                        Text(day, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (compact) {
                        val rows = entries.chunked(2)
                        items(rows, key = { it.first().id }, contentType = { "history_row" }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { item ->
                                    Box(Modifier.weight(1f)) { HistoryItemCard(item) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(entries, key = { it.id }, contentType = { "history" }) { item ->
                            HistoryItemCard(item)
                        }
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
private fun QuickAccessGlobalSearchPanel(
    tag: String,
    filters: QuickAccessFilters,
    onFiltersChange: (QuickAccessFilters) -> Unit,
    availableSites: List<String>,
    library: QuickAccessLibrary,
    localFollows: List<FollowUserEntity>,
    localHistory: List<HistoryEntity> = emptyList(),
    globalSearch: QuickAccessGlobalSearchState,
    isCurrent: (String, String) -> Boolean,
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit,
    onBack: () -> Unit,
    compact: Boolean,
    followCard: @Composable (FollowUserEntity) -> Unit,
    historyCard: (@Composable (HistoryEntity) -> Unit)? = null
) {
    val allSites = remember(availableSites, library.follows, library.history) {
        (availableSites + library.follows.map { it.siteId } + library.history.map { it.siteId }).distinct()
    }
    val followedSet = remember(library.follows) {
        library.follows.flatMap { listOf(it.siteId to it.roomId, it.siteId to it.playbackRoomId) }.toHashSet()
    }
    val remoteAnchors = remember(globalSearch.anchors, followedSet) {
        globalSearch.anchors.filterNot { (it.siteId to it.anchor.roomId) in followedSet }
    }
    val remoteRooms = remember(globalSearch.rooms, remoteAnchors, followedSet) {
        val anchorKeys = remoteAnchors.mapTo(hashSetOf()) { it.siteId to it.anchor.roomId }
        globalSearch.rooms.filterNot { (it.siteId to it.room.roomId) in followedSet || (it.siteId to it.room.roomId) in anchorKeys }
    }

    Column(Modifier.fillMaxSize()) {
        QuickAccessSearch(
            filters = filters,
            onFiltersChange = onFiltersChange,
            sites = allSites,
            tags = library.tags,
            tag = tag,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            compact = compact,
            onBack = onBack,
            autoFocus = true,
            trailingContent = {
                QuickAccessFilterSummary(
                    filters = filters,
                    tags = library.tags,
                    onReset = { onFiltersChange(QuickAccessFilters()) }
                )
            }
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickAccessPlatformChip(
                label = "全部平台",
                selected = filters.siteId == null,
                onClick = { onFiltersChange(filters.copy(siteId = null)) }
            )
            allSites.forEach { siteId ->
                QuickAccessPlatformChip(
                    label = quickAccessSiteName(siteId),
                    selected = filters.siteId == siteId,
                    onClick = { onFiltersChange(filters.copy(siteId = siteId)) }
                )
            }
        }
        val hasQuery = filters.query.isNotBlank()
        val isEmpty = localFollows.isEmpty() && localHistory.isEmpty() && remoteAnchors.isEmpty() && remoteRooms.isEmpty()
        when {
            isEmpty && globalSearch.loading -> QuickAccessLoadingRows()
            isEmpty -> QuickAccessEmpty(
                Icons.Default.Search,
                if (hasQuery) "未找到相关主播或直播间" else "搜索全平台主播与直播间",
                if (hasQuery) "试试切换平台或输入其他关键词" else "支持搜索未关注主播、全网平台房间号及已关注备注",
                actionLabel = if (filters != QuickAccessFilters()) "重置筛选" else null,
                onAction = { onFiltersChange(QuickAccessFilters()) }
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().testTag("quick_access_global_search_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (localFollows.isNotEmpty()) {
                    if (hasQuery) {
                        item(key = "section_local_follow") {
                            Text(
                                "已关注主播 (${localFollows.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (compact) {
                        val rows = localFollows.chunked(2)
                        items(rows, key = { "follow_${it.first().id}" }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { user -> Box(Modifier.weight(1f)) { followCard(user) } }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(localFollows, key = { "follow_${it.id}" }) { user -> followCard(user) }
                    }
                }
                if (historyCard != null && localHistory.isNotEmpty()) {
                    item(key = "section_local_history") {
                        Text(
                            "观看记录 (${localHistory.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    if (compact) {
                        val rows = localHistory.chunked(2)
                        items(rows, key = { "history_${it.first().id}" }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { item -> Box(Modifier.weight(1f)) { historyCard(item) } }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(localHistory, key = { "history_${it.id}" }) { item -> historyCard(item) }
                    }
                }
                if (hasQuery && (remoteAnchors.isNotEmpty() || remoteRooms.isNotEmpty() || globalSearch.loading)) {
                    item(key = "section_global_remote") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "全平台搜索结果",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (globalSearch.loading) {
                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                            }
                        }
                    }
                    if (compact) {
                        val anchorRows = remoteAnchors.chunked(2)
                        items(anchorRows, key = { "remote_anchor_${it.first().siteId}_${it.first().anchor.roomId}" }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { hit ->
                                    Box(Modifier.weight(1f)) {
                                        QuickAccessRoomRow(
                                            name = hit.anchor.userName,
                                            face = hit.anchor.avatar,
                                            subtitle = "${quickAccessSiteName(hit.siteId)} · 房间 ${hit.anchor.roomId}",
                                            status = if (hit.anchor.liveStatus) "直播中" else "未开播",
                                            live = hit.anchor.liveStatus,
                                            current = isCurrent(hit.siteId, hit.anchor.roomId),
                                            onClick = { onNavigateToRoom(hit.siteId, hit.anchor.roomId, false) },
                                            compact = true
                                        )
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        val roomRows = remoteRooms.chunked(2)
                        items(roomRows, key = { "remote_room_${it.first().siteId}_${it.first().room.roomId}" }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { hit ->
                                    Box(Modifier.weight(1f)) {
                                        QuickAccessRoomRow(
                                            name = hit.room.userName.ifBlank { hit.room.title },
                                            face = hit.room.faceUrl.ifBlank { hit.room.cover },
                                            subtitle = "${quickAccessSiteName(hit.siteId)} · ${hit.room.title.ifBlank { "房间 ${hit.room.roomId}" }}",
                                            status = if (hit.room.online > 0) quickAccessPopularity(hit.room.online) else "直播中",
                                            live = true,
                                            current = isCurrent(hit.siteId, hit.room.roomId),
                                            onClick = { onNavigateToRoom(hit.siteId, hit.room.roomId, false) },
                                            compact = true
                                        )
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(remoteAnchors, key = { "remote_anchor_${it.siteId}_${it.anchor.roomId}" }) { hit ->
                            QuickAccessRoomRow(
                                name = hit.anchor.userName,
                                face = hit.anchor.avatar,
                                subtitle = "${quickAccessSiteName(hit.siteId)} · 房间 ${hit.anchor.roomId}",
                                status = if (hit.anchor.liveStatus) "直播中" else "未开播",
                                live = hit.anchor.liveStatus,
                                current = isCurrent(hit.siteId, hit.anchor.roomId),
                                onClick = { onNavigateToRoom(hit.siteId, hit.anchor.roomId, false) },
                                compact = false
                            )
                        }
                        items(remoteRooms, key = { "remote_room_${it.siteId}_${it.room.roomId}" }) { hit ->
                            QuickAccessRoomRow(
                                name = hit.room.userName.ifBlank { hit.room.title },
                                face = hit.room.faceUrl.ifBlank { hit.room.cover },
                                subtitle = "${quickAccessSiteName(hit.siteId)} · ${hit.room.title.ifBlank { "房间 ${hit.room.roomId}" }}",
                                status = if (hit.room.online > 0) quickAccessPopularity(hit.room.online) else "直播中",
                                live = true,
                                current = isCurrent(hit.siteId, hit.room.roomId),
                                onClick = { onNavigateToRoom(hit.siteId, hit.room.roomId, false) },
                                compact = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecommendationQuickPanel(
    viewModel: QuickAccessViewModel,
    library: QuickAccessLibrary,
    currentSiteId: String,
    currentRoomId: String,
    resolvedRoomId: String?,
    active: Boolean,
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit,
    compact: Boolean = false
) {
    val state by viewModel.recommendations.collectAsStateWithLifecycle()
    val rooms = remember(state.rooms, currentSiteId, currentRoomId, resolvedRoomId) {
        state.rooms.filterNot { isCurrentQuickAccessRoom(currentSiteId, it.roomId, currentSiteId, currentRoomId, resolvedRoomId) }
    }
    val roomRows = remember(rooms) { rooms.chunked(2) }
    val listState = rememberQuickAccessListState(viewModel, "recommendation")
    val followed = remember(library.follows) { library.follows.mapTo(hashSetOf()) { it.siteId to it.roomId } }
    LaunchedEffect(active, rooms.size, state.loading, state.hasMore, state.error) {
        if (!active || state.loading || !state.hasMore || state.error != null || rooms.isEmpty()) return@LaunchedEffect
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= (if (compact) (rooms.size + 1) / 2 - 2 else rooms.lastIndex - 2) }
            .distinctUntilChanged().collect { nearEnd -> if (nearEnd) viewModel.loadMoreRecommendations() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.source?.categoryId != null) "发现同类直播" else "${quickAccessSiteName(currentSiteId)}热门直播",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("为你隐藏当前直播间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = viewModel::refreshRecommendations, enabled = !state.loading,
                modifier = Modifier.size(if (compact) 30.dp else 36.dp).testTag("quick_access_refresh_recommendation")) {
                if (state.loading && rooms.isNotEmpty()) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "刷新推荐", Modifier.size(18.dp))
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (compact) {
                    items(roomRows, key = { it.first().roomId }, contentType = { "recommendation_row" }) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { room ->
                                Box(Modifier.weight(1f)) {
                                    RecommendationQuickItem(room, compact = true) {
                                        onNavigateToRoom(currentSiteId, room.roomId, (currentSiteId to room.roomId) in followed)
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    items(rooms, key = { it.roomId }, contentType = { "recommendation" }) { room ->
                        RecommendationQuickItem(room, compact = false) {
                            onNavigateToRoom(currentSiteId, room.roomId, (currentSiteId to room.roomId) in followed)
                        }
                    }
                }
                item(key = "recommendation_footer") {
                    Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
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
private fun RecommendationQuickItem(room: LiveRoomItem, compact: Boolean = false, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)) {
        Row(Modifier.padding(if (compact) 6.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (compact) 76.dp else 92.dp).height(if (compact) 46.dp else 54.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                NetImage(room.cover, modifier = Modifier.fillMaxSize(), size = if (compact) 76.dp else 92.dp)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(room.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(room.userName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (room.online > 0) Text(quickAccessPopularity(room.online), style = MaterialTheme.typography.labelSmall,
                        color = quickAccessAccentColor(), maxLines = 1)
                }
            }
        }
    }
}
