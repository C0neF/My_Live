package com.mylive.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.site.LiveSite
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import java.util.Locale

@Composable
fun ColumnScope.BrowseContent(sites: List<LiveSite>, controller: DesktopBrowseController,
    searchPage: Boolean = false, onDirectRoom: () -> Unit = {}, onRoom: (RoomAddress) -> Unit) {
    val state by controller.state.collectAsState()
    var query by rememberSaveable(state.siteId) { mutableStateOf(state.query) }
    var searchAnchors by rememberSaveable { mutableStateOf(state.mode == BrowseMode.Anchors) }
    var appliedQuery by rememberSaveable(state.siteId) { mutableStateOf(state.query) }
    var categoryGroups by rememberSaveable { mutableStateOf(mapOf<String, Int>()) }
    LaunchedEffect(state.query) { if (state.query != appliedQuery) { query = state.query; appliedQuery = state.query } }
    fun search() { if (query.isBlank()) controller.prepareSearch(state.siteId) else controller.search(state.siteId, query, searchAnchors) }
    val sourceKey = "${state.siteId}:${state.mode}:${state.query}:${state.category?.parentId}:${state.category?.id}"
    val grid = rememberDesktopGridState(sourceKey)
    LaunchedEffect(grid, sourceKey) {
        snapshotFlow {
            val layout = grid.layoutInfo
            state.page > 0 && state.hasMore && !state.loading && state.error == null && state.mode != BrowseMode.Categories &&
                layout.totalItemsCount > 0 && (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 6
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) controller.more() }
    }
    if (!searchPage && state.mode == BrowseMode.Recommend) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("发现直播", style = MaterialTheme.typography.headlineSmall)
                Text("汇聚热门直播，按平台自由切换", style = MaterialTheme.typography.bodyMedium, color = UiMuted, modifier = Modifier.padding(top = 4.dp))
            }
            if (!state.loading && state.rooms.isNotEmpty()) Text(state.rooms.size.toString() + " 场直播", style = MaterialTheme.typography.labelMedium, color = UiMuted)
        }
    }
    Surface(shape = RoundedCornerShape(11.dp), color = UiSurface, border = BorderStroke(1.dp, UiHairline)) {
        Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sites.forEach { site -> DesktopTabButton(title = site.name.removeSuffix("直播"), selected = state.siteId == site.id, modifier = Modifier.weight(1f), onClick = {
                if (searchPage) {
                    if (query.isBlank()) controller.prepareSearch(site.id) else controller.search(site.id, query, searchAnchors)
                } else if (state.mode in listOf(BrowseMode.Categories, BrowseMode.CategoryRooms)) controller.categories(site.id) else controller.recommend(site.id)
            }) }
            if (!searchPage) DesktopIconButton(Icons.Default.Refresh, "刷新列表 F5", enabled = !state.loading, onClick = controller::refresh)
        }
    }
    if (searchPage) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("搜索", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            DesktopOutlinedButton(onDirectRoom, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp)) {
                Icon(Icons.Default.Link, null, Modifier.size(17.dp))
                Text("房间号 / 链接", Modifier.padding(start = 6.dp))
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopChoiceChip(!searchAnchors, onClick = { searchAnchors = false; if (query.isNotBlank()) search() }, modifier = Modifier.height(36.dp), label = { Text("直播间") })
            DesktopChoiceChip(searchAnchors, onClick = { searchAnchors = true; if (query.isNotBlank()) search() }, modifier = Modifier.height(36.dp), label = { Text("主播") })
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(if (searchAnchors) "搜索主播" else "搜索直播间") },
                leadingIcon = { DesktopSearchIcon() },
                trailingIcon = { if (query.isNotEmpty()) DesktopIconButton(Icons.Default.Close, "清除搜索") {
                    query = ""; appliedQuery = ""; controller.prepareSearch(state.siteId)
                } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f).testTag("browse-search"), shape = RoundedCornerShape(10.dp))
            DesktopTonalButton(onClick = { search() }, enabled = query.isNotBlank(), modifier = Modifier.height(44.dp).testTag("submit-search")) {
                Icon(Icons.Default.Search, null, Modifier.size(17.dp)); Text("搜索", Modifier.padding(start = 6.dp))
            }
        }
        if (state.query.isNotBlank()) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("“" + state.query + "” 的搜索结果", color = UiMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            DesktopIconButton(Icons.Default.Refresh, "刷新", enabled = !state.loading, onClick = controller::refresh)
        }
    } else if (state.mode in listOf(BrowseMode.Categories, BrowseMode.CategoryRooms)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.mode == BrowseMode.CategoryRooms) DesktopIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回分类") { controller.categories(state.siteId) }
            Text(if (state.mode == BrowseMode.Categories) "直播分类" else state.category?.name.orEmpty(),
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            DesktopIconButton(Icons.Default.Refresh, "刷新", enabled = !state.loading, onClick = controller::refresh)
        }
    } else Spacer(Modifier.height(18.dp))
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 10.dp))
    state.error?.let { error -> DesktopErrorBanner(error, onRetry = controller::retry) }
    if (searchPage && state.query.isBlank() && !state.loading) {
        DesktopEmptyState("查找感兴趣的直播", "选择平台，输入直播间或主播关键词。也可以通过房间号或链接直接进入。",
            Icons.Default.Search, Modifier.weight(1f).fillMaxHeight())
    } else if (state.mode == BrowseMode.Categories) {
        val selectedGroup = categoryGroups[state.siteId] ?: 0
        val group = selectedGroup.coerceIn(0, state.categories.lastIndex.coerceAtLeast(0))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val groupsList = rememberLazyListState()
            Box(Modifier.width(144.dp).fillMaxHeight()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = groupsList, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.categories.indices.toList()) { index ->
                    DesktopChoiceChip(group == index, onClick = { categoryGroups = categoryGroups + (state.siteId to index) }, label = { Text(state.categories[index].name, maxLines = 2) }, modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp))
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(groupsList), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
            val children = state.categories.getOrNull(group)?.children.orEmpty().distinctBy { it.id }
            if (!state.loading && children.isEmpty()) DesktopEmptyState("暂无分类", "可以切换平台，或直接输入直播间链接。", Icons.Default.GridView, Modifier.weight(1f).fillMaxHeight())
            else DesktopRoomGrid(rememberDesktopGridState("${state.siteId}:categories:$group"), Modifier.weight(1f).fillMaxHeight(), minCardWidth = 140.dp) {
                items(children, key = { it.id }) { category ->
                    val categoryInteraction = remember(category.id) { MutableInteractionSource() }
                    val categoryHovered = categoryInteraction.collectIsHoveredAsState()
                    val categoryFocused = categoryInteraction.collectIsFocusedAsState()
                    CompositionLocalProvider(LocalContentColor provides UiInk) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .desktopCardSurface(
                                    shape = BrowseCardShape,
                                    hoveredState = categoryHovered,
                                    focusedState = categoryFocused,
                                    containerColor = UiSurface,
                                    baseBorderColor = UiHairline,
                                    highlightBorderColor = UiAccent.copy(alpha = .55f)
                                )
                                .clickable(
                                    interactionSource = categoryInteraction,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { controller.category(state.siteId, category) }
                                )
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!category.pic.isNullOrBlank()) AsyncImage(category.pic, null, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Category, null, Modifier.size(40.dp).padding(6.dp), tint = UiAccent)
                            Text(category.name, modifier = Modifier.padding(top = 10.dp), maxLines = 2, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    } else if (!state.loading && state.rooms.isEmpty() && state.anchors.isEmpty()) {
        DesktopEmptyState(if (state.error != null) "暂时没有可显示的直播" else "没有找到直播间", "可以切换平台，或调整搜索关键词。",
            Icons.Default.SearchOff, Modifier.weight(1f).fillMaxHeight(), if (state.query.isNotBlank()) "清除搜索" else null,
            if (state.query.isNotBlank()) ({ query = ""; appliedQuery = ""; controller.prepareSearch(state.siteId) }) else null)
    } else DesktopRoomGrid(grid, Modifier.weight(1f).fillMaxWidth()) {
        if (state.loading && state.rooms.isEmpty() && state.anchors.isEmpty()) items(8) { LoadingRoomCard() }
        if (state.mode == BrowseMode.Anchors) items(state.anchors, key = { it.roomId }) { anchor ->
            BrowseRoomCard(LiveRoomItem(anchor.roomId, anchor.userName, anchor.avatar, "房间 ${anchor.roomId}"), live = anchor.liveStatus, avatar = true,
                onClick = { onRoom(RoomAddress(state.siteId, anchor.roomId)) })
        } else items(state.rooms, key = { it.roomId }) { room -> BrowseRoomCard(room) { onRoom(RoomAddress(state.siteId, room.roomId)) } }
        if (state.hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                DesktopOutlinedButton(onClick = controller::more, enabled = !state.loading, modifier = Modifier.widthIn(min = 160.dp)) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(15.dp).padding(end = 6.dp), strokeWidth = 2.dp)
                    Text(if (state.loading) "正在加载" else "加载更多")
                }
            }
        }
    }
}

