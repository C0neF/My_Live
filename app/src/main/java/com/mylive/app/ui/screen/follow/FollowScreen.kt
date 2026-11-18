package com.mylive.app.ui.screen.follow

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylive.app.R
import com.mylive.app.core.common.readUtf8TextWithinLimit
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.playbackRoomId
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.ui.component.BackToTopButton
import com.mylive.app.ui.component.FollowUserItem
import com.mylive.app.ui.component.FollowRefreshProgressLine
import com.mylive.app.ui.component.followRefreshHint
import com.mylive.app.ui.component.rememberFollowRefreshEntry
import com.mylive.app.ui.component.backToTopButtonMetrics
import com.mylive.app.ui.component.status.EmptyState
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.navigateToRoom
import com.mylive.app.ui.screen.backToTopButtonVisible
import com.mylive.app.ui.screen.isScrollableContentAtTop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowScreen(
    navigator: Navigator,
    refreshSignal: Int = 0,
    onRevealBottomBar: () -> Unit = {},
    contentBottomPadding: Dp = 96.dp,
    followCardColumns: Int = 1,
    viewModel: FollowViewModel = hiltViewModel()
) {
    val allFollows by viewModel.follows.collectAsStateWithLifecycle()
    val filteredFollows by viewModel.filteredFollows.collectAsStateWithLifecycle()
    val groupMode by viewModel.groupMode.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val groupOptions by viewModel.groupOptions.collectAsStateWithLifecycle()
    val userTags by viewModel.userTags.collectAsStateWithLifecycle()

    val updatingStatus by viewModel.updatingStatus.collectAsStateWithLifecycle()
    val refreshProgress by viewModel.refreshProgress.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val liveListState = rememberLazyListState()
    val inactiveListState = rememberLazyListState()
    val cardColumns = followCardColumns.coerceAtLeast(1)
    val useTabletTwoColumnLayout = followUseTabletTwoColumnLayout(cardColumns)
    var selectedTabletPlatformId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTabletTagId by rememberSaveable { mutableStateOf<String?>(null) }
    val tabletPlatformOptions = remember(allFollows) {
        followTabletPlatformOptions(allFollows)
    }
    val selectedTabletTag = remember(userTags, selectedTabletTagId) {
        userTags.firstOrNull { it.id == selectedTabletTagId }
    }
    val tabletFollows = remember(allFollows, selectedTabletPlatformId, selectedTabletTag) {
        allFollows.filter { follow ->
            (selectedTabletPlatformId == null || follow.siteId == selectedTabletPlatformId) &&
                (selectedTabletTag == null || follow.id in selectedTabletTag.userIds)
        }
    }
    val displayedFollows = remember(useTabletTwoColumnLayout, tabletFollows, filteredFollows) {
        if (useTabletTwoColumnLayout) tabletFollows else filteredFollows
    }
    val tabletStatusColumns = remember(displayedFollows) { followTabletStatusColumns(displayedFollows) }
    val compactStatusBuckets = remember(displayedFollows) { followStatusBuckets(displayedFollows) }
    var isAtTop by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showGroupModeMenu by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportedJson by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }

    // Dialog state for tags
    var longPressedFollow by remember { mutableStateOf<FollowUserEntity?>(null) }
    var showTagsManagerDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showRenameTagDialog by remember { mutableStateOf<FollowUserTagEntity?>(null) }

    // Confirm dialog for unfollow
    var unfollowTarget by remember { mutableStateOf<FollowUserEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.updateFollowStatus()
    }

    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) {
            viewModel.updateFollowStatus()
        }
    }

    LaunchedEffect(tabletPlatformOptions, selectedTabletPlatformId) {
        val selectedSiteStillExists = tabletPlatformOptions.any { it.siteId == selectedTabletPlatformId }
        if (selectedTabletPlatformId != null && !selectedSiteStillExists) {
            selectedTabletPlatformId = null
        }
    }

    LaunchedEffect(userTags, selectedTabletTagId) {
        if (selectedTabletTagId != null && userTags.none { it.id == selectedTabletTagId }) {
            selectedTabletTagId = null
        }
    }

    LaunchedEffect(displayedFollows.isEmpty()) {
        if (displayedFollows.isEmpty()) {
            isAtTop = true
        }
    }

    LaunchedEffect(
        useTabletTwoColumnLayout,
        gridState,
        liveListState,
        inactiveListState,
        displayedFollows.isNotEmpty()
    ) {
        if (displayedFollows.isNotEmpty()) {
            snapshotFlow {
                if (useTabletTwoColumnLayout) {
                    isScrollableContentAtTop(
                        firstVisibleItemIndex = liveListState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = liveListState.firstVisibleItemScrollOffset
                    ) && isScrollableContentAtTop(
                        firstVisibleItemIndex = inactiveListState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = inactiveListState.firstVisibleItemScrollOffset
                    )
                } else {
                    isScrollableContentAtTop(
                        firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset
                    )
                }
            }
                .distinctUntilChanged()
                .collect { isAtTop = it }
        }
    }

    // File export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = viewModel.exportFollows()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // File import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { input -> input.readUtf8TextWithinLimit() } ?: ""
                    viewModel.importFollows(json)
                    Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Compact Studio Header
        val totalLiveCount = remember(allFollows) { allFollows.count { it.liveStatus == 1 } }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (useTabletTwoColumnLayout) 20.dp else 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.nav_follow),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (allFollows.isNotEmpty()) {
                Text(
                    text = "$totalLiveCount 在播",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { viewModel.updateFollowStatus() },
                enabled = !updatingStatus,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "刷新状态",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("标签管理") },
                        onClick = {
                            showMenu = false
                            showTagsManagerDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导出到文件") },
                        onClick = {
                            showMenu = false
                            exportLauncher.launch("follows_export.json")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("从文件导入") },
                        onClick = {
                            showMenu = false
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导出为文本") },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                exportedJson = viewModel.exportFollows()
                                showExportDialog = true
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("从文本导入") },
                        onClick = {
                            showMenu = false
                            showImportDialog = true
                        }
                    )
                }
            }
        }

        if (useTabletTwoColumnLayout) {
            FollowTabletPlatformBar(
                options = tabletPlatformOptions,
                selectedSiteId = selectedTabletPlatformId,
                tags = userTags,
                selectedTagId = selectedTabletTagId,
                onSelected = { siteId ->
                    if (selectedTabletPlatformId != siteId) {
                        selectedTabletPlatformId = siteId
                        scope.launch {
                            liveListState.scrollToItem(0)
                            inactiveListState.scrollToItem(0)
                        }
                    }
                },
                onTagSelected = { tagId ->
                    if (selectedTabletTagId != tagId) {
                        selectedTabletTagId = tagId
                        scope.launch {
                            liveListState.scrollToItem(0)
                            inactiveListState.scrollToItem(0)
                        }
                    }
                }
            )
        } else {
            // Compact filter bar: platform options are few, so show them all without horizontal scroll.
            val compactFilterMetrics = followCompactPlatformFilterMetrics()
            if (followCompactFilterOptionsScrollable(groupMode)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactGroupModeMenu(
                        modifier = Modifier.width(followCompactModeSelectorWidthDp().dp),
                        groupMode = groupMode,
                        expanded = showGroupModeMenu,
                        onExpandedChange = { showGroupModeMenu = it },
                        onModeSelected = viewModel::setGroupMode,
                        pillHorizontalPaddingDp = compactFilterMetrics.pillHorizontalPaddingDp,
                        fontSizeSp = compactFilterMetrics.fontSizeSp,
                        iconSizeDp = compactFilterMetrics.iconSizeDp
                    )

                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(groupOptions, key = { it.id }) { option ->
                            CompactFilterPill(
                                text = option.title,
                                selected = selectedGroupId == option.id,
                                onClick = { viewModel.setGroupOption(option.id) }
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = compactFilterMetrics.horizontalPaddingDp.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(compactFilterMetrics.itemGapDp.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactGroupModeMenu(
                        modifier = Modifier.width(followCompactModeSelectorWidthDp().dp),
                        groupMode = groupMode,
                        expanded = showGroupModeMenu,
                        onExpandedChange = { showGroupModeMenu = it },
                        onModeSelected = viewModel::setGroupMode
                    )

                    groupOptions.forEach { option ->
                        CompactFilterPill(
                            modifier = Modifier.weight(1f),
                            text = option.title,
                            selected = selectedGroupId == option.id,
                            onClick = { viewModel.setGroupOption(option.id) },
                            horizontalPaddingDp = compactFilterMetrics.pillHorizontalPaddingDp,
                            fontSizeSp = compactFilterMetrics.fontSizeSp
                        )
                    }
                }
            }
        }

        FollowRefreshProgressLine(refreshProgress)
        PullToRefreshBox(
            isRefreshing = updatingStatus,
            onRefresh = { viewModel.updateFollowStatus() },
            modifier = Modifier.fillMaxSize()
        ) {

            if (displayedFollows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(message = stringResource(R.string.follow_empty))
                }
            } else if (useTabletTwoColumnLayout) {
                FollowTabletTwoColumnContent(
                    columns = tabletStatusColumns,
                    liveListState = liveListState,
                    inactiveListState = inactiveListState,
                    contentBottomPadding = contentBottomPadding,
                    navigator = navigator,
                    viewModel = viewModel,
                    onLongClick = { longPressedFollow = it },
                    onUnfollowConfirm = { unfollowTarget = it }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cardColumns),
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = contentBottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 直播中
                    if (compactStatusBuckets.live.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FollowGroupHeader(
                                title = stringResource(R.string.follow_group_live),
                                count = compactStatusBuckets.live.size,
                                isLive = true
                            )
                        }
                        items(compactStatusBuckets.live, key = { it.id }) { follow ->
                            SwipeableFollowItem(
                                follow = follow,
                                viewModel = viewModel,
                                navigator = navigator,
                                cardHorizontalPadding = 0.dp,
                                onLongClick = { longPressedFollow = follow },
                                onUnfollowConfirm = { unfollowTarget = it }
                            )
                        }
                    }

                    // 未知
                    if (compactStatusBuckets.unknown.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FollowGroupHeader(
                                title = stringResource(R.string.follow_group_unknown),
                                count = compactStatusBuckets.unknown.size
                            )
                        }
                        items(compactStatusBuckets.unknown, key = { it.id }) { follow ->
                            SwipeableFollowItem(
                                follow = follow,
                                viewModel = viewModel,
                                navigator = navigator,
                                cardHorizontalPadding = 0.dp,
                                onLongClick = { longPressedFollow = follow },
                                onUnfollowConfirm = { unfollowTarget = it }
                            )
                        }
                    }

                    // 未开播
                    if (compactStatusBuckets.offline.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FollowGroupHeader(
                                title = stringResource(R.string.follow_group_offline),
                                count = compactStatusBuckets.offline.size
                            )
                        }
                        items(compactStatusBuckets.offline, key = { it.id }) { follow ->
                            SwipeableFollowItem(
                                follow = follow,
                                viewModel = viewModel,
                                navigator = navigator,
                                cardHorizontalPadding = 0.dp,
                                onLongClick = { longPressedFollow = follow },
                                onUnfollowConfirm = { unfollowTarget = it }
                            )
                        }
                    }
                }
            }

            if (backToTopButtonVisible(isAtTop = isAtTop, hasItems = displayedFollows.isNotEmpty())) {
                val metrics = backToTopButtonMetrics()
                BackToTopButton(
                    onClick = {
                        onRevealBottomBar()
                        if (useTabletTwoColumnLayout) {
                            scope.launch {
                                liveListState.animateScrollToItem(0)
                            }
                            scope.launch {
                                inactiveListState.animateScrollToItem(0)
                            }
                        } else {
                            scope.launch {
                                gridState.animateScrollToItem(0)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = metrics.endPaddingDp.dp, bottom = metrics.bottomPaddingDp.dp)
                )
            }
        } // PullToRefreshBox
    }

    // Unfollow confirm dialog
    unfollowTarget?.let { follow ->
        AlertDialog(
            onDismissRequest = { unfollowTarget = null },
            title = { Text("取消关注") },
            text = { Text("确定要取消关注 ${follow.userName} 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFollow(follow)
                    unfollowTarget = null
                }) { Text("删除", color = Color(0xFFFF3B30)) }
            },
            dismissButton = {
                TextButton(onClick = { unfollowTarget = null }) { Text("取消") }
            }
        )
    }

    // Dialog: Set tag for followed user
    longPressedFollow?.let { follow ->
        val tags = userTags
        var selectedTag by remember(follow) {
            mutableStateOf(tags.find { follow.id in it.userIds })
        }
        AlertDialog(
            onDismissRequest = { longPressedFollow = null },
            title = { Text("设置标签 - ${follow.userName}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // "全部" (Clear Tag)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTag = null }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTag == null,
                            onClick = { selectedTag = null }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("全部 (无标签)")
                    }
                    // Custom tags
                    tags.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTag = tag }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTag?.id == tag.id,
                                onClick = { selectedTag = tag }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tag.tag)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setFollowTag(follow, selectedTag)
                    longPressedFollow = null
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { longPressedFollow = null }) { Text("取消") }
            }
        )
    }

    // Dialog: Tags Manager
    if (showTagsManagerDialog) {
        val tags = userTags
        AlertDialog(
            onDismissRequest = { showTagsManagerDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("标签管理", modifier = Modifier.weight(1f))
                    IconButton(onClick = { showAddTagDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加标签")
                    }
                }
            },
            text = {
                if (tags.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无自定义标签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        tags.forEach { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showRenameTagDialog = tag }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tag.tag,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                IconButton(onClick = { showRenameTagDialog = tag }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "重命名",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { viewModel.removeTag(tag.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagsManagerDialog = false }) { Text("关闭") }
            }
        )
    }

    // Dialog: Add Tag
    if (showAddTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("添加标签") },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("标签名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newTagName.isNotBlank(),
                    onClick = {
                        viewModel.addTag(newTagName.trim())
                        showAddTagDialog = false
                    }
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) { Text("取消") }
            }
        )
    }

    // Dialog: Rename Tag
    showRenameTagDialog?.let { tag ->
        var renameTagName by remember(tag) { mutableStateOf(tag.tag) }
        AlertDialog(
            onDismissRequest = { showRenameTagDialog = null },
            title = { Text("修改标签名称") },
            text = {
                OutlinedTextField(
                    value = renameTagName,
                    onValueChange = { renameTagName = it },
                    label = { Text("标签名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameTagName.isNotBlank(),
                    onClick = {
                        viewModel.renameTag(tag.id, renameTagName.trim())
                        showRenameTagDialog = null
                    }
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameTagDialog = null }) { Text("取消") }
            }
        )
    }

    // Export dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出关注列表") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = exportedJson,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 300.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("follows", exportedJson))
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    showExportDialog = false
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("关闭") }
            }
        )
    }

    // Import dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("从文本导入") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("粘贴 JSON 数据") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 250.dp),
                    maxLines = 10
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (importText.isNotBlank()) {
                        scope.launch {
                            try {
                                viewModel.importFollows(importText)
                                Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                                showImportDialog = false
                                importText = ""
                            } catch (e: Exception) {
                                Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false; importText = "" }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FollowTabletPlatformBar(
    options: List<FollowTabletPlatformOption>,
    selectedSiteId: String?,
    tags: List<FollowUserTagEntity> = emptyList(),
    selectedTagId: String? = null,
    onSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(options, key = { it.id }) { option ->
                CompactFilterPill(
                    text = "${option.title} ${option.count}",
                    selected = option.siteId == selectedSiteId,
                    onClick = { onSelected(option.siteId) },
                    horizontalPaddingDp = 14,
                    fontSizeSp = 13
                )
            }
        }
        if (tags.isNotEmpty() || selectedTagId != null) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                item(key = "tag:all") {
                    CompactFilterPill(
                        text = "全部标签",
                        selected = selectedTagId == null,
                        onClick = { onTagSelected(null) },
                        horizontalPaddingDp = 12,
                        fontSizeSp = 12
                    )
                }
                items(tags, key = { "tag:${it.id}" }) { tag ->
                    CompactFilterPill(
                        text = "${tag.tag} ${tag.userIds.size}",
                        selected = selectedTagId == tag.id,
                        onClick = { onTagSelected(tag.id) },
                        horizontalPaddingDp = 12,
                        fontSizeSp = 12
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowTabletTwoColumnContent(
    columns: FollowTabletStatusColumns,
    liveListState: LazyListState,
    inactiveListState: LazyListState,
    contentBottomPadding: Dp,
    navigator: Navigator,
    viewModel: FollowViewModel,
    onLongClick: (FollowUserEntity) -> Unit,
    onUnfollowConfirm: (FollowUserEntity) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = contentBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FollowTabletStatusColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            title = stringResource(R.string.follow_group_live),
            emptyMessage = "暂无直播中",
            count = columns.live.size,
            isLive = true,
            follows = columns.live,
            listState = liveListState,
            navigator = navigator,
            viewModel = viewModel,
            onLongClick = onLongClick,
            onUnfollowConfirm = onUnfollowConfirm
        )
        FollowTabletStatusColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            title = stringResource(R.string.follow_group_offline),
            emptyMessage = "暂无未开播",
            count = columns.inactive.size,
            isLive = false,
            follows = columns.inactive,
            listState = inactiveListState,
            navigator = navigator,
            viewModel = viewModel,
            onLongClick = onLongClick,
            onUnfollowConfirm = onUnfollowConfirm
        )
    }
}

@Composable
private fun FollowTabletStatusColumn(
    modifier: Modifier = Modifier,
    title: String,
    emptyMessage: String,
    count: Int,
    isLive: Boolean,
    follows: List<FollowUserEntity>,
    listState: LazyListState,
    navigator: Navigator,
    viewModel: FollowViewModel,
    onLongClick: (FollowUserEntity) -> Unit,
    onUnfollowConfirm: (FollowUserEntity) -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isLive) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Color(0xFF34C759), CircleShape)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (follows.isEmpty()) {
            EmptyState(message = emptyMessage, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(follows, key = { it.id }) { follow ->
                    SwipeableFollowItem(
                        follow = follow,
                        viewModel = viewModel,
                        navigator = navigator,
                        cardHorizontalPadding = 0.dp,
                        onLongClick = { onLongClick(follow) },
                        onUnfollowConfirm = onUnfollowConfirm
                    )
                }
            }
        }
    }
}

