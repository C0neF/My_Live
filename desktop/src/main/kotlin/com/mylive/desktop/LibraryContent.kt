package com.mylive.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mylive.app.core.site.LiveSite
import com.mylive.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class DesktopHomePage { Recommend, Search, Categories, Follows, History }

@Composable
fun ColumnScope.LibraryContent(
    repository: DesktopLibrary, state: DesktopLibraryState, sites: List<LiveSite>,
    followRefresh: DesktopFollowController,
    history: Boolean, onBrowse: (() -> Unit)? = null, onRoom: (RoomAddress) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedSite by rememberSaveable(history) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(history) { mutableStateOf("") }
    var clearDialog by remember { mutableStateOf(false) }
    var removeFollow by remember { mutableStateOf<SavedRoom?>(null) }
    var editFollow by remember { mutableStateOf<SavedRoom?>(null) }
    var onlyLive by rememberSaveable(history) { mutableStateOf(false) }
    var selectedTag by rememberSaveable(history) { mutableStateOf<String?>(null) }
    var refreshSettings by remember { mutableStateOf(false) }
    val refresh by followRefresh.state.collectAsState()
    val all = if (history) state.history else state.follows
    val rooms = remember(all, selectedSite, onlyLive, selectedTag, query, history) {
        val keyword = query.trim()
        all.filter { room ->
            (selectedSite == null || room.siteId == selectedSite) &&
                (history || !onlyLive || room.liveStatus == 1) && (history || selectedTag == null || selectedTag in room.tags) &&
                (keyword.isEmpty() || room.userName.contains(keyword, true) || room.title.contains(keyword, true) ||
                    room.roomId.contains(keyword, true) || room.note.contains(keyword, true) || room.tags.any { it.contains(keyword, true) })
        }.let { if (history) it else it.sortedWith(compareByDescending<SavedRoom> { it.pinned }
            .thenByDescending { it.liveStatus == 1 }.thenByDescending { it.savedAt }) }
    }
    val grid = rememberDesktopGridState("$history:$selectedSite:$onlyLive:$selectedTag:$query")
    val names = remember(sites) { sites.associate { it.id to it.name } }
    val tags = remember(state.follows) { state.follows.flatMap { it.tags }.distinct().sorted() }
    var tagMenu by remember { mutableStateOf(false) }
    fun clearFilters() { selectedSite = null; query = ""; onlyLive = false; selectedTag = null }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        val heading: @Composable () -> Unit = {
            Column {
                Text(if (history) "观看历史" else "我的关注", style = MaterialTheme.typography.headlineSmall)
                Text(if (history) "最近 ${DesktopLibrary.MAX_HISTORY} 个直播间 · 共 ${all.size} 条记录" else "已关注 ${all.size} 个直播间 · 开播状态一目了然",
                    style = MaterialTheme.typography.bodyMedium, color = UiMuted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        val actions: @Composable () -> Unit = {
            if (history) DesktopOutlinedButton(onClick = { clearDialog = true }, enabled = state.loaded && state.writable && all.isNotEmpty()) {
                Icon(Icons.Default.DeleteOutline, null, Modifier.size(17.dp)); Text("清空历史", Modifier.padding(start = 6.dp))
            } else {
                DesktopTonalButton(onClick = followRefresh::refresh, enabled = state.loaded && state.writable && !refresh.refreshing) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(17.dp)); Text(if (refresh.refreshing) "刷新 ${refresh.completed}/${refresh.total}" else "刷新状态", Modifier.padding(start = 6.dp))
                }
                DesktopIconButton(Icons.Default.Tune, "刷新设置") { refreshSettings = true }
            }
        }
        val search: @Composable (Modifier) -> Unit = { modifier ->
            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("搜索主播、房间或备注") },
                leadingIcon = { DesktopSearchIcon() },
                trailingIcon = { if (query.isNotEmpty()) DesktopIconButton(Icons.Default.Close, "清除筛选关键词") { query = "" } },
                modifier = modifier, shape = RoundedCornerShape(10.dp))
        }
        if (maxWidth >= 1000.dp) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { heading() }; search(Modifier.width(270.dp)); actions()
        } else Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { heading() }; actions() }
            search(Modifier.fillMaxWidth().padding(top = 14.dp))
        }
    }
    FlowRow(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DesktopChoiceChip(selectedSite == null, onClick = { selectedSite = null }, label = { Text("全部平台") })
        sites.forEach { site -> DesktopChoiceChip(selectedSite == site.id, onClick = { selectedSite = site.id }, label = { Text(site.name.removeSuffix("直播")) }) }
        if (!history) {
            DesktopChoiceChip(onlyLive, onClick = { onlyLive = !onlyLive }, label = { Text("只看开播") },
                leading = if (onlyLive) ({ Icon(Icons.Default.Check, null, Modifier.size(15.dp)) }) else null)
            if (tags.isNotEmpty() || selectedTag != null) Box {
                DesktopChoiceChip(selectedTag != null, onClick = { tagMenu = true }, label = { Text(selectedTag ?: "全部分组", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp)) },
                    trailing = { Icon(Icons.Default.ExpandMore, null, Modifier.size(17.dp)) })
                DropdownMenu(tagMenu, { tagMenu = false }, Modifier.heightIn(max = 300.dp)) {
                    DropdownMenuItem(text = { Text("全部分组") }, onClick = { selectedTag = null; tagMenu = false })
                    tags.forEach { tag -> DropdownMenuItem(text = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { selectedTag = tag; tagMenu = false }) }
                }
            }
        }
    }
    if (!history) {
        refresh.error?.let { error -> DesktopErrorBanner(error, "重试刷新", followRefresh::refresh) }
        if (refresh.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 10.dp))
    }
    if (!state.loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.loaded && rooms.isEmpty()) {
        val filtered = all.isNotEmpty()
        DesktopEmptyState(when { filtered -> "没有匹配的记录。"; history -> "还没有观看历史"; else -> "还没有关注" },
            when { filtered -> "试试其他关键词，或清除当前筛选。"; history -> "打开直播间后，会自动为你保留最近的观看记录。"; else -> "在直播间点击关注，下次就能从这里找到主播。" },
            if (history) Icons.Default.History else Icons.Default.FavoriteBorder, Modifier.weight(1f).fillMaxHeight(),
            if (filtered) "清除筛选" else if (onBrowse != null) "去发现直播" else null,
            if (filtered) ::clearFilters else onBrowse)
    } else DesktopRoomGrid(grid, Modifier.weight(1f).fillMaxWidth()) {
        items(rooms, key = { it.key }) { room ->
            LibraryRoomCard(
                room = room,
                siteLabel = names[room.siteId]?.removeSuffix("直播") ?: room.siteId,
                history = history,
                writable = state.writable,
                onOpen = { onRoom(room.address) },
                onEdit = { editFollow = room },
                onDelete = { if (history) scope.launch { repository.removeHistory(room.key) } else removeFollow = room }
            )
        }
    }
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("清空观看历史？") },
        text = { Text("将删除本机全部观看历史。关注列表会保留。") },
        confirmButton = { DesktopPrimaryButton(onClick = { clearDialog = false; scope.launch { repository.clearHistory() } }) { Text("清空历史") } },
        dismissButton = { DesktopTextButton(onClick = { clearDialog = false }) { Text("取消") } })
    removeFollow?.let { room ->
        AlertDialog(onDismissRequest = { removeFollow = null }, title = { Text("取消关注？") }, text = { Text(room.userName) },
            confirmButton = { DesktopPrimaryButton(onClick = { removeFollow = null; scope.launch { repository.removeFollow(room.key) } }) { Text("取消关注") } },
            dismissButton = { DesktopTextButton(onClick = { removeFollow = null }) { Text("保留关注") } })
    }
    editFollow?.let { room ->
        var pinned by remember(room.key) { mutableStateOf(room.pinned) }
        var tagsText by remember(room.key) { mutableStateOf(room.tags.joinToString("\n")) }
        var note by remember(room.key) { mutableStateOf(room.note) }
        AlertDialog(onDismissRequest = { editFollow = null }, title = { Text("管理关注") }, text = {
            Column(Modifier.width(400.dp)) {
                Text(room.userName)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(pinned, { pinned = it }); Text("置顶关注") }
                OutlinedTextField(tagsText, { tagsText = it }, label = { Text("分组（每行一个）") }, minLines = 2)
                OutlinedTextField(note, { if (it.length <= 1000) note = it }, label = { Text("备注") }, minLines = 2,
                    modifier = Modifier.padding(top = 10.dp))
            }
        }, confirmButton = { DesktopPrimaryButton(onClick = {
            editFollow = null; scope.launch { repository.editFollow(room.key, pinned, tagsText.lines(), note) }
        }, enabled = tagsText.lines().all { it.length <= 80 }) { Text("保存") } }, dismissButton = { DesktopTextButton(onClick = { editFollow = null }) { Text("取消") } })
    }
    if (refreshSettings) AlertDialog(onDismissRequest = { refreshSettings = false }, title = { Text("关注刷新") }, text = {
        Column {
            SettingsToggle("自动刷新", state.followOptions.autoRefresh, state.writable) { enabled ->
                scope.launch { repository.updateFollowOptions { it.copy(autoRefresh = enabled) } }
            }
            Text("刷新间隔", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 300, 900).forEach { seconds -> DesktopChoiceChip(state.followOptions.intervalSeconds == seconds,
                    onClick = { scope.launch { repository.updateFollowOptions { it.copy(intervalSeconds = seconds) } } },
                    enabled = state.writable, label = { Text(if (seconds < 60) "${seconds}秒" else "${seconds / 60}分") }) }
            }
            Text("只在程序运行时刷新，每次最多同时查询 4 个房间。", color = UiMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }, confirmButton = { DesktopOutlinedButton(onClick = { refreshSettings = false }) { Text("完成") } })
}

