package com.mylive.app.ui.screen.room.quickaccess

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.model.LiveRoomItem
import com.mylive.app.core.model.LiveSubCategory
import com.mylive.app.core.site.LiveSite
import com.mylive.app.ui.component.NetImage
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.HistoryRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.ui.theme.Icons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── QuickAccess ViewModel ───────────────────────────────────────────────────

data class QuickAccessExtraTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int? = null,
    val content: @Composable () -> Unit
)

@HiltViewModel
class QuickAccessViewModel @Inject constructor(
    private val followRepository: FollowRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val sites: Set<@JvmSuppressWildcards LiveSite>
) : ViewModel() {

    val follows: StateFlow<List<FollowUserEntity>> = followRepository.getAllFollows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = historyRepository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickAccessSort: StateFlow<String> = settingsRepository.liveRoomQuickAccessSort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "follow,history,recommendation")

    val quickAccessEnabled: StateFlow<Boolean> = settingsRepository.liveRoomQuickAccessEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun removeHistory(id: String) {
        viewModelScope.launch { historyRepository.removeHistory(id) }
    }

    fun getSiteById(siteId: String): LiveSite? = sites.find { it.id == siteId }

    // Recommendation state
    private val _recommendations = MutableStateFlow<List<LiveRoomItem>>(emptyList())
    val recommendations: StateFlow<List<LiveRoomItem>> = _recommendations.asStateFlow()

    private val _recLoading = MutableStateFlow(false)
    val recLoading: StateFlow<Boolean> = _recLoading.asStateFlow()

    private var recPage = 1

    fun loadRecommendations(siteId: String, categoryId: String?, reset: Boolean = false) {
        if (categoryId == null) return
        val site = getSiteById(siteId) ?: return
        viewModelScope.launch {
            _recLoading.value = true
            try {
                if (reset) {
                    recPage = 1
                    _recommendations.value = emptyList()
                }
                val cat = LiveSubCategory(id = categoryId, name = "", parentId = "")
                val result = site.getCategoryRooms(cat, recPage)
                _recommendations.value = if (reset) result.items else _recommendations.value + result.items
                recPage++
            } catch (_: Exception) {
                // Silently fail for recommendations
            } finally {
                _recLoading.value = false
            }
        }
    }
}

