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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mylive.app.R
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.ui.component.FollowUserItem
import com.mylive.app.ui.component.status.EmptyState
import com.mylive.app.ui.navigation.Navigator
import com.mylive.app.ui.navigation.navigateToRoom
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowScreen(
    navigator: Navigator,
    refreshSignal: Int = 0,
    viewModel: FollowViewModel = hiltViewModel()
) {
    val filteredFollows by viewModel.filteredFollows.collectAsState()
    val groupMode by viewModel.groupMode.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val groupOptions by viewModel.groupOptions.collectAsState()
    val userTags by viewModel.userTags.collectAsState()

    val updatingStatus by viewModel.updatingStatus.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
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
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
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
        // Compact Premium Header (No TopAppBar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_follow),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.weight(1f))

            Box {
                IconButton(onClick = { showMenu = true }) {
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

        PullToRefreshBox(
            isRefreshing = updatingStatus,
            onRefresh = { viewModel.updateFollowStatus() },
            modifier = Modifier.fillMaxSize()
        ) {

            if (filteredFollows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(message = stringResource(R.string.follow_empty))
                }
            } else {
                // Group by live status
                val liveList = filteredFollows.filter { it.liveStatus == 1 }
                val unknownList = filteredFollows.filter { it.liveStatus == 0 }
                val offlineList = filteredFollows.filter { it.liveStatus == 2 }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    // 直播中
                    if (liveList.isNotEmpty()) {
                        item {
                            FollowGroupHeader(
                                title = stringResource(R.string.follow_group_live),
                                count = liveList.size,
                                isLive = true
                            )
                        }
                        items(liveList, key = { it.id }) { follow ->
                            SwipeableFollowItem(
                                follow = follow,
                                viewModel = viewModel,
                                navigator = navigator,
                                onLongClick = { longPressedFollow = follow },
                                onUnfollowConfirm = { unfollowTarget = it }
                            )
                        }
                    }

                    // 未知
                    if (unknownList.isNotEmpty()) {
                        item {
                            FollowGroupHeader(
                                title = stringResource(R.string.follow_group_unknown),
                                count = unknownList.size
                            )
                        }
                        items(unknownList, key = { it.id }) { follow ->
                            SwipeableFollowItem(
                                follow = follow,
                                viewModel = viewModel,
                                navigator = navigator,
                                onLongClick = { longPressedFollow = follow },
                                onUnfollowConfirm = { unfollowTarget = it }
                            )
                        }
                    }

                    // 未开播
                    if (offlineList.isNotEmpty()) {
                        item {
                            FollowGroupHeader(
                                title = stringResource(R.string.follow_group_offline),
                                count = offlineList.size
                            )
                        }
                        items(offlineList, key = { it.id }) { follow ->
                            SwipeableFollowItem(
                                follow = follow,
                                viewModel = viewModel,
                                navigator = navigator,
                                onLongClick = { longPressedFollow = follow },
                                onUnfollowConfirm = { unfollowTarget = it }
                            )
                        }
                    }
                }
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
                }) { Text("确定", color = MaterialTheme.colorScheme.error) }
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

/**
 * 可左滑的关注用户卡片。
 * 左滑固定距离，露出"置顶"和"取关"两个按钮。
 */
@Composable
private fun SwipeableFollowItem(
    follow: FollowUserEntity,
    viewModel: FollowViewModel,
    navigator: Navigator,
    onLongClick: () -> Unit,
    onUnfollowConfirm: (FollowUserEntity) -> Unit
) {
    val density = LocalDensity.current
    val actionButtonWidth = 72.dp
    val buttonWidth = with(density) { actionButtonWidth.toPx() }
    val totalReveal = buttonWidth * 2 // 两个按钮的总宽度
    val cardMetrics = followCompactCardMetrics()
    var offsetX by remember(follow.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(follow.id) { mutableStateOf(false) }
    // 拖动中直接跟手，松手后再平滑吸附，避免手势滞后造成阻力感。
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = if (isDragging) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "swipeOffset"
    )
    val actionsAlpha = followSwipeActionAlpha(animatedOffset)
    val actionsEnabled = actionsAlpha > 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        // 底层：操作按钮
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clipToBounds()
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(actionButtonWidth * 2)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = actionsAlpha },
                horizontalArrangement = Arrangement.End
            ) {
                // 置顶/取消置顶按钮
                Surface(
                    modifier = Modifier
                        .width(actionButtonWidth)
                        .fillMaxHeight()
                        .clickable(enabled = actionsEnabled) {
                            viewModel.toggleSpecialFollow(follow)
                            offsetX = 0f
                        },
                    color = Color(0xFFFF9F00)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = if (follow.isSpecialFollow) "取消置顶" else "置顶",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (follow.isSpecialFollow) "取消置顶" else "置顶",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                // 取关按钮
                Surface(
                    modifier = Modifier
                        .width(actionButtonWidth)
                        .fillMaxHeight()
                        .clickable(enabled = actionsEnabled) {
                            onUnfollowConfirm(follow)
                            offsetX = 0f
                        },
                    color = MaterialTheme.colorScheme.error
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "取关",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "取关",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // 上层：卡片，可左滑
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(follow.id) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            offsetX = followSwipeSettledOffset(offsetX, totalReveal)
                        },
                        onDragCancel = {
                            isDragging = false
                            offsetX = followSwipeSettledOffset(offsetX, totalReveal)
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            // 只允许左滑（负方向）
                            val newOffset = (offsetX + dragAmount).coerceIn(-totalReveal, 0f)
                            offsetX = newOffset
                        }
                    )
                }
        ) {
            FollowUserItem(
                userName = follow.userName,
                faceUrl = follow.face,
                liveStatus = follow.liveStatus,
                siteId = follow.siteId,
                showTime = follow.showTime,
                isSpecialFollow = follow.isSpecialFollow,
                avatarSize = cardMetrics.avatarSizeDp.dp,
                cardVerticalPadding = cardMetrics.cardVerticalPaddingDp.dp,
                contentPadding = cardMetrics.contentPaddingDp.dp,
                horizontalGap = cardMetrics.horizontalGapDp.dp,
                liveBadgeSize = 10.dp,
                playIconSize = 22.dp,
                cornerRadius = 14.dp,
                onClick = {
                    // 如果按钮已展开，点击关闭；否则进入直播间
                    if (offsetX < -10f) {
                        offsetX = 0f
                    } else {
                        navigator.navigateToRoom(
                            siteId = follow.siteId,
                            roomId = follow.roomId
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
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick),
        shape = shape,
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

internal fun followSwipeSettledOffset(offsetX: Float, totalReveal: Float): Float {
    if (totalReveal <= 0f) return 0f
    return if (offsetX < -totalReveal / 3f) -totalReveal else 0f
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLive) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(MaterialTheme.colorScheme.error, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = if (isLive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