/**
 * 可左滑的关注用户卡片。
 * 左滑固定距离，露出圆角胶囊式"收藏"与"删除"按钮。
 */
@Composable
private fun SwipeableFollowItem(
    follow: FollowUserEntity,
    viewModel: FollowViewModel,
    navigator: Navigator,
    cardHorizontalPadding: Dp = 16.dp,
    onLongClick: () -> Unit,
    onUnfollowConfirm: (FollowUserEntity) -> Unit
) {
    val density = LocalDensity.current
    val actionButtonWidth = 64.dp
    val actionButtonGap = 6.dp
    val cardToTrayGap = 8.dp
    val trayTotalWidth = actionButtonWidth * 2 + actionButtonGap + cardToTrayGap
    val refreshEntry = rememberFollowRefreshEntry(viewModel.refreshEntries, follow.id)
    val totalReveal = with(density) { trayTotalWidth.toPx() }
    val cardMetrics = followCompactCardMetrics()
    val cardCornerShape = RoundedCornerShape(14.dp)
    val actionPillShape = RoundedCornerShape(13.dp)
    val offsetX = remember(follow.id) { Animatable(0f) }
    val motionPolicy = LocalAppMotionPolicy.current
    val actionScope = rememberCoroutineScope()
    val animatedOffset = offsetX.value
    SideEffect {
        offsetX.updateBounds(lowerBound = -totalReveal, upperBound = 0f)
    }
    val actionsAlpha = followSwipeActionAlpha(animatedOffset)
    val revealProgress = if (totalReveal > 0f) (-animatedOffset / totalReveal).coerceIn(0f, 1f) else 0f
    val actionsEnabled = actionsAlpha > 0f

    fun closeSwipeActions() {
        actionScope.launch {
            if (motionPolicy.reducedMotion) {
                offsetX.snapTo(0f)
            } else {
                offsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = AppMotion.momentumSpec()
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = cardHorizontalPadding,
                vertical = cardMetrics.cardVerticalPaddingDp.dp
            )
            .clip(cardCornerShape)
    ) {
        // 底层：iOS 风格圆角胶囊操作区（与卡片等高对齐、无溢出漏色）
        Row(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = actionsAlpha * (0.35f + 0.65f * revealProgress)
                },
            horizontalArrangement = Arrangement.spacedBy(actionButtonGap, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isFavorited = follow.isSpecialFollow
            Surface(
                onClick = {
                    if (actionsEnabled) {
                        viewModel.toggleSpecialFollow(follow)
                        closeSwipeActions()
                    }
                },
                enabled = actionsEnabled,
                shape = actionPillShape,
                color = if (isFavorited) Color(0xFF8E8E93) else Color(0xFFFF9500),
                modifier = Modifier
                    .width(actionButtonWidth)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isFavorited) Icons.Default.StarOff else Icons.Default.StarFilled,
                        contentDescription = if (isFavorited) "取消收藏" else "收藏",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (isFavorited) "取消收藏" else "收藏",
                        color = Color.White,
                        fontSize = if (isFavorited) 10.5.sp else 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Surface(
                onClick = {
                    if (actionsEnabled) {
                        onUnfollowConfirm(follow)
                        closeSwipeActions()
                    }
                },
                enabled = actionsEnabled,
                shape = actionPillShape,
                color = Color(0xFFFF3B30),
                modifier = Modifier
                    .width(actionButtonWidth)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "删除",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 上层：主播卡片，可左滑
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(follow.id, totalReveal, motionPolicy.reducedMotion) {
                    coroutineScope {
                        var velocityTracker = VelocityTracker()
                        fun settle(velocityX: Float) {
                            val target = followSwipeSettledOffset(
                                offsetX = offsetX.value,
                                totalReveal = totalReveal,
                                velocityX = velocityX
                            )
                            launch {
                                if (motionPolicy.reducedMotion) {
                                    offsetX.snapTo(target)
                                } else {
                                    offsetX.animateTo(
                                        targetValue = target,
                                        animationSpec = AppMotion.momentumSpec(),
                                        initialVelocity = velocityX
                                    )
                                }
                            }
                        }

                        detectHorizontalDragGestures(
                            onDragStart = {
                                velocityTracker = VelocityTracker()
                                launch { offsetX.stop() }
                            },
                            onDragEnd = {
                                settle(velocityTracker.calculateVelocity().x)
                            },
                            onDragCancel = {
                                settle(0f)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                velocityTracker.addPosition(
                                    change.uptimeMillis,
                                    change.position
                                )
                                launch {
                                    val newOffset = (offsetX.value + dragAmount)
                                        .coerceIn(-totalReveal, 0f)
                                    offsetX.snapTo(newOffset)
                                }
                            }
                        )
                    }
                }
        ) {
            FollowUserItem(
                userName = follow.userName,
                faceUrl = follow.face,
                liveStatus = follow.liveStatus,
                refreshHint = followRefreshHint(refreshEntry),
                siteId = follow.siteId,
                showTime = follow.showTime,
                isSpecialFollow = follow.isSpecialFollow,
                avatarSize = cardMetrics.avatarSizeDp.dp,
                cardHorizontalPadding = 0.dp,
                cardVerticalPadding = 0.dp,
                contentPadding = cardMetrics.contentPaddingDp.dp,
                horizontalGap = cardMetrics.horizontalGapDp.dp,
                liveBadgeSize = 10.dp,
                playIconSize = 22.dp,
                cornerRadius = 14.dp,
                onClick = {
                    // 如果按钮已展开，点击收起；否则进入直播间
                    if (offsetX.value < -10f) {
                        closeSwipeActions()
                    } else {
                        navigator.navigateToRoom(
                            siteId = follow.siteId,
                            roomId = follow.playbackRoomId,
                            initialIsFollowing = true
                        )
                    }
                },
                onLongClick = onLongClick
            )
        }
    }
}

@Composable
private fun CompactGroupModeMenu(
    modifier: Modifier = Modifier,
    groupMode: FollowGroupMode,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onModeSelected: (FollowGroupMode) -> Unit,
    pillHorizontalPaddingDp: Int = 12,
    fontSizeSp: Int = 13,
    iconSizeDp: Int = 16
) {
    Box(modifier = modifier) {
        CompactFilterPill(
            modifier = Modifier.fillMaxWidth(),
            text = followCompactGroupModeLabel(groupMode),
            selected = true,
            onClick = { onExpandedChange(true) },
            showDropdownIcon = true,
            horizontalPaddingDp = pillHorizontalPaddingDp,
            fontSizeSp = fontSizeSp,
            iconSizeDp = iconSizeDp
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            listOf(
                FollowGroupMode.STATUS,
                FollowGroupMode.PLATFORM,
                FollowGroupMode.TAG
            ).forEach { mode ->
                DropdownMenuItem(
                    text = { Text(followCompactGroupModeLabel(mode)) },
                    onClick = {
                        onExpandedChange(false)
                        onModeSelected(mode)
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactFilterPill(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    showDropdownIcon: Boolean = false,
    horizontalPaddingDp: Int = 12,
    fontSizeSp: Int = 13,
    iconSizeDp: Int = 16
) {
    val shape = RoundedCornerShape(12.dp)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPaddingDp.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                color = contentColor,
                fontSize = fontSizeSp.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
            if (showDropdownIcon) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(iconSizeDp.dp)
                )
            }
        }
    }
}

internal data class FollowCompactCardMetrics(
    val avatarSizeDp: Int,
    val contentPaddingDp: Int,
    val horizontalGapDp: Int,
    val cardVerticalPaddingDp: Int
)

internal fun followCompactCardMetrics(): FollowCompactCardMetrics {
    return FollowCompactCardMetrics(
        avatarSizeDp = 40,
        contentPaddingDp = 8,
        horizontalGapDp = 8,
        cardVerticalPaddingDp = 3
    )
}

internal fun followCardGridColumns(useSideNavigation: Boolean): Int {
    return if (useSideNavigation) 2 else 1
}

internal data class FollowTabletStatusColumns(
    val live: List<FollowUserEntity>,
    val inactive: List<FollowUserEntity>
)

internal data class FollowStatusBuckets(
    val live: List<FollowUserEntity>,
    val unknown: List<FollowUserEntity>,
    val offline: List<FollowUserEntity>
)

internal data class FollowTabletPlatformOption(
    val id: String,
    val title: String,
    val siteId: String?,
    val count: Int
)

internal fun followUseTabletTwoColumnLayout(followCardColumns: Int): Boolean {
    return followCardColumns >= 2
}

internal fun followTabletStatusColumns(follows: List<FollowUserEntity>): FollowTabletStatusColumns {
    return FollowTabletStatusColumns(
        live = follows.filter { it.liveStatus == 1 },
        inactive = follows.filter { it.liveStatus != 1 }
    )
}

internal fun followStatusBuckets(follows: List<FollowUserEntity>): FollowStatusBuckets {
    return FollowStatusBuckets(
        live = follows.filter { it.liveStatus == 1 },
        unknown = follows.filter { it.liveStatus == 0 },
        offline = follows.filter { it.liveStatus == 2 }
    )
}

internal fun followTabletPlatformOptions(follows: List<FollowUserEntity>): List<FollowTabletPlatformOption> {
    val siteOrder = listOf("bilibili", "douyu", "huya", "douyin")
    val counts = follows.groupingBy { it.siteId }.eachCount()
    val sortedSiteIds = counts.keys.sortedWith(
        compareBy<String> {
            val index = siteOrder.indexOf(it)
            if (index >= 0) index else siteOrder.size
        }.thenBy { it }
    )

    return buildList {
        add(
            FollowTabletPlatformOption(
                id = "all",
                title = "全部",
                siteId = null,
                count = follows.size
            )
        )
        sortedSiteIds.forEach { siteId ->
            add(
                FollowTabletPlatformOption(
                    id = "site:$siteId",
                    title = followTabletPlatformTitle(siteId),
                    siteId = siteId,
                    count = counts.getValue(siteId)
                )
            )
        }
    }
}

internal fun followTabletPlatformTitle(siteId: String): String {
    return when (siteId) {
        "bilibili" -> "B站"
        "douyu" -> "斗鱼"
        "huya" -> "虎牙"
        "douyin" -> "抖音"
        else -> siteId
    }
}

internal fun followCompactGroupModeLabel(mode: FollowGroupMode): String {
    return when (mode) {
        FollowGroupMode.STATUS -> "状态"
        FollowGroupMode.PLATFORM -> "平台"
        FollowGroupMode.TAG -> "标签"
    }
}

internal fun followCompactFilterOptionsScrollable(mode: FollowGroupMode): Boolean {
    return mode != FollowGroupMode.PLATFORM
}

internal fun followCompactModeSelectorWidthDp(): Int {
    return 64
}

internal data class FollowCompactPlatformFilterMetrics(
    val horizontalPaddingDp: Int,
    val itemGapDp: Int,
    val pillHorizontalPaddingDp: Int,
    val fontSizeSp: Int,
    val iconSizeDp: Int
)

internal fun followCompactPlatformFilterMetrics(): FollowCompactPlatformFilterMetrics {
    return FollowCompactPlatformFilterMetrics(
        horizontalPaddingDp = 16,
        itemGapDp = 8,
        pillHorizontalPaddingDp = 6,
        fontSizeSp = 12,
        iconSizeDp = 14
    )
}

internal fun followSwipeSettledOffset(
    offsetX: Float,
    totalReveal: Float,
    velocityX: Float = 0f
): Float {
    if (totalReveal <= 0f) return 0f
    val projectedOffset = AppMotion.projectedEndpoint(
        currentValue = offsetX,
        velocity = velocityX
    )
    return if (projectedOffset < -totalReveal / 3f) -totalReveal else 0f
}

internal fun followSwipeActionAlpha(offsetX: Float): Float {
    return if (offsetX < -1f) 1f else 0f
}

@Composable
private fun FollowGroupHeader(
    title: String,
    count: Int,
    isLive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isLive) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(Color(0xFF34C759), shape = CircleShape)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = if (isLive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
