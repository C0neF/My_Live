package com.mylive.app.ui.screen.room.quickaccess

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.ui.component.NetImage
import com.mylive.app.ui.screen.room.LocalRoomAccentColor
import com.mylive.app.ui.theme.Icons

@Composable
internal fun quickAccessAccentColor(): Color = MaterialTheme.colorScheme.primary

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
internal fun QuickAccessSearchButton(
    query: String,
    tag: String,
    onClick: () -> Unit
) {
    val accent = quickAccessAccentColor()
    val hasQuery = query.isNotBlank()
    Surface(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .testTag("quick_access_open_search_$tag"),
        shape = RoundedCornerShape(13.dp),
        color = if (hasQuery) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "打开搜索",
                modifier = Modifier.size(13.dp),
                tint = if (hasQuery) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (hasQuery) query else "搜索",
                style = MaterialTheme.typography.labelSmall,
                color = if (hasQuery) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (hasQuery) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 84.dp)
            )
        }
    }
}

@Composable
internal fun QuickAccessFilterButton(
    filters: QuickAccessFilters,
    onFiltersChange: (QuickAccessFilters) -> Unit,
    sites: List<String>,
    tags: List<FollowUserTagEntity> = emptyList(),
    tag: String,
    size: androidx.compose.ui.unit.Dp = 26.dp
) {
    var expanded by remember { mutableStateOf(false) }
    val hasActiveFilter = filters.siteId != null || filters.tagId != null
    val accent = quickAccessAccentColor()
    Box {
        Surface(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 2))
                .clickable { expanded = true }
                .testTag("quick_access_filter_$tag"),
            shape = RoundedCornerShape(size / 2),
            color = if (hasActiveFilter) accent.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "筛选平台和分组",
                    modifier = Modifier.size(14.dp),
                    tint = if (hasActiveFilter) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

@Composable
internal fun QuickAccessSearch(
    filters: QuickAccessFilters,
    onFiltersChange: (QuickAccessFilters) -> Unit,
    sites: List<String>,
    tags: List<FollowUserTagEntity> = emptyList(),
    tag: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onBack: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val accent = quickAccessAccentColor()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val barHeight = if (compact) 32.dp else 36.dp
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            runCatching { focusRequester.requestFocus() }
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onBack != null) {
            Surface(
                modifier = Modifier
                    .size(barHeight)
                    .clip(CircleShape)
                    .clickable {
                        focusManager.clearFocus()
                        onBack()
                    }
                    .testTag("quick_access_search_back_$tag"),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "返回列表",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.weight(1f).height(barHeight),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ) {
            BasicTextField(
                value = filters.query,
                onValueChange = { onFiltersChange(filters.copy(query = it)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxSize().focusRequester(focusRequester).testTag("quick_access_search_$tag"),
                decorationBox = { innerTextField ->
                    Row(
                        Modifier.fillMaxSize().padding(start = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (filters.query.isEmpty()) {
                                Text(
                                    "搜索主播、备注或房间号",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                        if (filters.query.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { onFiltersChange(filters.copy(query = "")) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, "清除搜索", Modifier.size(14.dp))
                            }
                        }
                    }
                }
            )
        }
        QuickAccessFilterButton(
            filters = filters,
            onFiltersChange = onFiltersChange,
            sites = sites,
            tags = tags,
            tag = tag,
            size = barHeight
        )
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
internal fun QuickAccessStatusChip(
    status: QuickAccessStatus,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = quickAccessAccentColor()
    Surface(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .testTag("quick_access_status_${status.name}"),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                status.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun QuickAccessPlatformChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = quickAccessAccentColor()
    Surface(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun QuickAccessFilterSummary(
    count: String? = null,
    filters: QuickAccessFilters,
    tags: List<FollowUserTagEntity> = emptyList(),
    modifier: Modifier = Modifier,
    onReset: () -> Unit
) {
    val labels = listOfNotNull(
        filters.siteId?.let(::quickAccessSiteName),
        tags.find { it.id == filters.tagId }?.tag,
        count?.takeIf { it.isNotBlank() }
    )
    if (labels.isEmpty() && filters == QuickAccessFilters()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (labels.isNotEmpty()) {
            Text(
                labels.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (filters != QuickAccessFilters()) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onReset),
                shape = RoundedCornerShape(10.dp),
                color = quickAccessAccentColor().copy(alpha = 0.12f)
            ) {
                Text(
                    "重置筛选",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = quickAccessAccentColor(),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QuickAccessRoomRow(name: String, face: String, subtitle: String, current: Boolean,
    onClick: () -> Unit, status: String? = null, live: Boolean = false, special: Boolean = false,
    onLongClick: (() -> Unit)? = null, onDelete: (() -> Unit)? = null, refreshHint: String? = null,
    compact: Boolean = false) {
    val accent = quickAccessAccentColor()
    val vPad = if (compact) 5.dp else 8.dp
    val avatarSize = if (compact) 32.dp else 38.dp
    val rowModifier = if (compact) {
        Modifier.fillMaxWidth().height(if (refreshHint != null) 60.dp else 48.dp)
    } else {
        Modifier.fillMaxWidth()
    }
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Surface(shape = RoundedCornerShape(14.dp),
        color = if (current) accent.copy(alpha = 0.11f) else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = rowModifier.clip(RoundedCornerShape(14.dp))
            .then(clickModifier).testTag("quick_access_room_$name")) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp, top = vPad, bottom = vPad), verticalAlignment = Alignment.CenterVertically) {
            Box {
                NetImage(face, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), size = avatarSize, isCircle = true)
                if (live) Surface(Modifier.align(Alignment.BottomEnd).size(10.dp), shape = CircleShape,
                    color = Color(0xFF34C759), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surfaceContainerLow)) {}
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (special) {
                        Icon(Icons.Default.StarFilled, "特别关注", Modifier.size(12.5.dp), tint = Color(0xFFFF9500))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(name.ifBlank { "未命名主播" }, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                refreshHint?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(6.dp))
            if (current || status != null) Text(if (current) "当前观看" else status.orEmpty(),
                style = MaterialTheme.typography.labelSmall, color = if (current || live) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
            if (onDelete != null) IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.MoreVert, "管理${name}的观看记录", Modifier.size(16.dp))
            } else if (!current) Icon(Icons.Default.ChevronRight, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun QuickAccessLoadingRows() {
    Column(Modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = "正在加载快速入口" },
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(3) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(150.dp).height(11.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)))
                    Box(Modifier.width(90.dp).height(9.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp)))
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
        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
