package com.mylive.app.ui.screen.room

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mylive.app.ui.theme.Icons
import com.mylive.app.ui.theme.livePlatformOnAccentColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import com.mylive.app.ui.layout.LocalAppLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mylive.app.ui.navigation.Navigator
import coil3.compose.AsyncImage
import com.mylive.app.R
import com.mylive.app.core.common.buildLiveMessageDisplaySpans
import com.mylive.app.core.common.normalizeLiveMessageDisplaySpans
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.core.model.LiveMessageSpan
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.data.repository.LiveRoomPreferences
import com.mylive.app.ui.emoji.EmojiImage
import com.mylive.app.ui.screen.room.player.LiveDanmakuSurfaceFeed
import com.mylive.app.ui.screen.room.player.LivePlaybackHostContent
import com.mylive.app.ui.screen.room.player.DanmakuConfig
import com.mylive.app.ui.screen.room.player.shouldPrepareLivePlaybackExit
import com.mylive.app.data.local.entity.playbackRoomId
import com.mylive.app.core.model.DanmakuSettingsPolicy
import com.mylive.app.ui.component.FollowRefreshProgressLine
import com.mylive.app.ui.component.followRefreshHint
import com.mylive.app.ui.component.rememberFollowRefreshEntry
import com.mylive.app.ui.screen.room.player.PlayerDanmakuActions
import com.mylive.app.ui.screen.room.player.PlayerController
import com.mylive.app.ui.screen.room.player.PlayerView
import com.mylive.app.ui.screen.room.player.PlayerViewActions
import com.mylive.app.ui.screen.room.player.PlayerViewUiState
import com.mylive.app.ui.screen.room.player.DanmakuController
import com.mylive.app.ui.screen.room.player.rememberLivePlaybackHost
import com.mylive.app.ui.screen.room.player.shouldFeedLiveDanmakuSurface
import com.mylive.app.ui.screen.room.player.shouldFeedPipDanmakuSurface
import com.mylive.app.ui.screen.room.player.playbackContainerAspectRatio
import com.mylive.app.ui.screen.settings.SettingsViewModel
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clipToBounds
import com.mylive.app.ui.screen.follow.FollowViewModel
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.navigation.NavigationExitEffect
import com.mylive.app.ui.screen.room.quickaccess.QuickAccessExtraTab
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicLong


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUserItem(
    item: FollowUserEntity,
    isPlaying: Boolean,
    onTap: () -> Unit,
    refreshHint: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        onClick = onTap
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with live border if live
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(
                        width = 1.5.dp,
                        color = if (item.liveStatus == 1) Color.Red else Color.LightGray.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .padding(2.dp)
            ) {
                AsyncImage(
                    model = item.face,
                    contentDescription = item.userName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.userName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Site badge
                    val siteColor = when (item.siteId) {
                        "bilibili" -> Color(0xFFFB7299)
                        "douyu" -> Color(0xFFFF5D23)
                        "huya" -> Color(0xFFFF9F00)
                        "douyin" -> Color(0xFF000000)
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    val siteName = when (item.siteId) {
                        "bilibili" -> "B站"
                        "douyu" -> "斗鱼"
                        "huya" -> "虎牙"
                        "douyin" -> "抖音"
                        else -> item.siteId
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = siteColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, siteColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = siteName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = siteColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                if (item.siteId != "douyin") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "房间号: ${item.roomId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                refreshHint?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            // Live status indicator badge
            val statusText = when (item.liveStatus) { 1 -> "直播中"; 2 -> "未开播"; else -> "待更新" }
            val statusColor = if (item.liveStatus == 1) Color.Red else Color.Gray
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = statusColor.copy(alpha = 0.1f),
                border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// ── Follow List Panel ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListPanel(
    navigator: Navigator,
    currentRoomId: String,
    currentSiteId: String,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    viewModel: FollowViewModel = hiltViewModel()
) {
    val follows by viewModel.follows.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.updatingStatus.collectAsStateWithLifecycle()
    val refreshProgress by viewModel.refreshProgress.collectAsStateWithLifecycle()
    var filterIndex by remember { mutableIntStateOf(0) } // 0: 全部, 1: 直播中, 2: 未开播

    val filteredFollows = remember(follows, filterIndex) {
        when (filterIndex) {
            1 -> follows.filter { it.liveStatus == 1 }
            2 -> follows.filter { it.liveStatus == 2 }
            else -> follows
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        // Filter chips + refresh (manual only; do not auto-refresh on open).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val options = listOf("全部", "直播中", "未开播")
            options.forEachIndexed { index, text ->
                val selected = filterIndex == index
                FilterChip(
                    selected = selected,
                    onClick = { filterIndex = index },
                    label = { Text(text) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { viewModel.updateFollowStatus() },
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新关注状态"
                    )
                }
            }
        }

        FollowRefreshProgressLine(refreshProgress)
        if (filteredFollows.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isRefreshing) "刷新中…" else "暂无数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = filteredFollows,
                    key = { it.id },
                    contentType = { "room_follow" }
                ) { item ->
                    val isPlaying = (item.roomId == currentRoomId || item.playbackRoomId == currentRoomId) && item.siteId == currentSiteId
                    FollowUserItem(
                        item = item,
                        isPlaying = isPlaying,
                        refreshHint = followRefreshHint(rememberFollowRefreshEntry(viewModel.refreshEntries, item.id)),
                        onTap = {
                            navigator.navigate(
                                Route.LiveRoomDetail(
                                    roomId = item.playbackRoomId,
                                    siteId = item.siteId,
                                    initialIsFollowing = true,
                                    initialFullscreen = isFullscreen
                                ),
                                popUpToRoute = Route.LiveRoomDetail::class.java,
                                inclusive = true
                            )
                        }
                    )
                }
            }
        }
    }
}

// ── Room Settings Panel ──────────────────────────────────────────