private val LibraryCardShape = RoundedCornerShape(12.dp)
private val LibraryBadgeShape = RoundedCornerShape(5.dp)

@Composable
private fun LibraryRoomCard(
    room: SavedRoom,
    siteLabel: String,
    history: Boolean,
    writable: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val interaction = remember(room.key) { MutableInteractionSource() }
    val hoveredState = interaction.collectIsHoveredAsState()
    val focusedState = interaction.collectIsFocusedAsState()
    val baseBorder = if (room.pinned && !history) UiAccent.copy(alpha = .45f) else UiHairline
    CompositionLocalProvider(LocalContentColor provides UiInk) {
        Column(
            Modifier
                .fillMaxWidth()
                .desktopCardSurface(
                    shape = LibraryCardShape,
                    hoveredState = hoveredState,
                    focusedState = focusedState,
                    containerColor = UiSurface,
                    baseBorderColor = baseBorder,
                    highlightBorderColor = UiAccent.copy(alpha = .65f)
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onOpen
                )
        ) {
            LibraryRoomCardContent(room, siteLabel, history, writable, onEdit, onDelete)
        }
    }
}

@Composable
private fun LibraryRoomCardContent(
    room: SavedRoom,
    siteLabel: String,
    history: Boolean,
    writable: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        DesktopCover(room.cover.ifBlank { room.userAvatar }, Modifier.fillMaxSize())
        Surface(Modifier.padding(10.dp), color = UiBg.copy(alpha = .9f), shape = LibraryBadgeShape) {
            Text(siteLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
        }
    }
    Column(Modifier.padding(start = 13.dp, end = 10.dp, top = 12.dp, bottom = 8.dp)) {
        if (!history) Text(buildString {
            if (room.pinned) append("置顶 · ")
            if (room.tags.isNotEmpty()) append(room.tags.joinToString(" / ")).append(" · ")
            append(when (room.liveStatus) { 1 -> "直播中"; 2 -> "未开播"; else -> "状态未刷新" })
        }, color = if (room.liveStatus == 1) UiAccent else UiMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 7.dp))
        Text(room.title.ifBlank { room.userName }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
        Text(room.userName, color = UiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        if (!history && room.note.isNotBlank()) Text(room.note, color = UiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatSavedTime(if (!history && room.lastCheckedAt > 0) room.lastCheckedAt else room.savedAt), style = MaterialTheme.typography.labelSmall, color = UiMuted, modifier = Modifier.weight(1f))
            if (!history) DesktopIconButton(Icons.Default.Edit, "分组、置顶与备注", Modifier.size(32.dp), enabled = writable, showTooltip = false, onClick = onEdit)
            DesktopIconButton(Icons.Default.DeleteOutline, if (history) "删除这条历史" else "取消关注", Modifier.size(32.dp), enabled = writable, showTooltip = false, onClick = onDelete)
        }
    }
}

private val savedTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private fun formatSavedTime(millis: Long): String = runCatching {
    savedTimeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}.getOrDefault("")
