package com.mylive.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.site.LiveSite
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ColumnScope.BrowseContent(
    sites: List<LiveSite>,
    controller: DesktopBrowseController,
    searchPage: Boolean = false,
    onDirectRoom: () -> Unit = {},
    onRoom: (RoomAddress) -> Unit
) {
    val state by controller.state.collectAsState()
    var query by rememberSaveable(state.siteId) { mutableStateOf(state.query) }
    var searchAnchors by rememberSaveable { mutableStateOf(state.mode == BrowseMode.Anchors) }
    var appliedQuery by rememberSaveable(state.siteId) { mutableStateOf(state.query) }
    var categoryGroups by rememberSaveable { mutableStateOf(mapOf<String, Int>()) }
    LaunchedEffect(state.query) {
        if (state.query != appliedQuery) {
            query = state.query
            appliedQuery = state.query
        }
    }
    fun search() {
        if (query.isBlank()) controller.prepareSearch(state.siteId)
        else controller.search(state.siteId, query, searchAnchors)
    }
    fun selectPlatform(siteId: String) {
        if (searchPage) {
            if (query.isBlank()) controller.prepareSearch(siteId)
            else controller.search(siteId, query, searchAnchors)
        } else if (state.mode in listOf(BrowseMode.Categories, BrowseMode.CategoryRooms)) {
            controller.categories(siteId)
        } else {
            controller.recommend(siteId)
        }
    }
    val sourceKey = "${state.siteId}:${state.mode}:${state.query}:${state.category?.parentId}:${state.category?.id}"
    val grid = rememberDesktopGridState(sourceKey)
    LaunchedEffect(grid, sourceKey) {
        snapshotFlow {
            val layout = grid.layoutInfo
            state.page > 0 && state.hasMore && !state.loading && state.error == null && state.mode != BrowseMode.Categories &&
                layout.totalItemsCount > 0 && (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 6
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) controller.more() }
    }

    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val activeSiteAccent = desktopPlatformAccentColor(state.siteId, dark) ?: UiAccent

    if (searchPage) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            val inlineHeader = maxWidth >= 880.dp
            if (inlineHeader) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("搜索", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "跨平台查找直播间与主播，或直接输入房间号",
                            style = MaterialTheme.typography.bodySmall,
                            color = UiMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    BrowsePlatformCapsuleBar(
                        sites = sites,
                        selectedSiteId = state.siteId,
                        dark = dark,
                        fillWidth = false,
                        loading = state.loading,
                        showRefresh = false,
                        onSelectSite = ::selectPlatform,
                        onRefresh = controller::refresh
                    )
                    DesktopOutlinedButton(onDirectRoom, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) {
                        Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                        Text("房间号 / 链接", Modifier.padding(start = 6.dp))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("搜索", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        DesktopOutlinedButton(onDirectRoom, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp)) {
                            Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                            Text("房间号 / 链接", Modifier.padding(start = 6.dp))
                        }
                    }
                    BrowsePlatformCapsuleBar(
                        sites = sites,
                        selectedSiteId = state.siteId,
                        dark = dark,
                        fillWidth = true,
                        loading = state.loading,
                        showRefresh = false,
                        onSelectSite = ::selectPlatform,
                        onRefresh = controller::refresh
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(14.dp),
            color = UiSurface,
            border = BorderStroke(DesktopHairlineWidth, UiHairline)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DesktopChoiceChip(
                        !searchAnchors,
                        onClick = { searchAnchors = false; if (query.isNotBlank()) search() },
                        label = { Text("直播间") }
                    )
                    DesktopChoiceChip(
                        searchAnchors,
                        onClick = { searchAnchors = true; if (query.isNotBlank()) search() },
                        label = { Text("主播") }
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DesktopTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(if (searchAnchors) "搜索主播" else "搜索直播间") },
                        leadingIcon = { DesktopSearchIcon() },
                        trailingIcon = {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = query.isNotEmpty(),
                                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.78f, animationSpec = tween(140, easing = DesktopStandardEasing)),
                                exit = fadeOut(tween(110)) + scaleOut(targetScale = 0.78f, animationSpec = tween(110, easing = DesktopStandardEasing))
                            ) {
                                DesktopIconButton(Icons.Default.Close, "清除搜索", Modifier.size(26.dp)) {
                                    query = ""
                                    appliedQuery = ""
                                    controller.prepareSearch(state.siteId)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        modifier = Modifier.weight(1f).testTag("browse-search")
                    )
                    DesktopPrimaryButton(
                        onClick = { search() },
                        enabled = query.isNotBlank(),
                        modifier = Modifier.height(38.dp).testTag("submit-search")
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                        Text("搜索", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        var cachedSearchHeading by remember { mutableStateOf(state.query) }
        if (state.query.isNotBlank()) cachedSearchHeading = state.query
        AnimatedVisibility(
            visible = state.query.isNotBlank(),
            enter = desktopExpandEnter(),
            exit = desktopCollapseExit()
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "“" + cachedSearchHeading + "” 的搜索结果",
                    color = UiMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                DesktopIconButton(Icons.Default.Refresh, "刷新", enabled = !state.loading, onClick = controller::refresh)
            }
        }
    } else if (state.mode in listOf(BrowseMode.Categories, BrowseMode.CategoryRooms)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            val inlineHeader = maxWidth >= 880.dp
            val titleBlock: @Composable RowScope.() -> Unit = {
                this.AnimatedVisibility(
                    visible = state.mode == BrowseMode.CategoryRooms,
                    enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.85f, animationSpec = tween(160, easing = DesktopStandardEasing)),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, animationSpec = tween(120, easing = DesktopStandardEasing))
                ) {
                    DesktopIconButton(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "返回分类",
                        modifier = Modifier.padding(end = 6.dp)
                    ) { controller.categories(state.siteId) }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.mode == BrowseMode.Categories) "直播分类" else state.category?.name.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (state.mode == BrowseMode.Categories) {
                        Text(
                            "按游戏、娱乐、知识等频道探索直播",
                            style = MaterialTheme.typography.bodySmall,
                            color = UiMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            if (inlineHeader) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    titleBlock()
                    BrowsePlatformCapsuleBar(
                        sites = sites,
                        selectedSiteId = state.siteId,
                        dark = dark,
                        fillWidth = false,
                        loading = state.loading,
                        showRefresh = true,
                        onSelectSite = ::selectPlatform,
                        onRefresh = controller::refresh
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        titleBlock()
                        DesktopIconButton(Icons.Default.Refresh, "刷新", enabled = !state.loading, onClick = controller::refresh)
                    }
                    BrowsePlatformCapsuleBar(
                        sites = sites,
                        selectedSiteId = state.siteId,
                        dark = dark,
                        fillWidth = true,
                        loading = state.loading,
                        showRefresh = false,
                        onSelectSite = ::selectPlatform,
                        onRefresh = controller::refresh
                    )
                }
            }
        }
    } else {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            val inlineHeader = maxWidth >= 900.dp
            val titleSection: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("发现直播", style = MaterialTheme.typography.headlineSmall)
                        if (!state.loading && state.rooms.isNotEmpty()) {
                            Text(
                                "${state.rooms.size} 场热播",
                                style = MaterialTheme.typography.bodyMedium,
                                color = UiMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    Text(
                        "汇聚热门直播，按平台自由切换",
                        style = MaterialTheme.typography.bodySmall,
                        color = UiMuted,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            if (inlineHeader) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    titleSection(Modifier.weight(1f))
                    BrowsePlatformCapsuleBar(
                        sites = sites,
                        selectedSiteId = state.siteId,
                        dark = dark,
                        fillWidth = false,
                        loading = state.loading,
                        showRefresh = true,
                        onSelectSite = ::selectPlatform,
                        onRefresh = controller::refresh
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    titleSection(Modifier.fillMaxWidth())
                    BrowsePlatformCapsuleBar(
                        sites = sites,
                        selectedSiteId = state.siteId,
                        dark = dark,
                        fillWidth = true,
                        loading = state.loading,
                        showRefresh = true,
                        onSelectSite = ::selectPlatform,
                        onRefresh = controller::refresh
                    )
                }
            }
        }
    }

    DesktopThinProgressBar(
        visible = state.loading,
        modifier = Modifier.padding(bottom = 8.dp),
        color = activeSiteAccent
    )
    var cachedBrowseError by remember { mutableStateOf(state.error.orEmpty()) }
    if (state.error != null) cachedBrowseError = state.error.orEmpty()
    AnimatedVisibility(
        visible = state.error != null,
        enter = desktopExpandEnter(),
        exit = desktopCollapseExit()
    ) {
        DesktopErrorBanner(cachedBrowseError, onRetry = controller::retry)
    }
    if (searchPage && state.query.isBlank() && !state.loading) {
        DesktopEmptyState(
            "查找感兴趣的直播",
            "选择平台，输入直播间或主播关键词。也可以通过房间号或链接直接进入。",
            Icons.Default.Search,
            Modifier.weight(1f).fillMaxHeight()
        )
    } else if (state.mode == BrowseMode.Categories) {
        val selectedGroup = categoryGroups[state.siteId] ?: 0
        val group = selectedGroup.coerceIn(0, state.categories.lastIndex.coerceAtLeast(0))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val groupsList = rememberLazyListState()
            Surface(
                modifier = Modifier.width(168.dp).fillMaxHeight(),
                shape = RoundedCornerShape(14.dp),
                color = UiSurface,
                border = BorderStroke(DesktopHairlineWidth, UiHairline)
            ) {
                Box(Modifier.fillMaxSize().padding(8.dp)) {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(end = 6.dp),
                        state = groupsList,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(state.categories.indices.toList()) { index ->
                            val isSelected = group == index
                            DesktopCategoryRailItem(
                                title = state.categories[index].name,
                                selected = isSelected,
                                accent = activeSiteAccent,
                                onClick = { categoryGroups = categoryGroups + (state.siteId to index) }
                            )
                        }
                    }
                    VerticalScrollbar(
                        rememberScrollbarAdapter(groupsList),
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
            }
            AnimatedContent(
                targetState = state.siteId to group,
                transitionSpec = {
                    val dir = if (initialState.first == targetState.first) {
                        targetState.second.compareTo(initialState.second)
                    } else 0
                    desktopDirectionalTabTransform(dir)
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { (targetSiteId, targetGroup) ->
                val children = state.categories.getOrNull(targetGroup)?.children.orEmpty().distinctBy { it.id }
                if (!state.loading && children.isEmpty()) {
                    DesktopEmptyState(
                        "暂无分类",
                        "可以切换平台，或直接输入直播间链接。",
                        Icons.Default.GridView,
                        Modifier.fillMaxSize()
                    )
                } else {
                    DesktopRoomGrid(
                        rememberDesktopGridState("$targetSiteId:categories:$targetGroup"),
                        Modifier.fillMaxSize(),
                        minCardWidth = 144.dp
                    ) {
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
                                            highlightBorderColor = activeSiteAccent.copy(alpha = .5f)
                                        )
                                        .clickable(
                                            interactionSource = categoryInteraction,
                                            indication = null,
                                            role = Role.Button,
                                            onClick = { controller.category(targetSiteId, category) }
                                        )
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(UiFieldFill),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!category.pic.isNullOrBlank()) {
                                            AsyncImage(
                                                category.pic,
                                                null,
                                                Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Category, null, Modifier.size(26.dp), tint = activeSiteAccent)
                                        }
                                    }
                                    Text(
                                        category.name,
                                        modifier = Modifier.padding(top = 10.dp),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (!state.loading && state.rooms.isEmpty() && state.anchors.isEmpty()) {
        DesktopEmptyState(
            if (state.error != null) "暂时没有可显示的直播" else "没有找到直播间",
            "可以切换平台，或调整搜索关键词。",
            Icons.Default.SearchOff,
            Modifier.weight(1f).fillMaxHeight(),
            if (state.query.isNotBlank()) "清除搜索" else null,
            if (state.query.isNotBlank()) ({ query = ""; appliedQuery = ""; controller.prepareSearch(state.siteId) }) else null
        )
    } else {
        DesktopRoomGrid(grid, Modifier.weight(1f).fillMaxWidth()) {
            if (state.loading && state.rooms.isEmpty() && state.anchors.isEmpty()) items(8) { LoadingRoomCard() }
            if (state.mode == BrowseMode.Anchors) {
                items(state.anchors, key = { it.roomId }) { anchor ->
                    BrowseRoomCard(
                        LiveRoomItem(anchor.roomId, anchor.userName, anchor.avatar, "房间 ${anchor.roomId}", faceUrl = anchor.avatar),
                        live = anchor.liveStatus,
                        avatar = true,
                        accent = activeSiteAccent,
                        onClick = { onRoom(RoomAddress(state.siteId, anchor.roomId)) }
                    )
                }
            } else {
                items(state.rooms, key = { it.roomId }) { room ->
                    BrowseRoomCard(room, accent = activeSiteAccent) {
                        onRoom(RoomAddress(state.siteId, room.roomId))
                    }
                }
            }
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
}

@Composable
private fun DesktopCategoryRailItem(
    title: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val interaction = remember(title) { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState()
    val pressed = interaction.collectIsPressedAsState()
    val baseFill by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = .14f) else Color.Transparent,
        animationSpec = tween(durationMillis = 160, easing = DesktopStandardEasing)
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) accent else UiInk.copy(alpha = .85f),
        animationSpec = tween(durationMillis = 160, easing = DesktopStandardEasing)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .desktopInteractiveSurface(
                shape = RoundedCornerShape(9.dp),
                hoveredState = hovered,
                pressedState = pressed,
                baseColor = baseFill,
                hoverColor = if (selected) accent.copy(alpha = .18f) else UiInk.copy(alpha = .06f),
                pressColor = accent.copy(alpha = .22f)
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = labelColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun BrowsePlatformCapsuleBar(
    sites: List<LiveSite>,
    selectedSiteId: String,
    dark: Boolean,
    fillWidth: Boolean,
    loading: Boolean,
    showRefresh: Boolean,
    onSelectSite: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        shape = RoundedCornerShape(10.dp),
        color = UiFieldFill
    ) {
        Row(
            modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sites.forEach { site ->
                DesktopTabButton(
                    title = site.name.removeSuffix("直播"),
                    selected = selectedSiteId == site.id,
                    modifier = if (fillWidth) Modifier.weight(1f) else Modifier.widthIn(min = 80.dp),
                    onClick = { onSelectSite(site.id) }
                )
            }
            if (showRefresh) {
                DesktopIconButton(
                    Icons.Default.Refresh,
                    "刷新列表 F5",
                    enabled = !loading,
                    onClick = onRefresh
                )
            }
        }
    }
}

private val BrowseCardShape = RoundedCornerShape(14.dp)
private val BrowseBadgeShape = RoundedCornerShape(999.dp)
private val BrowseMediaScrim = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.58f to Color.Transparent,
    1.0f to Color(0xB806080C)
)

@Composable
private fun BrowseRoomCard(
    room: LiveRoomItem,
    live: Boolean = true,
    avatar: Boolean = false,
    accent: Color = UiAccent,
    onClick: () -> Unit
) {
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
                    baseBorderColor = UiHairline.copy(alpha = .45f),
                    highlightBorderColor = accent.copy(alpha = .5f)
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
                BrowseHoverPlayBadge(
                    hoveredState = hoveredState,
                    focusedState = focusedState,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            BrowseRoomCardDetails(room)
        }
    }
}

@Composable
private fun BrowseHoverPlayBadge(
    hoveredState: State<Boolean>,
    focusedState: State<Boolean>,
    modifier: Modifier = Modifier
) {
    val active = hoveredState.value || focusedState.value
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = DesktopStandardEasing)
    )
    Box(
        modifier
            .size(44.dp)
            .graphicsLayer {
                alpha = progress
                val scale = 0.86f + 0.14f * progress
                scaleX = scale
                scaleY = scale
            }
            .background(Color(0xCC0A0C10), CircleShape)
            .border(DesktopHairlineWidth, Color.White.copy(alpha = .26f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.PlayArrow,
            null,
            Modifier.size(24.dp),
            tint = Color.White
        )
    }
}

@Composable
private fun BoxScope.BrowseRoomCardMedia(room: LiveRoomItem, live: Boolean, avatar: Boolean) {
    if (avatar) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(76.dp)
                .clip(CircleShape)
                .background(UiSurface)
                .border(1.5.dp, if (live) DesktopLiveEmerald else UiHairline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (room.cover.isNotBlank()) AsyncImage(room.cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.Person, null, Modifier.size(34.dp), tint = UiMuted)
        }
    } else {
        DesktopCover(room.cover, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(BrowseMediaScrim))
    }
    Surface(
        Modifier.align(Alignment.TopStart).padding(10.dp),
        color = Color(0xBF0A0C10),
        contentColor = Color.White,
        shape = BrowseBadgeShape,
        border = BorderStroke(DesktopHairlineWidth, Color.White.copy(alpha = .14f))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (live) DesktopLiveEmerald else Color(0xff94a3b8), CircleShape)
            )
            Text(if (live) "直播中" else "未开播", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (room.online > 0) {
        Surface(
            Modifier.align(Alignment.BottomEnd).padding(10.dp),
            color = Color(0xBF0A0C10),
            contentColor = Color.White,
            shape = BrowseBadgeShape
        ) {
            Text(
                "${formatDesktopAudience(room.online)} 人气",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun BrowseRoomCardDetails(room: LiveRoomItem) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp)) {
        Text(
            room.title,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyLarge
        )
        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(UiFieldFill)
                    .border(DesktopHairlineWidth, UiHairline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (room.faceUrl.isNotBlank()) {
                    AsyncImage(room.faceUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.PersonOutline, null, Modifier.size(13.dp), tint = UiMuted)
                }
            }
            Text(
                room.userName,
                color = UiMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LoadingRoomCard() {
    Column(
        Modifier
            .background(UiSurface, BrowseCardShape)
            .border(DesktopHairlineWidth, UiHairline, BrowseCardShape)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(UiElevated, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
        )
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth(.85f).height(13.dp).background(UiElevated, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(.55f).height(11.dp).background(UiElevated, RoundedCornerShape(4.dp)))
        }
    }
}

internal fun formatDesktopAudience(count: Int): String {
    val unit = when {
        count >= 100_000_000 -> 100_000_000
        count >= 10_000 -> 10_000
        else -> return count.coerceAtLeast(0).toString()
    }
    return String.format(Locale.ROOT, "%.1f", count.toDouble() / unit).removeSuffix(".0") + if (unit == 10_000) "万" else "亿"
}