private val BrowseCardShape = RoundedCornerShape(12.dp)
private val BrowseBadgeShape = RoundedCornerShape(5.dp)

@Composable
private fun BrowseRoomCard(room: LiveRoomItem, live: Boolean = true, avatar: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val focusedState = interaction.collectIsFocusedAsState()
    CompositionLocalProvider(LocalContentColor provides UiInk) {
        Column(
            Modifier
                .fillMaxWidth()
                .desktopCardSurface(
                    shape = BrowseCardShape,
                    hoveredState = hoveredState,
                    focusedState = focusedState,
                    containerColor = UiSurface,
                    baseBorderColor = UiHairline,
                    highlightBorderColor = UiAccent.copy(alpha = .65f)
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(UiElevated)) {
                BrowseRoomCardMedia(room, live, avatar)
                Icon(
                    Icons.Default.PlayCircle,
                    null,
                    Modifier.align(Alignment.Center).size(42.dp).graphicsLayer {
                        alpha = if (hoveredState.value || focusedState.value) 1f else 0f
                    },
                    tint = UiAccent
                )
            }
            BrowseRoomCardDetails(room)
        }
    }
}

@Composable
private fun BoxScope.BrowseRoomCardMedia(room: LiveRoomItem, live: Boolean, avatar: Boolean) {
    if (avatar) {
        Box(Modifier.align(Alignment.Center).size(76.dp).clip(CircleShape).background(UiSurface), contentAlignment = Alignment.Center) {
            if (room.cover.isNotBlank()) AsyncImage(room.cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.Person, null, Modifier.size(34.dp), tint = UiMuted)
        }
    } else DesktopCover(room.cover, Modifier.fillMaxSize())
    Surface(Modifier.align(Alignment.TopStart).padding(10.dp), color = Color.Black.copy(alpha = .68f), contentColor = Color.White, shape = BrowseBadgeShape) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(5.dp).background(if (live) Color(0xff4ade80) else Color(0xff94a3b8), CircleShape)); Text(if (live) "直播中" else "未开播", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (room.online > 0) Surface(Modifier.align(Alignment.BottomEnd).padding(10.dp), color = Color.Black.copy(alpha = .68f), contentColor = Color.White, shape = BrowseBadgeShape) {
        Text("${formatDesktopAudience(room.online)} 人气", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

@Composable
private fun BrowseRoomCardDetails(room: LiveRoomItem) {
    Column(Modifier.fillMaxWidth().padding(13.dp)) {
        Text(room.title, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PersonOutline, null, Modifier.size(14.dp), tint = UiMuted)
            Text(room.userName, color = UiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp))
        }
    }
}

@Composable
private fun LoadingRoomCard() {
    Column(Modifier.background(UiSurface, RoundedCornerShape(12.dp))) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(UiElevated, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth(.85f).height(13.dp).background(UiElevated, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(.55f).height(11.dp).background(UiElevated, RoundedCornerShape(4.dp)))
        }
    }
}

internal fun formatDesktopAudience(count: Int): String {
    val unit = when { count >= 100_000_000 -> 100_000_000; count >= 10_000 -> 10_000; else -> return count.coerceAtLeast(0).toString() }
    return String.format(Locale.ROOT, "%.1f", count.toDouble() / unit).removeSuffix(".0") + if (unit == 10_000) "万" else "亿"
}
