package com.mylive.app.ui.screen.room

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clipToBounds
import com.mylive.app.ui.screen.follow.FollowViewModel
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.navigation.NavigationExitEffect
import com.mylive.app.ui.screen.room.quickaccess.QuickAccessExtraTab
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.motion.expandCollapseEnter
import com.mylive.app.ui.motion.expandCollapseExit
import com.mylive.app.ui.motion.horizontalContentTransform
import com.mylive.app.ui.motion.popoverEnter
import com.mylive.app.ui.motion.popoverExit
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicLong

@Composable
internal fun CompactPortraitRoomHeader(
    detail: com.mylive.app.core.model.LiveRoomDetail?,
    isFollowing: Boolean,
    isFollowStatusKnown: Boolean,
    accentColor: Color,
    onToggleFollow: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onQuickAccessClick: (() -> Unit)?
) {
    val toolbarIconTint = resolveLiveRoomToolbarIconTint(MaterialTheme.colorScheme.onSurface)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (detail == null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(15.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            } else {
                Box(modifier = Modifier.size(34.dp)) {
                    AsyncImage(
                        model = detail.userAvatar,
                        contentDescription = detail.userName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    if (detail.status) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                                .padding(1.5.dp)
                                .background(Color(0xFF34C759), CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.userName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(onClick = onRefreshClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    tint = toolbarIconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = toolbarIconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (onQuickAccessClick != null) {
                IconButton(onClick = onQuickAccessClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "快速入口",
                        tint = toolbarIconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            val canToggleFollow = detail != null && isFollowStatusKnown
            LiveRoomFollowButton(
                isFollowing = isFollowing,
                accentColor = accentColor,
                enabled = canToggleFollow,
                onClick = onToggleFollow,
                modifier = Modifier.size(36.dp),
                iconSizeDp = 18
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
internal fun LiveRoomFollowButton(
    isFollowing: Boolean,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSizeDp: Int
) {
    val motionPolicy = LocalAppMotionPolicy.current
    IconButton(
        onClick = {
            if (enabled) {
                onClick()
            }
        },
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = isFollowing,
            transitionSpec = {
                if (motionPolicy.reducedMotion) {
                    fadeIn(motionPolicy.effectsSpec()) togetherWith fadeOut(motionPolicy.effectsSpec())
                } else {
                    (scaleIn(motionPolicy.contentSpec(), initialScale = 0.78f) + fadeIn(motionPolicy.effectsSpec())) togetherWith
                        fadeOut(motionPolicy.effectsSpec())
                }
            },
            label = "liveRoomFollowHeart"
        ) { isFollowing ->
            Icon(
                imageVector = if (isFollowing) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFollowing) {
                    stringResource(R.string.room_following)
                } else {
                    stringResource(R.string.room_follow)
                },
                tint = accentColor,
                modifier = Modifier.size(iconSizeDp.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PortraitAuxiliaryPanelSheet(
    panel: PortraitLiveRoomPanel,
    uiState: LiveRoomUiState,
    viewModel: LiveRoomViewModel,
    navigator: Navigator,
    settingsViewModel: SettingsViewModel,
    liveRoomPreferences: LiveRoomPreferences,
    superChatSortDesc: Boolean,
    isFullscreen: Boolean = false,
    onDismiss: () -> Unit
) {
    val windowHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    val maxSheetHeight = windowHeight * 0.72f
    val isSettings = panel == PortraitLiveRoomPanel.SETTINGS
    var handleDismissAllowed by remember { mutableStateOf(false) }
    var handleGestureActive by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> !isSettings || target != SheetValue.Hidden || handleDismissAllowed }
    )
    LaunchedEffect(isSettings, handleGestureActive, handleDismissAllowed) {
        if (!isSettings || handleGestureActive || !handleDismissAllowed) return@LaunchedEffect
        withFrameNanos { }
        val settled = snapshotFlow { sheetState.currentValue to sheetState.targetValue }
            .first { (current, target) -> current == target }
        if (settled.first == SheetValue.Expanded) handleDismissAllowed = false
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = if (panel == PortraitLiveRoomPanel.SETTINGS) {
            {
                RoomSettingsSheetDragHandle(
                    onDownwardDragStarted = { handleDismissAllowed = true },
                    onGestureActiveChange = { handleGestureActive = it },
                    onDismiss = onDismiss
                )
            }
        } else {
            { BottomSheetDefaults.DragHandle() }
        }
    ) {
        BackHandler(onBack = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isSettings) Modifier.height(maxSheetHeight) else Modifier.heightIn(max = maxSheetHeight))
                .navigationBarsPadding()
                .then(if (isSettings) Modifier.roomPanelContentScrollIsolation().testTag("room_settings_sheet") else Modifier)
        ) {
            if (!isSettings) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 2.dp, end = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (panel) {
                        PortraitLiveRoomPanel.SUPER_CHAT -> if (viewModel.siteId == "huya") "头条" else "SC"
                        PortraitLiveRoomPanel.FOLLOW -> "关注列表"
                        PortraitLiveRoomPanel.SETTINGS -> "设置"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                when (panel) {
                    PortraitLiveRoomPanel.SUPER_CHAT -> {
                        SuperChatPanel(
                            viewModel = viewModel,
                            superChatSortDesc = superChatSortDesc,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    PortraitLiveRoomPanel.FOLLOW -> {
                        FollowListPanel(
                            navigator = navigator,
                            currentRoomId = viewModel.roomId,
                            currentSiteId = viewModel.siteId,
                            isFullscreen = isFullscreen,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    PortraitLiveRoomPanel.SETTINGS -> {
                        RoomSettingsPanel(
                            viewModel = settingsViewModel,
                            liveRoomPreferences = liveRoomPreferences,
                            isHuyaOrBilibili = viewModel.siteId == "huya" || viewModel.siteId == "bilibili",
                            siteId = viewModel.siteId,
                            onOpenShieldSettings = { navigator.navigate(Route.SettingsDanmuShield) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomSettingsSheetDragHandle(
    onDownwardDragStarted: () -> Unit,
    onGestureActiveChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(50.dp)
            .roomPanelDismissHandleGesture(onDownwardDragStarted, onGestureActiveChange)
            .semantics {
                contentDescription = "下滑关闭设置"
                dismiss { onDismiss(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)))
        Text("设置", Modifier.align(Alignment.CenterStart).padding(start = 20.dp, top = 6.dp),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp, top = 6.dp)) {
            Icon(Icons.Default.Close, "关闭设置", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
// ── Quality Bottom Sheet ────────────────────────────────────────
