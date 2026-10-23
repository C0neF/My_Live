package com.mylive.app.ui.screen.room.quickaccess

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.ui.component.NetImage
import com.mylive.app.ui.screen.room.LocalRoomAccentColor
import com.mylive.app.ui.theme.Icons

@Composable
internal fun quickAccessAccentColor(): Color =
    LocalRoomAccentColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary

@Composable
internal fun rememberQuickAccessListState(viewModel: QuickAccessViewModel, key: String): LazyListState {
    val initial = remember(viewModel, key) { viewModel.scrollPosition(key) }
    val state = rememberLazyListState(initial.first, initial.second)
    DisposableEffect(viewModel, key, state) {
        onDispose { viewModel.saveScrollPosition(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
    }
    return state
}

@Composable
internal fun ResetQuickAccessScrollOnFilterChange(state: LazyListState, filters: QuickAccessFilters) {
    var previous by remember { mutableStateOf(filters) }
    LaunchedEffect(filters) {
        if (previous != filters) {
            state.scrollToItem(0)
            previous = filters
        }
    }
}

@Composable
internal fun QuickAccessSearch(
    filters: QuickAccessFilters, onFiltersChange: (QuickAccessFilters) -> Unit,
    sites: List<String>, tags: List<FollowUserTagEntity> = emptyList(), tag: String
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = filters.query, onValueChange = { onFiltersChange(filters.copy(query = it)) },
            modifier = Modifier.weight(1f).testTag("quick_access_search_$tag"), singleLine = true,
            placeholder = { Text("搜索主播、备注或房间号", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
            trailingIcon = if (filters.query.isNotEmpty()) {{
                IconButton(onClick = { onFiltersChange(filters.copy(query = "")) }) {
                    Icon(Icons.Default.Close, "清除搜索", Modifier.size(18.dp))
                }
            }} else null,
            shape = RoundedCornerShape(18.dp), textStyle = MaterialTheme.typography.bodyMedium)
        Box {
            FilledTonalIconButton(onClick = { expanded = true }, modifier = Modifier.testTag("quick_access_filter_$tag")) {
                Icon(Icons.Default.Tune, "筛选平台和分组", tint = if (filters.siteId != null || filters.tagId != null)
                    quickAccessAccentColor() else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 400.dp)) {
                DropdownMenuItem(text = { Text("所有平台") }, onClick = { onFiltersChange(filters.copy(siteId = null)); expanded = false },
                    trailingIcon = { if (filters.siteId == null) Icon(Icons.Default.Check, null) })
                sites.distinct().forEach { site ->
                    DropdownMenuItem(text = { Text(quickAccessSiteName(site)) },
                        onClick = { onFiltersChange(filters.copy(siteId = site)); expanded = false },
                        trailingIcon = { if (filters.siteId == site) Icon(Icons.Default.Check, null) })
                }
                if (tags.isNotEmpty()) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("所有分组") }, onClick = { onFiltersChange(filters.copy(tagId = null)); expanded = false },
                        trailingIcon = { if (filters.tagId == null) Icon(Icons.Default.Check, null) })
                    tags.forEach { group ->
                        DropdownMenuItem(text = { Text(group.tag) },
                            onClick = { onFiltersChange(filters.copy(tagId = group.id)); expanded = false },
                            trailingIcon = { if (filters.tagId == group.id) Icon(Icons.Default.Check, null) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuickAccessFilterSummary(count: String, filters: QuickAccessFilters,
    tags: List<FollowUserTagEntity> = emptyList(), onReset: () -> Unit) {
    val labels = listOfNotNull(filters.siteId?.let(::quickAccessSiteName), tags.find { it.id == filters.tagId }?.tag)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text((labels + count).joinToString(" · "), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (filters != QuickAccessFilters()) TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("重置筛选") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QuickAccessRoomRow(name: String, face: String, subtitle: String, current: Boolean,
    onClick: () -> Unit, status: String? = null, live: Boolean = false, special: Boolean = false,
    onLongClick: (() -> Unit)? = null, onDelete: (() -> Unit)? = null, refreshHint: String? = null) {
    val accent = quickAccessAccentColor()
    Surface(shape = RoundedCornerShape(18.dp),
        color = if (current) accent.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = if (current) BorderStroke(1.dp, accent.copy(alpha = 0.35f)) else null,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).testTag("quick_access_room_$name")) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                NetImage(face, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), size = 44.dp, isCircle = true)
                if (live) Surface(Modifier.align(Alignment.BottomEnd).size(11.dp), shape = CircleShape,
                    color = accent, border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)) {}
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (special) {
                        Icon(Icons.Default.Star, "特别关注", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(name.ifBlank { "未命名主播" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                refreshHint?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (current || status != null) Text(if (current) "当前观看" else status.orEmpty(),
                style = MaterialTheme.typography.labelSmall, color = if (current || live) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            if (onDelete != null) IconButton(onClick = onDelete) {
                Icon(Icons.Default.MoreVert, "管理${name}的观看记录", Modifier.size(18.dp))
            } else if (!current) Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun QuickAccessLoadingRows() {
    Column(Modifier.fillMaxSize().padding(20.dp).semantics { contentDescription = "正在加载快速入口" },
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        repeat(3) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(150.dp).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)))
                    Box(Modifier.width(90.dp).height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}

@Composable
internal fun QuickAccessEmpty(icon: ImageVector, title: String, description: String,
    actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
