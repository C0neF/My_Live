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
import coil.compose.AsyncImage
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


@Composable
internal fun LandscapeLayout(
    uiState: LiveRoomUiState,
    viewModel: LiveRoomViewModel,
    accentSiteId: String,
    navigator: Navigator,
    playerController: PlayerController?,
    activity: Activity?,
    settingsViewModel: SettingsViewModel,
    liveRoomPreferences: LiveRoomPreferences,
    onQualityClick: () -> Unit,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    routeActive: Boolean = true,
    onControlsLockChange: (Boolean) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val danmuEnable = liveRoomPreferences.danmuEnable
    val danmuSize = liveRoomPreferences.danmuSize
    val danmuSpeed = liveRoomPreferences.danmuSpeed
    val danmuArea = liveRoomPreferences.danmuArea
    val danmuLineCount = liveRoomPreferences.danmuLineCount
    val danmuDelay = liveRoomPreferences.danmuDelay
    val danmuDelayBySiteJson = liveRoomPreferences.danmuDelayBySiteJson
    val danmuOpacity = liveRoomPreferences.danmuOpacity
    val danmuFontWeight = liveRoomPreferences.danmuFontWeight
    val danmuStrokeWidth = liveRoomPreferences.danmuStrokeWidth
    val danmuTopMargin = liveRoomPreferences.danmuTopMargin
    val danmuBottomMargin = liveRoomPreferences.danmuBottomMargin
    val danmuHideScroll = liveRoomPreferences.danmuHideScroll
    val danmuHideTop = liveRoomPreferences.danmuHideTop
    val danmuHideBottom = liveRoomPreferences.danmuHideBottom
    val danmuDedupeEnable = liveRoomPreferences.danmuDedupeEnable
    val danmuDedupeWindow = liveRoomPreferences.danmuDedupeWindow
    val danmuDedupeStep = liveRoomPreferences.danmuDedupeStep
    val danmuDedupeStrictMode = liveRoomPreferences.danmuDedupeStrictMode
    val scaleMode = liveRoomPreferences.scaleMode
    val playerCompatMode = liveRoomPreferences.playerCompatMode
    val danmuRenderEmoji = liveRoomPreferences.danmuRenderEmoji
    val chatTextSize = liveRoomPreferences.chatTextSize
    val chatTextGap = liveRoomPreferences.chatTextGap
    val chatBubbleStyle = liveRoomPreferences.chatBubbleStyle
    val liveRoomQuickAccessEnabled = liveRoomPreferences.liveRoomQuickAccessEnabled

    val roomTabs = remember { resolveLandscapeLiveRoomTabs() }
    val resolvedDanmuDelay = remember(danmuDelay, danmuDelayBySiteJson, viewModel.siteId) {
        resolveDanmuDelayMs(
            globalDelayMs = danmuDelay.toInt(),
            delayBySiteJson = danmuDelayBySiteJson,
            siteId = viewModel.siteId
        )
    }
    val roomPlatformAccentColor = resolveLiveRoomPlatformAccentColor(
        siteId = accentSiteId,
        defaultAccentColor = MaterialTheme.colorScheme.primary
    )
    var showQuickAccess by remember { mutableStateOf(false) }
    var quickAccessInitialTabKey by remember { mutableStateOf<String?>(null) }
    val quickAccessAction = liveRoomQuickAccessAction(liveRoomQuickAccessEnabled) {
        quickAccessInitialTabKey = null
        showQuickAccess = true
    }

    LaunchedEffect(liveRoomQuickAccessEnabled) {
        if (!liveRoomQuickAccessEnabled) {
            showQuickAccess = false
        }
    }

    if (showQuickAccess) {
        CompositionLocalProvider(LocalRoomAccentColor provides roomPlatformAccentColor) {
            com.mylive.app.ui.screen.room.quickaccess.QuickAccessPanel(
                currentSiteId = viewModel.siteId,
                currentRoomId = viewModel.roomId,
                currentCategoryId = uiState.detail?.categoryId,
                currentResolvedRoomId = uiState.detail?.roomId,
                extraTabs = landscapeQuickAccessExtraTabs(
                    settingsViewModel = settingsViewModel,
                    liveRoomPreferences = liveRoomPreferences,
                    viewModel = viewModel,
                    navigator = navigator
                ),
                initialSelectedKey = quickAccessInitialTabKey,
                onNavigateToRoom = { siteId, roomId, initialIsFollowing ->
                    showQuickAccess = false
                    navigator.navigate(
                        Route.LiveRoomDetail(
                            roomId = roomId,
                            siteId = siteId,
                            initialIsFollowing = initialIsFollowing,
                            initialFullscreen = isFullscreen
                        ),
                        singleTop = true,
                        popUpToRoute = Route.Index::class.java,
                        inclusive = false
                    )
                },
                onDismiss = { showQuickAccess = false }
            )
        }
    }

    val density = LocalDensity.current
    val panelWidthPx = remember(density) { with(density) { 320.dp.toPx() } }
    val panelOffset = remember(panelWidthPx) {
        Animatable(liveRoomInitialSidePanelOffsetPx(panelWidthPx))
    }
    SideEffect {
        panelOffset.updateBounds(lowerBound = 0f, upperBound = panelWidthPx)
    }
    val coroutineScope = rememberCoroutineScope()
    val motionPolicy = LocalAppMotionPolicy.current
    val showSidePanel = panelOffset.value < panelWidthPx
    val closeSidePanel: () -> Unit = {
        coroutineScope.launch {
            if (motionPolicy.reducedMotion) {
                panelOffset.snapTo(panelWidthPx)
            } else {
                panelOffset.animateTo(
                    targetValue = panelWidthPx,
                    animationSpec = AppMotion.momentumSpec(),
                    initialVelocity = 0f
                )
            }
        }
    }

    var danmakuController by remember { mutableStateOf<DanmakuController?>(null) }

    LiveDanmakuSurfaceFeed(
        messages = viewModel.newDanmakuMessages,
        playbackState = playerController?.state,
        controller = danmakuController,
        active = shouldFeedLiveDanmakuSurface(
            hasController = danmakuController != null,
            enabled = danmuEnable,
            routeActive = routeActive
        )
    )

    ImmersiveLiveRoomLayout(
        panelFraction = 1f - panelOffset.value / panelWidthPx,
        onDismissPanel = closeSidePanel,
        player = { playerModifier ->
        // Player (takes remaining space)
        Box(
            modifier = playerModifier.background(Color.Black)
        ) {
            val currentQualityName = uiState.playQualities.getOrNull(uiState.currentQualityIndex)?.quality ?: "画质"
            PlayerView(
                playerController = playerController,
                viewState = PlayerViewUiState(
                    title = uiState.detail?.title ?: "",
                    currentQualityName = currentQualityName,
                    danmakuEnabled = danmuEnable,
                    danmaku = DanmakuConfig(
                        size = danmuSize,
                        speed = danmuSpeed,
                        area = danmuArea,
                        lineCount = danmuLineCount,
                        delayMs = resolvedDanmuDelay,
                        opacity = danmuOpacity,
                        fontWeight = danmuFontWeight,
                        strokeWidth = danmuStrokeWidth,
                        topMargin = danmuTopMargin,
                        bottomMargin = danmuBottomMargin,
                        hideScroll = danmuHideScroll,
                        hideTop = danmuHideTop,
                        hideBottom = danmuHideBottom,
                        dedupeEnable = danmuDedupeEnable,
                        dedupeWindow = danmuDedupeWindow,
                        dedupeStep = danmuDedupeStep,
                        dedupeStrictMode = danmuDedupeStrictMode,
                        renderEmoji = danmuRenderEmoji
                    ),
                    accentColor = roomPlatformAccentColor,
                    scaleMode = scaleMode,
                    playerCompatMode = playerCompatMode,
                    chatBubbleStyle = chatBubbleStyle,
                    fullscreenOverride = isFullscreen,
                    isFollowing = uiState.isFollowing,
                    followEnabled = uiState.detail != null && uiState.isFollowStatusKnown
                ),
                actions = PlayerViewActions(
                    onBack = onBack,
                    onQualityClick = onQualityClick,
                    onRefreshClick = viewModel::refreshPlay,
                    danmaku = PlayerDanmakuActions(
                        onEnabledChange = settingsViewModel::setDanmuEnable,
                        onSizeChange = settingsViewModel::setDanmuSize,
                        onSpeedChange = settingsViewModel::setDanmuSpeed,
                        onAreaChange = settingsViewModel::setDanmuArea,
                        onOpacityChange = settingsViewModel::setDanmuOpacity,
                        onChatBubbleStyleChange = settingsViewModel::setChatBubbleStyle,
                        onControllerCreated = { danmakuController = it },
                        onControllerReleased = { released ->
                            if (danmakuController === released) danmakuController = null
                        }
                    ),
                    onFullscreenClick = onToggleFullscreen,
                    onQuickAccessClick = quickAccessAction,
                    onFollowClick = viewModel::toggleFollow,
                    onControlsLockChange = onControlsLockChange,
                    onHorizontalDragDelta = { deltaX ->
                        coroutineScope.launch {
                            val newOffset = liveRoomSidePanelOffsetAfterDrag(
                                currentOffsetPx = panelOffset.value,
                                deltaX = deltaX,
                                panelWidthPx = panelWidthPx
                            )
                            panelOffset.snapTo(newOffset)
                        }
                    },
                    onHorizontalDragEnd = { velocityX ->
                        coroutineScope.launch {
                            val targetOffset = liveRoomSidePanelSettledOffset(
                                currentOffsetPx = panelOffset.value,
                                velocityX = velocityX,
                                panelWidthPx = panelWidthPx
                            )
                            if (motionPolicy.reducedMotion) {
                                panelOffset.snapTo(targetOffset)
                            } else {
                                panelOffset.animateTo(
                                    targetValue = targetOffset,
                                    animationSpec = AppMotion.momentumSpec(),
                                    initialVelocity = velocityX
                                )
                            }
                        }
                    }
                ),
                modifier = Modifier.fillMaxSize()
            )
        }

        },
        panel = { panelModifier ->
            Box(
                modifier = panelModifier
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(0.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    LandscapeRoomSidePanelHeader(
                        detail = uiState.detail
                    )

                    if (roomTabs.contains(LiveRoomTabType.CHAT)) {
                        ChatPanel(
                            messagesFlow = viewModel.danmakuMessages,
                            noticeFlow = viewModel.danmakuNotice,
                            hostName = uiState.detail?.userName ?: "",
                            chatTextSize = chatTextSize,
                            chatTextGap = chatTextGap,
                            chatBubbleStyle = chatBubbleStyle,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    )
    // Register after the player's fullscreen back handler so the open chat panel closes first.
    BackHandler(enabled = showSidePanel, onBack = closeSidePanel)
}

private fun landscapeQuickAccessExtraTabs(
    settingsViewModel: SettingsViewModel,
    liveRoomPreferences: LiveRoomPreferences,
    viewModel: LiveRoomViewModel,
    navigator: Navigator
): List<QuickAccessExtraTab> {
    return listOf(
        QuickAccessExtraTab(
            key = "room_settings",
            label = "设置",
            icon = Icons.Default.Settings,
            content = {
                RoomSettingsPanel(
                    viewModel = settingsViewModel,
                    liveRoomPreferences = liveRoomPreferences,
                    isHuyaOrBilibili = viewModel.siteId == "huya" || viewModel.siteId == "bilibili",
                    siteId = viewModel.siteId,
                    onOpenShieldSettings = { navigator.navigate(Route.SettingsDanmuShield) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        )
    )
}

// ── Room Info Bar ───────────────────────────────────────────────

@Composable
private fun LandscapeRoomSidePanelHeader(
    detail: com.mylive.app.core.model.LiveRoomDetail?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (detail == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = detail.userAvatar,
                    contentDescription = detail.userName,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = detail.userName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