// ─── Main QuickAccess Panel ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessPanel(
    currentSiteId: String,
    currentRoomId: String,
    currentCategoryId: String?,
    extraTabs: List<QuickAccessExtraTab> = emptyList(),
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit,
    onDismiss: () -> Unit,
    viewModel: QuickAccessViewModel = hiltViewModel()
) {
    val sortStr by viewModel.quickAccessSort.collectAsStateWithLifecycle()
    val enabled by viewModel.quickAccessEnabled.collectAsStateWithLifecycle()

    if (!enabled) {
        onDismiss()
        return
    }

    val extraTabsByKey = remember(extraTabs) {
        extraTabs.associateBy { it.key }
    }

    val orderedKeys = remember(sortStr, extraTabs) {
        val default = listOf("follow", "history", "recommendation")
        val keys = sortStr.split(",").filter { it.isNotBlank() }
        val defaultKeys = (keys + default).distinct()
        val extraKeys = extraTabs.map { it.key }
        (extraKeys + defaultKeys.filterNot { it in extraKeys }).distinct()
    }

    val tabLabels = mapOf(
        "follow" to "关注",
        "history" to "历史",
        "recommendation" to "推荐"
    )

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { orderedKeys.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(orderedKeys.size) {
        if (pagerState.currentPage > orderedKeys.lastIndex) {
            pagerState.scrollToPage(orderedKeys.lastIndex.coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            QuickAccessIslandTabBar(
                orderedKeys = orderedKeys,
                selectedTab = pagerState.currentPage.coerceIn(0, orderedKeys.lastIndex.coerceAtLeast(0)),
                extraTabsByKey = extraTabsByKey,
                tabLabels = tabLabels,
                onSelectedTabChange = { index ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = 500.dp)
            ) { page ->
                Box(modifier = Modifier.fillMaxSize()) {
                    val selectedKey = orderedKeys.getOrNull(page)
                    val extraTab = extraTabsByKey[selectedKey]
                    if (extraTab != null) {
                        extraTab.content()
                    } else {
                        when (selectedKey) {
                            "follow" -> FollowQuickPanel(
                                viewModel = viewModel,
                                onNavigateToRoom = onNavigateToRoom
                            )
                            "history" -> HistoryQuickPanel(
                                viewModel = viewModel,
                                onNavigateToRoom = onNavigateToRoom
                            )
                            "recommendation" -> RecommendationQuickPanel(
                                viewModel = viewModel,
                                currentSiteId = currentSiteId,
                                currentRoomId = currentRoomId,
                                currentCategoryId = currentCategoryId,
                                onNavigateToRoom = onNavigateToRoom
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAccessIslandTabBar(
    orderedKeys: List<String>,
    selectedTab: Int,
    extraTabsByKey: Map<String, QuickAccessExtraTab>,
    tabLabels: Map<String, String>,
    onSelectedTabChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            orderedKeys.forEachIndexed { index, key ->
                val extraTab = extraTabsByKey[key]
                val label = when {
                    extraTab?.badgeCount != null -> "${extraTab.label} ${extraTab.badgeCount}"
                    extraTab != null -> extraTab.label
                    else -> tabLabels[key] ?: key
                }
                val icon = extraTab?.icon ?: when (key) {
                    "follow" -> Icons.Default.Favorite
                    "history" -> Icons.Default.History
                    "recommendation" -> Icons.Default.Category
                    else -> Icons.Default.Info
                }
                QuickAccessIslandTab(
                    selected = selectedTab == index,
                    label = label,
                    icon = icon,
                    onClick = { onSelectedTabChange(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickAccessIslandTab(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        }
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )

    Column(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Follow Quick Panel ──────────────────────────────────────────────────────

@Composable
private fun FollowQuickPanel(
    viewModel: QuickAccessViewModel,
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit
) {
    val follows by viewModel.follows.collectAsStateWithLifecycle()
    var filterIndex by remember { mutableIntStateOf(0) }

    val filteredFollows = remember(follows, filterIndex) {
        when (filterIndex) {
            1 -> follows.filter { it.liveStatus == 1 }
            2 -> follows.filter { it.liveStatus == 2 }
            else -> follows
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("全部", "直播中", "未开播").forEachIndexed { index, label ->
                FilterChip(
                    selected = filterIndex == index,
                    onClick = { filterIndex = index },
                    label = { Text(label) },
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        if (filteredFollows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无关注", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredFollows, key = { it.id }) { user ->
                    FollowQuickItem(
                        user = user,
                        onClick = { onNavigateToRoom(user.siteId, user.roomId, true) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun FollowQuickItem(user: FollowUserEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetImage(
            url = user.face,
            modifier = Modifier.size(40.dp),
            isCircle = true
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.userName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = user.siteId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val statusColor = when (user.liveStatus) {
            1 -> MaterialTheme.colorScheme.primary
            2 -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        }
        val statusText = when (user.liveStatus) {
            1 -> "直播中"
            2 -> "未开播"
            else -> "未知"
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── History Quick Panel ─────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryQuickPanel(
    viewModel: QuickAccessViewModel,
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无观看记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(history, key = { it.id }) { item ->
                HistoryQuickItem(
                    item = item,
                    onClick = { onNavigateToRoom(item.siteId, item.roomId, null) },
                    onLongClick = { deleteConfirmId = item.id }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    if (deleteConfirmId != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("删除记录") },
            text = { Text("确定要删除这条观看记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeHistory(deleteConfirmId!!)
                    deleteConfirmId = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryQuickItem(item: HistoryEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetImage(
            url = item.face,
            modifier = Modifier.size(40.dp),
            isCircle = true
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.userName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.siteId} · ${formatRelativeTime(item.updateTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Recommendation Quick Panel ──────────────────────────────────────────────

@Composable
private fun RecommendationQuickPanel(
    viewModel: QuickAccessViewModel,
    currentSiteId: String,
    currentRoomId: String,
    currentCategoryId: String?,
    onNavigateToRoom: (siteId: String, roomId: String, initialIsFollowing: Boolean?) -> Unit
) {
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val loading by viewModel.recLoading.collectAsStateWithLifecycle()

    LaunchedEffect(currentSiteId, currentCategoryId) {
        viewModel.loadRecommendations(currentSiteId, currentCategoryId, reset = true)
    }

    val filtered = remember(recommendations, currentRoomId) {
        recommendations.filter { it.roomId != currentRoomId }
    }

    if (currentCategoryId == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("无法获取当前分类信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (filtered.isEmpty() && !loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无同类推荐", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.roomId }) { room ->
                    RecommendationQuickItem(
                        room = room,
                        onClick = { onNavigateToRoom(currentSiteId, room.roomId, null) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            TextButton(onClick = {
                                viewModel.loadRecommendations(currentSiteId, currentCategoryId)
                            }) {
                                Text("加载更多")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationQuickItem(room: LiveRoomItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetImage(
            url = room.cover,
            modifier = Modifier
                .width(108.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp)),
            size = 0.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = room.userName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (room.online > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${room.online}人观看",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        minutes < 1440 -> "${minutes / 60}小时前"
        minutes < 43200 -> "${minutes / 1440}天前"
        else -> "${minutes / 43200}月前"
    }
}
