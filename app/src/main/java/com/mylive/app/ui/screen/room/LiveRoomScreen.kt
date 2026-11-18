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


enum class LiveRoomTabType {
    CHAT, SUPER_CHAT, FOLLOW, SETTINGS
}

enum class RoomSettingsSectionKey {
    PLAYER_DANMAKU,
    LIVE_BEHAVIOR,
    CHAT,
    SHIELD,
    FILTER
}

internal fun liveRoomUiStateForRoute(
    uiState: LiveRoomUiState,
    viewModelRoomId: String,
    viewModelSiteId: String,
    routeRoomId: String,
    routeSiteId: String,
    routeInitialIsFollowing: Boolean? = null
): LiveRoomUiState {
    val routeSite = routeSiteId.trim()
    val isSameRoom = viewModelRoomId.trim() == routeRoomId.trim()
    val isSameSite = routeSite.isEmpty() || viewModelSiteId.trim() == routeSite
    return if (isSameRoom && isSameSite) {
        uiState
    } else {
        loadingLiveRoomUiState(routeInitialIsFollowing)
    }
}

internal fun canShowLiveRoomFollowButton(
    hasDetail: Boolean,
    isFollowStatusKnown: Boolean
): Boolean {
    return isFollowStatusKnown
}

internal fun defaultExpandedRoomSettingsSections(): Set<RoomSettingsSectionKey> {
    return setOf(RoomSettingsSectionKey.PLAYER_DANMAKU)
}

internal fun liveRoomInitialSidePanelOffsetPx(panelWidthPx: Float): Float {
    return panelWidthPx
}

internal fun liveRoomSidePanelOffsetAfterDrag(
    currentOffsetPx: Float,
    deltaX: Float,
    panelWidthPx: Float
): Float {
    return (currentOffsetPx + deltaX).coerceIn(0f, panelWidthPx)
}

internal fun liveRoomSidePanelSettledOffset(
    currentOffsetPx: Float,
    velocityX: Float,
    panelWidthPx: Float
): Float {
    if (panelWidthPx <= 0f) return 0f
    val projectedOffset = AppMotion.projectedEndpoint(
        currentValue = currentOffsetPx,
        velocity = velocityX
    )
    return if (projectedOffset < panelWidthPx * 0.7f) 0f else panelWidthPx
}

internal fun liveRoomTabIndex(roomTabs: List<LiveRoomTabType>, target: LiveRoomTabType): Int {
    return roomTabs.indexOf(target).coerceAtLeast(0)
}

internal fun resolveLandscapeLiveRoomTabs(): List<LiveRoomTabType> {
    return listOf(LiveRoomTabType.CHAT)
}

internal fun liveRoomQuickAccessAction(
    enabled: Boolean,
    action: () -> Unit
): (() -> Unit)? = if (enabled) action else null

val LocalRoomAccentColor = compositionLocalOf { Color.Unspecified }

private fun currentEpochMillis(): Long = System.currentTimeMillis()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRoomScreen(
    navigator: Navigator,
    key: Route.LiveRoomDetail
) {
    val viewModel: LiveRoomViewModel = hiltViewModel()
    LaunchedEffect(key.roomId, key.siteId, key.initialIsFollowing) {
        viewModel.openRoute(key.roomId, key.siteId, key.initialIsFollowing)
    }

    val rawUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uiState = liveRoomUiStateForRoute(
        uiState = rawUiState,
        viewModelRoomId = viewModel.roomId,
        viewModelSiteId = viewModel.siteId,
        routeRoomId = key.roomId,
        routeSiteId = key.siteId,
        routeInitialIsFollowing = key.initialIsFollowing
    )
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val activity = context as? Activity
    val portraitPlayerResizeState = rememberPortraitPlayerResizeState(key.siteId to key.roomId)

    val navigation = navigator.rememberRouteActions()
    val routeActive = navigation.isCurrent
    var isControlsLocked by remember { mutableStateOf(false) }
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val liveRoomPreferences by settingsViewModel.liveRoomPreferences.collectAsStateWithLifecycle()
    val scaleMode = liveRoomPreferences.scaleMode
    val playerCompatMode = liveRoomPreferences.playerCompatMode
    val pipHideDanmu = liveRoomPreferences.pipHideDanmu
    val chatBubbleStyle = liveRoomPreferences.chatBubbleStyle
    val pipDanmuEnable = liveRoomPreferences.danmuEnable
    val pipDanmuSize = liveRoomPreferences.danmuSize
    val pipDanmuSpeed = liveRoomPreferences.danmuSpeed
    val pipDanmuArea = liveRoomPreferences.danmuArea
    val pipDanmuLineCount = liveRoomPreferences.danmuLineCount
    val pipDanmuDelay = liveRoomPreferences.danmuDelay
    val pipDanmuDelayBySiteJson = liveRoomPreferences.danmuDelayBySiteJson
    val pipDanmuOpacity = liveRoomPreferences.danmuOpacity
    val pipDanmuFontWeight = liveRoomPreferences.danmuFontWeight
    val pipDanmuStrokeWidth = liveRoomPreferences.danmuStrokeWidth
    val pipDanmuTopMargin = liveRoomPreferences.danmuTopMargin
    val pipDanmuBottomMargin = liveRoomPreferences.danmuBottomMargin
    val pipDanmuHideScroll = liveRoomPreferences.danmuHideScroll
    val pipDanmuHideTop = liveRoomPreferences.danmuHideTop
    val pipDanmuHideBottom = liveRoomPreferences.danmuHideBottom
    val pipDanmuDedupeEnable = liveRoomPreferences.danmuDedupeEnable
    val pipDanmuDedupeWindow = liveRoomPreferences.danmuDedupeWindow
    val pipDanmuDedupeStep = liveRoomPreferences.danmuDedupeStep
    val pipDanmuDedupeStrictMode = liveRoomPreferences.danmuDedupeStrictMode
    val pipDanmuRenderEmoji = liveRoomPreferences.danmuRenderEmoji

    var pipDanmakuController by remember { mutableStateOf<DanmakuController?>(null) }
    val resolvedPipDanmuDelay = remember(pipDanmuDelay, pipDanmuDelayBySiteJson, viewModel.siteId) {
        resolveDanmuDelayMs(
            globalDelayMs = pipDanmuDelay.toInt(),
            delayBySiteJson = pipDanmuDelayBySiteJson,
            siteId = viewModel.siteId
        )
    }
    val accentSiteId = resolveLiveRoomAccentSiteId(
        routeSiteId = key.siteId,
        viewModelSiteId = viewModel.siteId
    )
    val roomPlatformAccentColor = resolveLiveRoomPlatformAccentColor(
        siteId = accentSiteId,
        defaultAccentColor = MaterialTheme.colorScheme.primary
    )

    val playbackHost = rememberLivePlaybackHost(
        content = LivePlaybackHostContent(
            routeRoomId = key.roomId,
            routeSiteId = key.siteId,
            roomReady = uiState.detail != null,
            title = uiState.detail?.title ?: "",
            anchorName = uiState.detail?.userName ?: "",
            platform = viewModel.siteId,
            initialFullscreen = key.initialFullscreen
        ),
        preferences = liveRoomPreferences,
        controlsLocked = isControlsLocked,
        exiting = !routeActive,
        binding = viewModel,
        settingsRepository = viewModel.settingsRepository,
        onPlaybackSourceExhausted = viewModel::recoverPlaybackAfterSourceFailure,
        keepWindowOnDispose = { navigator.backStack.lastOrNull() is Route.LiveRoomDetail }
    )
    val playerController = playbackHost.playerController
    val isInPip = playbackHost.isInPictureInPicture
    val isFullscreen = playbackHost.isFullscreen
    val roomLayout = resolveLiveRoomLayout(LocalAppLayout.current, isLandscape, isFullscreen)

    LiveDanmakuSurfaceFeed(
        messages = viewModel.newDanmakuMessages,
        playbackState = playerController?.state,
        controller = pipDanmakuController,
        active = shouldFeedPipDanmakuSurface(
            isInPip = isInPip,
            danmuEnable = pipDanmuEnable,
            pipHideDanmu = pipHideDanmu,
            routeActive = routeActive
        ),
        clearWhenInactive = true
    )

    // Room idle auto exit state
    val roomAutoExitDuration = liveRoomPreferences.roomAutoExitDuration
    val lastInteractionTime = remember { AtomicLong(System.currentTimeMillis()) }

    NavigationExitEffect(navigator = navigator, route = key) {
        if (shouldPrepareLivePlaybackExit(navigator.backStack.lastOrNull())) {
            playbackHost.prepareToLeave()
        }
    }
    val handleBack = navigation.onBack

    val currentHandleBack by rememberUpdatedState(handleBack)
    LaunchedEffect(roomAutoExitDuration) {
        if (roomAutoExitDuration <= 0) return@LaunchedEffect
        val timeoutMillis = roomAutoExitDuration * 60 * 1000L
        while (true) {
            val idleMillis = System.currentTimeMillis() - lastInteractionTime.get()
            val remainingMillis = timeoutMillis - idleMillis
            if (remainingMillis <= 0) {
                currentHandleBack()
                break
            }
            kotlinx.coroutines.delay(remainingMillis.coerceAtMost(1000L))
        }
    }

    // Quality selection bottom sheet (non-fullscreen portrait only)
    var showQualitySheet by remember { mutableStateOf(false) }
    LaunchedEffect(isFullscreen, roomLayout) {
        if (isFullscreen || roomLayout == LiveRoomLayout.Immersive) {
            showQualitySheet = false
        }
    }

    if (showQualitySheet && uiState.playQualities.isNotEmpty() && !isFullscreen && roomLayout != LiveRoomLayout.Immersive) {
        QualityBottomSheet(
            qualities = uiState.playQualities,
            currentIndex = uiState.currentQualityIndex,
            onSelect = { index ->
                viewModel.switchQuality(index)
                showQualitySheet = false
            },
            onDismiss = { showQualitySheet = false }
        )
    }



    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_live_room")
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        lastInteractionTime.set(System.currentTimeMillis())
                    }
                }
            }
    ) {
        if (isInPip) {
            val currentQualityName = uiState.playQualities.getOrNull(uiState.currentQualityIndex)?.quality ?: "画质"
            PlayerView(
                playerController = playerController,
                viewState = PlayerViewUiState(
                    title = uiState.detail?.title ?: "",
                    currentQualityName = currentQualityName,
                    danmakuEnabled = pipDanmuEnable && !pipHideDanmu,
                    danmaku = DanmakuConfig(
                        size = pipDanmuSize,
                        speed = pipDanmuSpeed,
                        area = pipDanmuArea,
                        lineCount = pipDanmuLineCount,
                        delayMs = resolvedPipDanmuDelay,
                        opacity = pipDanmuOpacity,
                        fontWeight = pipDanmuFontWeight,
                        strokeWidth = pipDanmuStrokeWidth,
                        topMargin = pipDanmuTopMargin,
                        bottomMargin = pipDanmuBottomMargin,
                        hideScroll = pipDanmuHideScroll,
                        hideTop = pipDanmuHideTop,
                        hideBottom = pipDanmuHideBottom,
                        dedupeEnable = pipDanmuDedupeEnable,
                        dedupeWindow = pipDanmuDedupeWindow,
                        dedupeStep = pipDanmuDedupeStep,
                        dedupeStrictMode = pipDanmuDedupeStrictMode,
                        renderEmoji = pipDanmuRenderEmoji
                    ),
                    accentColor = roomPlatformAccentColor,
                    scaleMode = scaleMode,
                    playerCompatMode = playerCompatMode,
                    chatBubbleStyle = chatBubbleStyle,
                    fullscreenOverride = isFullscreen,
                    isInPictureInPicture = true
                ),
                actions = PlayerViewActions(
                    danmaku = PlayerDanmakuActions(
                        onChatBubbleStyleChange = settingsViewModel::setChatBubbleStyle,
                        onControllerCreated = { pipDanmakuController = it },
                        onControllerReleased = { released ->
                            if (pipDanmakuController === released) pipDanmakuController = null
                        }
                    ),
                    onFullscreenClick = { playbackHost.toggleFullscreen() }
                ),
                modifier = Modifier.fillMaxSize()
            )
        } else if (roomLayout == LiveRoomLayout.Immersive) {
            LandscapeLayout(
                uiState = uiState,
                viewModel = viewModel,
                accentSiteId = accentSiteId,
                navigator = navigator,
                playerController = playerController,
                activity = activity,
                settingsViewModel = settingsViewModel,
                liveRoomPreferences = liveRoomPreferences,
                onQualityClick = { showQualitySheet = true },
                isFullscreen = isFullscreen,
                onToggleFullscreen = playbackHost::toggleFullscreen,
                routeActive = routeActive,
                onControlsLockChange = { isControlsLocked = it },
                onBack = {
                    if (isFullscreen) {
                        playbackHost.toggleFullscreen()
                    } else {
                        handleBack()
                    }
                }
            )
        } else {
            PortraitLayout(
                tabletSplit = roomLayout == LiveRoomLayout.Split,
                videoAspectRatio = playbackHost.videoAspectRatio,
                playerResizeState = portraitPlayerResizeState,
                uiState = uiState,
                viewModel = viewModel,
                accentSiteId = accentSiteId,
                navigator = navigator,
                playerController = playerController,
                activity = activity,
                settingsViewModel = settingsViewModel,
                liveRoomPreferences = liveRoomPreferences,
                onQualityClick = { showQualitySheet = true },
                isFullscreen = isFullscreen,
                onToggleFullscreen = playbackHost::toggleFullscreen,
                routeActive = routeActive,
                onControlsLockChange = { isControlsLocked = it },
                onBack = handleBack
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitLayout(
    tabletSplit: Boolean,
    videoAspectRatio: Float?,
    playerResizeState: PortraitPlayerResizeState,
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
    val superChatSortDesc = liveRoomPreferences.superChatSortDesc
    val liveRoomQuickAccessEnabled = liveRoomPreferences.liveRoomQuickAccessEnabled

    val superChats by viewModel.superChats.collectAsStateWithLifecycle()
    val activeSuperChatCount = remember(superChats) {
        countActiveSuperChats(superChats, System.currentTimeMillis())
    }
    val portraitActions = remember(viewModel.siteId, activeSuperChatCount) {
        resolvePortraitLiveRoomActions(
            siteId = viewModel.siteId,
            superChatCount = activeSuperChatCount
        )
    }
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

    var danmakuController by remember { mutableStateOf<DanmakuController?>(null) }
    var showQuickAccess by remember { mutableStateOf(false) }
    var activeAuxiliaryPanel by remember { mutableStateOf<PortraitLiveRoomPanel?>(null) }
    val quickAccessAction = liveRoomQuickAccessAction(liveRoomQuickAccessEnabled) {
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
            currentCategoryParentId = uiState.detail?.categoryParentId,
            currentResolvedRoomId = uiState.detail?.roomId,
            extraTabs = portraitActions.mapNotNull { action ->
                if (action.panel != PortraitLiveRoomPanel.SUPER_CHAT) return@mapNotNull null
                QuickAccessExtraTab(
                    key = "room_${action.panel.name.lowercase()}",
                    label = action.label,
                    badgeCount = action.badgeCount,
                    icon = Icons.Default.Chat,
                    content = {
                        SuperChatPanel(
                            viewModel = viewModel,
                            superChatSortDesc = superChatSortDesc,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            },
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

    activeAuxiliaryPanel?.let { panel ->
        PortraitAuxiliaryPanelSheet(
            panel = panel,
            uiState = uiState,
            viewModel = viewModel,
            navigator = navigator,
            settingsViewModel = settingsViewModel,
            liveRoomPreferences = liveRoomPreferences,
            superChatSortDesc = superChatSortDesc,
            isFullscreen = isFullscreen,
            onDismiss = { activeAuxiliaryPanel = null }
        )
    }

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

    val playerAspectRatio = playbackContainerAspectRatio(videoAspectRatio, scaleMode)
    var tabletSidePanelCollapsed by rememberSaveable { mutableStateOf(false) }
    var tabletSelectedTab by rememberSaveable { mutableStateOf(LiveRoomTabType.CHAT) }
    val supportsSuperChat = viewModel.siteId == "bilibili" ||
        viewModel.siteId == "huya" ||
        activeSuperChatCount > 0
    LaunchedEffect(supportsSuperChat, tabletSelectedTab) {
        if (!supportsSuperChat && tabletSelectedTab == LiveRoomTabType.SUPER_CHAT) {
            tabletSelectedTab = LiveRoomTabType.CHAT
        }
    }

    AdaptiveLiveRoomLayout(
        splitPane = tabletSplit,
        chatWidthDp = LocalAppLayout.current.liveChatWidthDp,
        limitStackedPlayerHeight = LocalAppLayout.current.tablet,
        sidePanelCollapsed = tabletSplit && tabletSidePanelCollapsed,
        sidePanelHeader = if (tabletSplit) {
            {
                TabletSplitSidePanelHeader(
                    selectedTab = tabletSelectedTab,
                    supportsSuperChat = supportsSuperChat,
                    superChatCount = activeSuperChatCount,
                    accentColor = roomPlatformAccentColor,
                    onSelectTab = { tabletSelectedTab = it },
                    onCollapse = { tabletSidePanelCollapsed = true }
                )
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        videoAspectRatio = playerAspectRatio,
        playerResizeState = playerResizeState,
        player = { playerMaxHeight ->
        LiveRoomPlayerArea(
            maximumHeight = playerMaxHeight,
            modifier = if (tabletSplit) Modifier.weight(1f) else Modifier,
            fillAvailableHeight = tabletSplit,
            videoAspectRatio = playerAspectRatio
        ) {
            val currentQualityName = uiState.playQualities.getOrNull(uiState.currentQualityIndex)?.quality ?: "画质"
            PlayerView(
                playerController = playerController,
                viewState = PlayerViewUiState(
                    title = uiState.detail?.title ?: "",
                    currentQualityName = currentQualityName,
                    qualities = uiState.playQualities,
                    currentQualityIndex = uiState.currentQualityIndex,
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
                    fullscreenOverride = false
                ),
                actions = PlayerViewActions(
                    onBack = onBack,
                    onQualityClick = onQualityClick,
                    onQualitySelect = viewModel::switchQuality,
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
                    onFullscreenClick = { onToggleFullscreen?.invoke() ?: playerController?.toggleFullscreen() },
                    onControlsLockChange = onControlsLockChange
                ),
                modifier = Modifier.fillMaxSize()
            )
        }

        if (tabletSplit) {
            TabletSplitRoomStageBar(
                detail = uiState.detail,
                siteId = accentSiteId,
                onlineCountFlow = viewModel.onlineCount,
                isFollowing = uiState.isFollowing,
                isFollowStatusKnown = uiState.isFollowStatusKnown,
                accentColor = roomPlatformAccentColor,
                sidePanelCollapsed = tabletSidePanelCollapsed,
                superChatCount = activeSuperChatCount,
                onToggleFollow = { viewModel.toggleFollow() },
                onRefreshClick = { viewModel.refreshPlay() },
                onSettingsClick = { activeAuxiliaryPanel = PortraitLiveRoomPanel.SETTINGS },
                onQuickAccessClick = quickAccessAction,
                onToggleSidePanel = { tabletSidePanelCollapsed = !tabletSidePanelCollapsed }
            )
        } else {
            CompactPortraitRoomHeader(
                detail = uiState.detail,
                isFollowing = uiState.isFollowing,
                isFollowStatusKnown = uiState.isFollowStatusKnown,
                accentColor = roomPlatformAccentColor,
                onToggleFollow = { viewModel.toggleFollow() },
                onRefreshClick = { viewModel.refreshPlay() },
                onSettingsClick = { activeAuxiliaryPanel = PortraitLiveRoomPanel.SETTINGS },
                onQuickAccessClick = quickAccessAction
            )
        }

        },
        chat = { chatModifier ->
            if (tabletSplit) {
                val motionPolicy = LocalAppMotionPolicy.current
                val tabOrder = remember {
                    listOf(LiveRoomTabType.CHAT, LiveRoomTabType.SUPER_CHAT, LiveRoomTabType.FOLLOW)
                }
                AnimatedContent(
                    targetState = tabletSelectedTab,
                    transitionSpec = {
                        val dir = AppMotion.indexDirection(
                            tabOrder.indexOf(initialState),
                            tabOrder.indexOf(targetState)
                        )
                        horizontalContentTransform(dir, motionPolicy)
                    },
                    modifier = chatModifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
                    label = "tabletSplitSideTabContent"
                ) { activeTab ->
                    when (activeTab) {
                        LiveRoomTabType.SUPER_CHAT -> {
                            SuperChatPanel(
                                viewModel = viewModel,
                                superChatSortDesc = superChatSortDesc,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("live_room_super_chat")
                            )
                        }
                        LiveRoomTabType.FOLLOW -> {
                            FollowListPanel(
                                navigator = navigator,
                                currentRoomId = viewModel.roomId,
                                currentSiteId = viewModel.siteId,
                                isFullscreen = false,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("live_room_follow")
                            )
                        }
                        else -> {
                            ChatPanel(
                                messagesFlow = viewModel.danmakuMessages,
                                noticeFlow = viewModel.danmakuNotice,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("live_room_chat"),
                                hostName = uiState.detail?.userName ?: "",
                                chatTextSize = chatTextSize,
                                chatTextGap = chatTextGap,
                                chatBubbleStyle = chatBubbleStyle,
                                playerResizing = playerResizeState.isResizing
                            )
                        }
                    }
                }
            } else {
                ChatPanel(
                    messagesFlow = viewModel.danmakuMessages,
                    noticeFlow = viewModel.danmakuNotice,
                    modifier = chatModifier
                        .testTag("live_room_chat")
                        .background(MaterialTheme.colorScheme.background),
                    hostName = uiState.detail?.userName ?: "",
                    chatTextSize = chatTextSize,
                    chatTextGap = chatTextGap,
                    chatBubbleStyle = chatBubbleStyle,
                    playerResizing = playerResizeState.isResizing
                )
            }
        }
    )
}

@Composable
private fun LiveRoomTabPage(
    tabType: LiveRoomTabType?,
    uiState: LiveRoomUiState,
    viewModel: LiveRoomViewModel,
    navigator: Navigator,
    settingsViewModel: SettingsViewModel,
    liveRoomPreferences: LiveRoomPreferences,
    chatTextSize: Double,
    chatTextGap: Double,
    chatBubbleStyle: Boolean,
    superChatSortDesc: Boolean,
    isFullscreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    when (tabType) {
        LiveRoomTabType.CHAT -> {
            ChatPanel(
                messagesFlow = viewModel.danmakuMessages,
                noticeFlow = viewModel.danmakuNotice,
                modifier = modifier,
                hostName = uiState.detail?.userName ?: "",
                chatTextSize = chatTextSize,
                chatTextGap = chatTextGap,
                chatBubbleStyle = chatBubbleStyle
            )
        }
        LiveRoomTabType.SUPER_CHAT -> {
            SuperChatPanel(
                viewModel = viewModel,
                superChatSortDesc = superChatSortDesc,
                modifier = modifier
            )
        }
        LiveRoomTabType.FOLLOW -> {
            FollowListPanel(
                navigator = navigator,
                currentRoomId = viewModel.roomId,
                currentSiteId = viewModel.siteId,
                isFullscreen = isFullscreen,
                modifier = modifier
            )
        }
        LiveRoomTabType.SETTINGS -> {
            RoomSettingsPanel(
                viewModel = settingsViewModel,
                liveRoomPreferences = liveRoomPreferences,
                isHuyaOrBilibili = viewModel.siteId == "huya" || viewModel.siteId == "bilibili",
                siteId = viewModel.siteId,
                onOpenShieldSettings = { navigator.navigate(Route.SettingsDanmuShield) },
                modifier = modifier
            )
        }
        null -> {}
    }
}

@Composable
private fun TabletSplitRoomStageBar(
    detail: com.mylive.app.core.model.LiveRoomDetail?,
    siteId: String,
    onlineCountFlow: kotlinx.coroutines.flow.StateFlow<Int>,
    isFollowing: Boolean,
    isFollowStatusKnown: Boolean,
    accentColor: Color,
    sidePanelCollapsed: Boolean,
    superChatCount: Int,
    onToggleFollow: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onQuickAccessClick: (() -> Unit)?,
    onToggleSidePanel: () -> Unit
) {
    val toolbarIconTint = resolveLiveRoomToolbarIconTint(MaterialTheme.colorScheme.onSurface)
    val platformLabel = when (siteId.lowercase()) {
        "bilibili" -> "哔哩哔哩"
        "douyu" -> "斗鱼"
        "huya" -> "虎牙"
        "douyin" -> "抖音"
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (detail == null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(15.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    )
                }
            } else {
                Box(modifier = Modifier.size(36.dp)) {
                    AsyncImage(
                        model = detail.userAvatar,
                        contentDescription = detail.userName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    if (detail.status) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(10.dp),
                            shape = CircleShape,
                            color = Color(0xFF34C759),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {}
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.title.ifBlank { detail.userName },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (platformLabel.isNotEmpty()) "${detail.userName} · $platformLabel" else detail.userName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        RoomOnlineCountPill(onlineCountFlow = onlineCountFlow)
                    }
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

            Surface(
                onClick = onToggleSidePanel,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .height(32.dp)
                    .testTag("tablet_toggle_side_panel")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (sidePanelCollapsed) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                        contentDescription = if (sidePanelCollapsed) "展开侧栏" else "收起侧栏",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = buildString {
                            append(if (sidePanelCollapsed) "展开互动" else "影院宽屏")
                            if (sidePanelCollapsed && superChatCount > 0) append(" ($superChatCount)")
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomOnlineCountPill(onlineCountFlow: kotlinx.coroutines.flow.StateFlow<Int>) {
    val count by onlineCountFlow.collectAsStateWithLifecycle()
    if (count <= 0) return
    val text = if (count >= 10_000) String.format(java.util.Locale.ROOT, "%.1f万 人气", count / 10_000.0) else "$count 人气"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(5.dp)
                .background(Color(0xFF34C759), CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TabletSplitSidePanelHeader(
    selectedTab: LiveRoomTabType,
    supportsSuperChat: Boolean,
    superChatCount: Int,
    accentColor: Color,
    onSelectTab: (LiveRoomTabType) -> Unit,
    onCollapse: () -> Unit
) {
    val tabs = remember(supportsSuperChat) {
        buildList {
            add(LiveRoomTabType.CHAT to "弹幕")
            if (supportsSuperChat) add(LiveRoomTabType.SUPER_CHAT to "SC")
            add(LiveRoomTabType.FOLLOW to "关注")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val motionPolicy = LocalAppMotionPolicy.current
                tabs.forEach { (type, label) ->
                    val selected = selectedTab == type
                    val tabShape = RoundedCornerShape(8.dp)
                    val tabColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
                        animationSpec = motionPolicy.effectsSpec(),
                        label = "tabletRoomTabBg"
                    )
                    val tabTextColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = motionPolicy.effectsSpec(),
                        label = "tabletRoomTabText"
                    )
                    Surface(
                        onClick = { onSelectTab(type) },
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .testTag("tablet_room_tab_${type.name.lowercase()}"),
                        shape = tabShape,
                        color = tabColor
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = tabTextColor
                            )
                            if (type == LiveRoomTabType.SUPER_CHAT && superChatCount > 0) {
                                Text(
                                    text = "$superChatCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "收起侧栏",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LandscapeLayout(
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
                currentCategoryParentId = uiState.detail?.categoryParentId,
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
    val panelOffset = remember(panelWidthPx, LocalConfiguration.current.orientation) {
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
                    qualities = uiState.playQualities,
                    currentQualityIndex = uiState.currentQualityIndex,
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
                    onQualitySelect = viewModel::switchQuality,
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
            Row(
                modifier = panelModifier
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                VerticalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
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
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 11.dp),
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
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(15.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
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
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = detail.userName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

@Composable
private fun CompactPortraitRoomHeader(
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
private fun LiveRoomFollowButton(
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
private fun PortraitAuxiliaryPanelSheet(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityBottomSheet(
    qualities: List<LivePlayQuality>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.room_quality_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    qualities.forEachIndexed { index, quality ->
                        val isSelected = index == currentIndex
                        Surface(
                            onClick = { onSelect(index) },
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = quality.quality,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                        if (index < qualities.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Chat Panel ──────────────────────────────────────────────────

private const val ChatPanelMaxMessages = 200

@Composable
fun ChatPanel(
    messagesFlow: StateFlow<List<DisplayLiveMessage>>,
    noticeFlow: StateFlow<String?>,
    modifier: Modifier = Modifier,
    hostName: String = "",
    chatTextSize: Double = 14.0,
    chatTextGap: Double = 4.0,
    chatBubbleStyle: Boolean = false,
    playerResizing: Boolean = false
) {
    // Collect at the chat leaf so message snapshots do not recompose room chrome.
    val messages by messagesFlow.collectAsStateWithLifecycle()
    val notice by noticeFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var previousLastMessageId by remember { mutableStateOf<Long?>(null) }
    var autoScrollDisabled by remember { mutableStateOf(false) }
    var showLatestButton by remember { mutableStateOf(false) }
    var userTouchingChat by remember { mutableStateOf(false) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var displayMessages by remember { mutableStateOf<List<DisplayLiveMessage>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val isAtBottomNow by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            lastVisible == null || lastVisible.index >= info.totalItemsCount - 2
        }
    }
    // Key on the last message (not messages.size): once the ring buffer saturates at its cap,
    // size stays constant and a size-keyed effect would stop firing, freezing auto-scroll in
    // busy rooms. Auto-follow is controlled by user scroll state, matching dart_simple_live:
    // once the user scrolls away, new messages show the latest button instead of pulling back.
    val followViewportHeightPx = if (userTouchingChat) 0 else viewportHeightPx
    LaunchedEffect(messages.lastOrNull()?.id, messages.size, autoScrollDisabled, userTouchingChat, followViewportHeightPx) {
        val nextDisplayMessages = mergeChatDisplayMessages(
            currentDisplay = displayMessages,
            sourceMessages = messages,
            autoScrollDisabled = autoScrollDisabled,
            maxEnabledMessages = ChatPanelMaxMessages,
            keyOf = { it.id }
        )
        displayMessages = nextDisplayMessages

        if (nextDisplayMessages.isEmpty()) {
            previousLastMessageId = null
            autoScrollDisabled = false
            showLatestButton = false
            return@LaunchedEffect
        }

        val currentLastMessageId = messages.lastOrNull()?.id
        if (!userTouchingChat && shouldAutoScrollChat(
                autoScrollDisabled = autoScrollDisabled,
                hasMessages = nextDisplayMessages.isNotEmpty()
            )
        ) {
            showLatestButton = false
            withFrameNanos { }
            listState.scrollToItem(nextDisplayMessages.lastIndex)
        } else if (shouldShowLatestChatButton(
                previousLastMessageId = previousLastMessageId,
                currentLastMessageId = currentLastMessageId,
                autoScrollDisabled = autoScrollDisabled
            )
        ) {
            showLatestButton = true
        }
        previousLastMessageId = currentLastMessageId
    }

    LaunchedEffect(
        isAtBottomNow,
        userTouchingChat,
        playerResizing
    ) {
        // Changing the viewport during a drag is not a request to browse chat history.
        if (playerResizing) return@LaunchedEffect
        autoScrollDisabled = reduceChatAutoScrollDisabled(
            currentDisabled = autoScrollDisabled,
            isNearBottom = isAtBottomNow,
            userScrolledAwayFromBottom = userTouchingChat && !isAtBottomNow
        )
        if (!autoScrollDisabled) {
            showLatestButton = false
        }
    }

    Box(modifier = modifier.fillMaxWidth().onSizeChanged { viewportHeightPx = it.height }) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        userTouchingChat = true
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        userTouchingChat = false
                    }
                },
            contentPadding = PaddingValues(
                top = if (notice != null) 38.dp else 6.dp,
                bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(chatTextGap.dp)
        ) {
            items(
                items = displayMessages,
                key = { it.id },
                contentType = { it.message.type }
            ) { item ->
                ChatMessageItem(
                    message = item.message,
                    hostName = hostName,
                    chatTextSize = chatTextSize,
                    chatBubbleStyle = chatBubbleStyle
                )
            }
        }

        val motionPolicy = LocalAppMotionPolicy.current
        var cachedNotice by remember { mutableStateOf(notice.orEmpty()) }
        if (notice != null) cachedNotice = notice.orEmpty()
        AnimatedVisibility(
            visible = notice != null,
            enter = expandCollapseEnter(motionPolicy),
            exit = expandCollapseExit(motionPolicy),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = cachedNotice,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showLatestButton,
            enter = popoverEnter(motionPolicy),
            exit = popoverExit(motionPolicy),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 14.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        val latestMessages = messages.takeLast(ChatPanelMaxMessages)
                        autoScrollDisabled = false
                        displayMessages = latestMessages
                        showLatestButton = false
                        previousLastMessageId = messages.lastOrNull()?.id
                        if (latestMessages.isNotEmpty()) {
                            withFrameNanos { }
                            listState.scrollToItem(latestMessages.lastIndex)
                        }
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 3.dp,
                    pressedElevation = 5.dp,
                    focusedElevation = 3.dp,
                    hoveredElevation = 3.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "滚动到最新弹幕"
                )
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: LiveMessage,
    hostName: String,
    chatTextSize: Double,
    chatBubbleStyle: Boolean
) {
    when (message.type) {
        LiveMessageType.CHAT -> {
            val isSystem = message.userName == "LiveSysMessage"
            val chatSurfaceLuminance = MaterialTheme.colorScheme.surface.luminance().toDouble()
            // Light chat surfaces make yellow / lime / cyan unreadable — darken while keeping hue.
            val liveMessageColor = remember(message.color, chatSurfaceLuminance) {
                val readable = resolveReadableChatMessageColor(
                    color = message.color,
                    backgroundLuminance = chatSurfaceLuminance
                )
                val r = (readable.r / 255f).coerceIn(0f, 1f)
                val g = (readable.g / 255f).coerceIn(0f, 1f)
                val b = (readable.b / 255f).coerceIn(0f, 1f)
                Color(r, g, b)
            }
            val colorPolicy = remember(message.color) {
                resolveChatMessageColorPolicy(message.color)
            }
            val userNameColor = MaterialTheme.colorScheme.onSurfaceVariant
            val hostBadgeColor = MaterialTheme.colorScheme.primary
            val hostBadgeOnColor = MaterialTheme.colorScheme.onPrimary
            val messageTextColor = if (colorPolicy.applyMessageColorToText) {
                liveMessageColor
            } else {
                Color.Unspecified
            }

            val isHost = message.userName.isNotEmpty() && message.userName == hostName

            val (annotatedString, inlineContent) = remember(
                message,
                isHost,
                userNameColor,
                messageTextColor,
                isSystem,
                hostBadgeColor,
                hostBadgeOnColor
            ) {
                if (isSystem) {
                    AnnotatedString(message.message) to emptyMap<String, InlineTextContent>()
                } else {
                    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
                    val builder = AnnotatedString.Builder()

                    if (isHost) {
                        builder.appendInlineContent("host_badge", "[主播]")
                        inlineContentMap["host_badge"] = InlineTextContent(
                            Placeholder(
                                width = 36.sp,
                                height = 16.sp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = hostBadgeColor,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 4.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "主播",
                                        color = hostBadgeOnColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    builder.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = userNameColor)) {
                        append(message.userName)
                    }
                    builder.withStyle(SpanStyle(color = userNameColor.copy(alpha = 0.7f))) {
                        append("：")
                    }

                    val spans = normalizeLiveMessageDisplaySpans(
                        message.spans ?: buildLiveMessageDisplaySpans(
                            message = message.message,
                            imageUrls = message.imageUrls,
                            imageMap = message.imageMap
                        )
                    )
                    if (!spans.isNullOrEmpty()) {
                        spans.forEachIndexed { index, span ->
                            when (span) {
                                is LiveMessageSpan.Text -> {
                                    builder.withStyle(SpanStyle(color = messageTextColor)) {
                                        append(span.text)
                                    }
                                }
                                is LiveMessageSpan.Image -> {
                                    val id = "img_${index}"
                                    builder.appendInlineContent(id, "[图片]")
                                    inlineContentMap[id] = InlineTextContent(
                                        Placeholder(
                                            width = 20.sp,
                                            height = 20.sp,
                                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                        )
                                    ) {
                                        EmojiImage(
                                            url = span.imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        builder.withStyle(SpanStyle(color = messageTextColor)) {
                            append(message.message)
                        }
                    }
                    builder.toAnnotatedString() to inlineContentMap
                }
            }

            val textContent = @Composable {
                if (isSystem) {
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = chatTextSize.sp,
                            color = userNameColor
                        ),
                        modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)
                    )
                } else {
                    Text(
                        text = annotatedString,
                        inlineContent = inlineContent,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = chatTextSize.sp,
                            lineHeight = (chatTextSize * 1.42).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            vertical = if (chatBubbleStyle) 6.dp else 3.dp,
                            horizontal = if (chatBubbleStyle) 10.dp else 4.dp
                        )
                    )
                }
            }

            if (chatBubbleStyle && !isSystem) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.5.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.widthIn(max = 310.dp)
                    ) {
                        textContent()
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                ) {
                    textContent()
                }
            }
        }
        LiveMessageType.SUPER_CHAT -> {
            message.superChatMessage?.let { sc ->
                SuperChatCard(item = sc)
            }
        }
        LiveMessageType.ONLINE -> {
            // Online count - handled in ViewModel
        }
        LiveMessageType.GIFT -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.userName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = (chatTextSize - 0.5).coerceAtLeast(12.0).sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " ${message.message}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = (chatTextSize - 0.5).coerceAtLeast(12.0).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ── Super Chat Card ─────────────────────────────────────────────

@Composable
fun SuperChatCard(
    item: LiveSuperChatMessage,
    modifier: Modifier = Modifier,
    customCountdownSeconds: Int? = null
) {
    val topColor = remember(item.backgroundColor) {
        parseSuperChatColor(item.backgroundColor, fallback = Color(0xFFFFD600))
    }
    val bottomColor = remember(item.backgroundBottomColor, item.backgroundColor) {
        parseSuperChatColor(
            value = item.backgroundBottomColor,
            fallback = parseSuperChatColor(item.backgroundColor, fallback = Color(0xFFE9A400))
        )
    }
    val headerInk = if (topColor.luminance() > 0.55f) Color(0xFF1D1D1F) else Color.White
    val headerMuted = headerInk.copy(alpha = 0.72f)
    val bodyInk = if (bottomColor.luminance() > 0.55f) Color(0xFF1D1D1F) else Color.White

    var nowMillis by remember(item.endTime) { mutableLongStateOf(currentEpochMillis()) }
    LaunchedEffect(item.endTime, customCountdownSeconds) {
        if (customCountdownSeconds == null) {
            while (remainingSuperChatSeconds(item, nowMillis) > 0) {
                kotlinx.coroutines.delay(1000L)
                nowMillis = currentEpochMillis()
            }
        }
    }
    val countdown = customCountdownSeconds ?: remainingSuperChatSeconds(item, nowMillis)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(topColor)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = item.face,
                    contentDescription = item.userName,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.userName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = headerInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "¥${item.price}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = headerMuted,
                        maxLines = 1
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = headerInk.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "${countdown}s",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = headerInk,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            if (item.message.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bottomColor)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = item.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = bodyInk
                    )
                }
            }
        }
    }
}

// ── Super Chat Panel ────────────────────────────────────────────

@Composable
fun SuperChatPanel(
    viewModel: LiveRoomViewModel,
    superChatSortDesc: Boolean,
    modifier: Modifier = Modifier
) {
    val superChats by viewModel.superChats.collectAsStateWithLifecycle()

    // Schedule cleanup at the next super chat expiration deadline instead of waking every second
    LaunchedEffect(superChats) {
        if (superChats.isEmpty()) return@LaunchedEffect
        val waitMs = (superChats.minOf { it.endTime } - System.currentTimeMillis() + 50L)
            .coerceAtLeast(50L)
        kotlinx.coroutines.delay(waitMs)
        viewModel.removeExpiredSuperChats()
    }

    val activeChats = remember(superChats) {
        activeSuperChats(superChats, System.currentTimeMillis())
    }
    val sortedChats = remember(activeChats, superChatSortDesc) {
        if (superChatSortDesc) {
            activeChats.sortedByDescending { it.endTime }
        } else {
            activeChats.sortedBy { it.endTime }
        }
    }

    if (sortedChats.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val emptyMsg = if (viewModel.siteId == "huya") "当前直播间无头条内容" else "当前直播间无 SC 内容"
            Text(
                text = emptyMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 18.dp)
        ) {
            items(sortedChats, key = { it.id ?: "${it.userName}_${it.startTime}_${it.message.hashCode()}" }) { item ->
                SuperChatCard(item = item)
            }
        }
    }
}

// ── Follow User Item ────────────────────────────────────────────

@Composable
fun FollowUserItem(
    item: FollowUserEntity,
    isPlaying: Boolean,
    onTap: () -> Unit,
    refreshHint: String? = null
) {
    val siteName = when (item.siteId) {
        "bilibili" -> "哔哩哔哩"
        "douyu" -> "斗鱼"
        "huya" -> "虎牙"
        "douyin" -> "抖音"
        else -> item.siteId
    }
    val subtitle = if (item.siteId != "douyin" && item.roomId.isNotBlank()) {
        "$siteName · 房间 ${item.roomId}"
    } else {
        siteName
    }
    val isLive = item.liveStatus == 1

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onTap
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp)) {
                AsyncImage(
                    model = item.face,
                    contentDescription = item.userName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(11.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                            .padding(1.5.dp)
                            .background(Color(0xFF34C759), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.userName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                refreshHint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            val statusText = when {
                isPlaying -> "当前观看"
                isLive -> "直播中"
                item.liveStatus == 2 -> "未开播"
                else -> "待更新"
            }
            val statusColor = when {
                isPlaying -> MaterialTheme.colorScheme.primary
                isLive -> Color(0xFF34C759)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isPlaying || isLive) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = statusColor,
                maxLines = 1
            )
        }
    }
}

// ── Follow List Panel ────────────────────────────────────────────

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp)
    ) {
        // Filter chips + refresh (manual only; do not auto-refresh on open).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val options = listOf("全部", "直播中", "未开播")
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    options.forEachIndexed { index, text ->
                        val selected = filterIndex == index
                        Surface(
                            onClick = { filterIndex = index },
                            modifier = Modifier
                                .weight(1f)
                                .height(29.dp),
                            shape = RoundedCornerShape(7.dp),
                            color = if (selected) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            IconButton(
                onClick = { viewModel.updateFollowStatus() },
                enabled = !isRefreshing,
                modifier = Modifier.size(36.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新关注状态",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
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
                contentPadding = PaddingValues(top = 6.dp, bottom = 18.dp)
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

@Composable
fun RoomSettingsPanel(
    liveRoomPreferences: LiveRoomPreferences,
    isHuyaOrBilibili: Boolean,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    siteId: String = "",
    onOpenShieldSettings: () -> Unit = {},
    listState: LazyListState = rememberLazyListState()
) {
    val chatTextSize = liveRoomPreferences.chatTextSize
    val chatTextGap = liveRoomPreferences.chatTextGap
    val chatBubbleStyle = liveRoomPreferences.chatBubbleStyle
    val superChatSortDesc = liveRoomPreferences.superChatSortDesc
    val danmuEnable = liveRoomPreferences.danmuEnable
    val danmuRenderEmoji = liveRoomPreferences.danmuRenderEmoji
    val danmuSize = liveRoomPreferences.danmuSize
    val danmuSpeed = liveRoomPreferences.danmuSpeed
    val danmuArea = liveRoomPreferences.danmuArea
    val danmuLineCount = liveRoomPreferences.danmuLineCount
    val danmuDelay = liveRoomPreferences.danmuDelay
    val danmuDelayBySiteJson = liveRoomPreferences.danmuDelayBySiteJson
    val danmuOpacity = liveRoomPreferences.danmuOpacity
    val danmuStrokeWidth = liveRoomPreferences.danmuStrokeWidth
    val danmuTopMargin = liveRoomPreferences.danmuTopMargin
    val danmuBottomMargin = liveRoomPreferences.danmuBottomMargin
    val danmuDedupeEnable = liveRoomPreferences.danmuDedupeEnable
    val danmuDedupeWindow = liveRoomPreferences.danmuDedupeWindow
    val danmuDedupeStep = liveRoomPreferences.danmuDedupeStep
    val danmuDedupeStrictMode = liveRoomPreferences.danmuDedupeStrictMode
    val danmuShieldEnable = liveRoomPreferences.danmuShieldEnable
    val danmuKeywordShieldEnable = liveRoomPreferences.danmuKeywordShieldEnable
    val danmuUserShieldEnable = liveRoomPreferences.danmuUserShieldEnable
    val autoFullScreen = liveRoomPreferences.autoFullScreen
    val autoPipOnExit = liveRoomPreferences.autoPipOnExit
    val pipHideDanmu = liveRoomPreferences.pipHideDanmu
    val normalizedDanmuSize = roomSettingsNormalizedDanmuSize(danmuSize)
    val normalizedDanmuSpeed = roomSettingsNormalizedDanmuSpeed(danmuSpeed)
    val normalizedDanmuArea = DanmakuSettingsPolicy.area(danmuArea)
    val normalizedDanmuOpacity = DanmakuSettingsPolicy.opacity(danmuOpacity)
    val normalizedDanmuStroke = DanmakuSettingsPolicy.strokeWidth(danmuStrokeWidth)
    val siteDanmuDelay = remember(danmuDelayBySiteJson, siteId, danmuDelay) {
        resolveDanmuDelayMs(
            globalDelayMs = danmuDelay.toInt(),
            delayBySiteJson = danmuDelayBySiteJson,
            siteId = siteId
        )
    }
    var expandedSectionNames by rememberSaveable {
        mutableStateOf(defaultExpandedRoomSettingsSections().map { it.name })
    }

    fun isRoomSettingsSectionExpanded(sectionKey: RoomSettingsSectionKey): Boolean {
        return sectionKey.name in expandedSectionNames
    }

    fun setRoomSettingsSectionExpanded(sectionKey: RoomSettingsSectionKey, expanded: Boolean) {
        // Pin the clicked header in place while its content changes height, including sticky headers.
        val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == sectionKey.name }
        listState.requestScrollToItem(
            index = header?.index ?: sectionKey.ordinal * 3,
            scrollOffset = -(header?.offset ?: 0).coerceAtLeast(0)
        )
        expandedSectionNames = if (expanded) {
            (expandedSectionNames + sectionKey.name).distinct()
        } else {
            expandedSectionNames.filterNot { it == sectionKey.name }
        }
    }

    val settingsDividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("room_settings_list"),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
    ) {
        RoomSettingsCollapsibleSection(
            listState = listState,
            sectionKey = RoomSettingsSectionKey.PLAYER_DANMAKU,
            title = "播放器弹幕设置",
            subtitle = "滚动弹幕显示、表情、区域、延迟和边距",
            expanded = isRoomSettingsSectionExpanded(RoomSettingsSectionKey.PLAYER_DANMAKU),
            onExpandedChange = {
                setRoomSettingsSectionExpanded(RoomSettingsSectionKey.PLAYER_DANMAKU, it)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoomSettingsSwitchRow(
                    title = "显示播放器弹幕",
                    subtitle = "在视频画面上显示滚动弹幕",
                    checked = danmuEnable,
                    onCheckedChange = { viewModel.setDanmuEnable(it) }
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSwitchRow(
                    title = "渲染表情",
                    subtitle = "在播放器弹幕中显示平台表情图片",
                    checked = danmuRenderEmoji,
                    onCheckedChange = { viewModel.setDanmuRenderEmoji(it) }
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSliderRow(
                    title = "弹幕字号",
                    valueText = roomSettingsMultiplierText(normalizedDanmuSize),
                    value = normalizedDanmuSize.toFloat(),
                    onValueChange = { viewModel.setDanmuSize(it.toDouble()) },
                    valueRange = DanmakuSettingsPolicy.sizeRange,
                    steps = DanmakuSettingsPolicy.sizeValues.size - 2
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSliderRow(
                    title = "弹幕速度",
                    valueText = roomSettingsMultiplierText(normalizedDanmuSpeed),
                    value = normalizedDanmuSpeed.toFloat(),
                    onValueChange = { viewModel.setDanmuSpeed(it.toDouble()) },
                    valueRange = DanmakuSettingsPolicy.speedRange,
                    steps = DanmakuSettingsPolicy.speedValues.size - 2
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSliderRow(
                    title = "显示区域",
                    valueText = roomSettingsPercentText(normalizedDanmuArea),
                    value = normalizedDanmuArea.toFloat(),
                    onValueChange = { viewModel.setDanmuArea(it.toDouble()) },
                    valueRange = DanmakuSettingsPolicy.areaRange,
                    steps = 14
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSliderRow(
                    title = "显示几行",
                    valueText = if (danmuLineCount <= 0) "不显示" else "$danmuLineCount 行",
                    value = danmuLineCount.toFloat(),
                    onValueChange = { viewModel.setDanmuLineCount(it.toInt()) },
                    valueRange = 0f..12f,
                    steps = 11
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSliderRow(
                    title = "弹幕透明度",
                    valueText = roomSettingsPercentText(normalizedDanmuOpacity),
                    value = normalizedDanmuOpacity.toFloat(),
                    onValueChange = { viewModel.setDanmuOpacity(it.toDouble()) },
                    valueRange = DanmakuSettingsPolicy.opacityRange,
                    steps = DanmakuSettingsPolicy.opacityValues.size - 2
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSliderRow(
                    title = "弹幕描边",
                    valueText = roomSettingsStrokeText(normalizedDanmuStroke),
                    value = normalizedDanmuStroke.toFloat(),
                    onValueChange = { viewModel.setDanmuStrokeWidth(it.toDouble()) },
                    valueRange = DanmakuSettingsPolicy.strokeRange,
                    steps = DanmakuSettingsPolicy.strokeValues.size - 2
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSliderRow(
                    title = "全局弹幕延迟",
                    valueText = "${danmuDelay.toInt()} ms",
                    value = danmuDelay.toFloat(),
                    onValueChange = { viewModel.setDanmuDelay(it.toDouble()) },
                    valueRange = 0f..5000f,
                    steps = 49
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSliderRow(
                    title = "${danmuDelaySiteLabel(siteId)} 平台补偿延迟",
                    valueText = "$siteDanmuDelay ms",
                    value = siteDanmuDelay.toFloat(),
                    onValueChange = {
                        viewModel.setDanmuDelayBySiteJson(
                            updateDanmuDelayBySite(
                                delayBySiteJson = danmuDelayBySiteJson,
                                siteId = siteId,
                                delayMs = it.toInt()
                            )
                        )
                    },
                    valueRange = 0f..5000f,
                    steps = 49
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSliderRow(
                    title = "顶部安全边距",
                    valueText = "${danmuTopMargin.toInt()} dp",
                    value = danmuTopMargin.toFloat(),
                    onValueChange = { viewModel.setDanmuTopMargin(it.toDouble()) },
                    valueRange = 0f..48f,
                    steps = 11
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSliderRow(
                    title = "底部安全边距",
                    valueText = "${danmuBottomMargin.toInt()} dp",
                    value = danmuBottomMargin.toFloat(),
                    onValueChange = { viewModel.setDanmuBottomMargin(it.toDouble()) },
                    valueRange = 0f..48f,
                    steps = 11
                )
            }
        }

        RoomSettingsCollapsibleSection(
            listState = listState,
            sectionKey = RoomSettingsSectionKey.LIVE_BEHAVIOR,
            title = "直播间行为设置",
            subtitle = "全屏、小窗和小窗弹幕行为",
            expanded = isRoomSettingsSectionExpanded(RoomSettingsSectionKey.LIVE_BEHAVIOR),
            onExpandedChange = {
                setRoomSettingsSectionExpanded(RoomSettingsSectionKey.LIVE_BEHAVIOR, it)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoomSettingsSwitchRow(
                    title = "进入直播间自动全屏",
                    subtitle = "打开直播间后自动进入横屏全屏播放",
                    checked = autoFullScreen,
                    onCheckedChange = { viewModel.setAutoFullScreen(it) }
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSwitchRow(
                    title = "退出时自动小窗",
                    subtitle = "按 Home 键或系统手势退到后台时进入小窗",
                    checked = autoPipOnExit,
                    onCheckedChange = { viewModel.setAutoPipOnExit(it) }
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSwitchRow(
                    title = "进入小窗隐藏弹幕",
                    subtitle = "小窗播放时隐藏播放器滚动弹幕",
                    checked = pipHideDanmu,
                    onCheckedChange = { viewModel.setPipHideDanmu(it) }
                )
            }
        }

        RoomSettingsCollapsibleSection(
            listState = listState,
            sectionKey = RoomSettingsSectionKey.CHAT,
            title = "聊天区设置",
            subtitle = "文字大小、间距、气泡样式和 SC 排序",
            expanded = isRoomSettingsSectionExpanded(RoomSettingsSectionKey.CHAT),
            onExpandedChange = {
                setRoomSettingsSectionExpanded(RoomSettingsSectionKey.CHAT, it)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Font size slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "文字大小", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        Text(text = "${chatTextSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RoomSettingsSlider(
                        value = chatTextSize.toFloat(),
                        onValueChange = { viewModel.setChatTextSize(it.toDouble()) },
                        valueRange = 8f..36f,
                        steps = 27,
                        modifier = Modifier.semantics { contentDescription = "文字大小" }
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                // Gap slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "上下间隔", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        Text(text = "${chatTextGap.toInt()} dp", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RoomSettingsSlider(
                        value = chatTextGap.toFloat(),
                        onValueChange = { viewModel.setChatTextGap(it.toDouble()) },
                        valueRange = 0f..12f,
                        steps = 11,
                        modifier = Modifier.semantics { contentDescription = "上下间隔" }
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                // Bubble style switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "气泡样式", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        Text(text = "使用类似社交聊天的气泡框渲染弹幕", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = chatBubbleStyle,
                        onCheckedChange = { viewModel.setChatBubbleStyle(it) }
                    )
                }

                if (isHuyaOrBilibili) {
                    HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                    // SC sort desc
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "SC排序", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text(
                                text = if (superChatSortDesc) "按消失时间倒序" else "按消失时间正序",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = superChatSortDesc,
                            onCheckedChange = { viewModel.setSuperChatSortDesc(it) }
                        )
                    }
                }
            }
        }

        RoomSettingsCollapsibleSection(
            listState = listState,
            sectionKey = RoomSettingsSectionKey.SHIELD,
            title = "弹幕屏蔽设置",
            subtitle = "关键词、用户屏蔽和屏蔽管理入口",
            expanded = isRoomSettingsSectionExpanded(RoomSettingsSectionKey.SHIELD),
            onExpandedChange = {
                setRoomSettingsSectionExpanded(RoomSettingsSectionKey.SHIELD, it)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoomSettingsSwitchRow(
                    title = "启用弹幕屏蔽",
                    subtitle = "关闭后关键词和用户屏蔽都会暂时失效",
                    checked = danmuShieldEnable,
                    onCheckedChange = { viewModel.setDanmuShieldEnable(it) }
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSwitchRow(
                    title = "关键词屏蔽",
                    subtitle = "按普通文本或 /.../ 正则过滤聊天弹幕",
                    checked = danmuKeywordShieldEnable,
                    onCheckedChange = { viewModel.setDanmuKeywordShieldEnable(it) }
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                RoomSettingsSwitchRow(
                    title = "用户屏蔽",
                    subtitle = "按全平台或当前平台用户名过滤聊天弹幕",
                    checked = danmuUserShieldEnable,
                    onCheckedChange = { viewModel.setDanmuUserShieldEnable(it) }
                )

                HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenShieldSettings),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "打开屏蔽管理", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        Text(text = "添加、编辑关键词和屏蔽用户，管理屏蔽预设", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        RoomSettingsCollapsibleSection(
            listState = listState,
            sectionKey = RoomSettingsSectionKey.FILTER,
            title = "弹幕过滤设置",
            subtitle = "重复弹幕过滤、严格模式、窗口和步长",
            expanded = isRoomSettingsSectionExpanded(RoomSettingsSectionKey.FILTER),
            onExpandedChange = {
                setRoomSettingsSectionExpanded(RoomSettingsSectionKey.FILTER, it)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Deduplicate switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "重复弹幕过滤", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        Text(text = "同一用户在最近若干条内重复刷同一句时只显示一次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = danmuDedupeEnable,
                        onCheckedChange = { viewModel.setDanmuDedupeEnable(it) }
                    )
                }

                val filterMotionPolicy = LocalAppMotionPolicy.current
                AnimatedVisibility(
                    visible = danmuDedupeEnable,
                    enter = expandCollapseEnter(filterMotionPolicy),
                    exit = expandCollapseExit(filterMotionPolicy)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                        // Strict deduplication mode switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "严格去重模式", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Text(text = "全局过滤不同观众发送的相同弹幕（不区分发送人）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = danmuDedupeStrictMode,
                                onCheckedChange = { viewModel.setDanmuDedupeStrictMode(it) }
                            )
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                        // Deduplicate window slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "过滤窗口", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Text(text = "${danmuDedupeWindow} 条", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RoomSettingsSlider(
                                value = danmuDedupeWindow.toFloat(),
                                onValueChange = { viewModel.setDanmuDedupeWindow(it.toInt()) },
                                valueRange = 1f..100f,
                                steps = 98,
                                modifier = Modifier.semantics { contentDescription = "过滤窗口" }
                            )
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = settingsDividerColor)

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "过滤步长", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Text(text = "$danmuDedupeStep 条", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RoomSettingsSlider(
                                value = danmuDedupeStep.toFloat(),
                                onValueChange = { viewModel.setDanmuDedupeStep(it.toInt()) },
                                valueRange = 1f..20f,
                                steps = 18,
                                modifier = Modifier.semantics { contentDescription = "过滤步长" }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.RoomSettingsCollapsibleSection(
    listState: LazyListState,
    sectionKey: RoomSettingsSectionKey,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    stickyHeader(key = sectionKey.name, contentType = "settings_header") { headerIndex ->
        val motionPolicy = LocalAppMotionPolicy.current
        val pinned by remember(listState, headerIndex, expanded) {
            derivedStateOf {
                expanded && (listState.firstVisibleItemIndex > headerIndex ||
                    (listState.firstVisibleItemIndex == headerIndex && listState.firstVisibleItemScrollOffset > 0))
            }
        }
        val arrowRotation by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = motionPolicy.contentSpec(),
            label = "roomSettingsSectionArrow_${sectionKey.name}"
        )
        // Include the opaque gutter in the pinned item so following cards cannot touch it
        // or show through it while they scroll underneath.
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(bottom = 12.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = if (pinned) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .testTag("room_settings_section_${sectionKey.name}")
                        .clickable(role = Role.Button, onClickLabel = if (expanded) "收起$title" else "展开$title") {
                            onExpandedChange(!expanded)
                        }
                        .semantics { heading(); stateDescription = if (expanded) "已展开" else "已收起" }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(if (pinned) "当前分组 · 点击收起" else subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起$title" else "展开$title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = arrowRotation })
                }
            }
        }
    }
    item(key = "${sectionKey.name}_content", contentType = "settings_content") {
        val motionPolicy = LocalAppMotionPolicy.current
        AnimatedVisibility(
            visible = expanded,
            enter = if (motionPolicy.reducedMotion) fadeIn(motionPolicy.effectsSpec()) else
                expandVertically(motionPolicy.contentSpec(), expandFrom = Alignment.Top) + fadeIn(motionPolicy.effectsSpec()),
            exit = if (motionPolicy.reducedMotion) fadeOut(motionPolicy.effectsSpec()) else
                shrinkVertically(motionPolicy.contentSpec(), shrinkTowards = Alignment.Top) + fadeOut(motionPolicy.effectsSpec())
        ) {
            Column {
                Surface(modifier = Modifier.fillMaxWidth().testTag("room_settings_content_${sectionKey.name}"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(content = content)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    // An empty end marker releases the pinned heading at this group's boundary.
    // Collapsed groups therefore scroll as ordinary, separated cards.
    stickyHeader(key = "${sectionKey.name}_end", contentType = "settings_boundary") {
        Spacer(Modifier.fillMaxWidth().height(0.dp))
    }
}
@Composable
private fun RoomSettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun RoomSettingsSliderRow(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(text = valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RoomSettingsSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.semantics { contentDescription = title }
        )
    }
}

private fun roomSettingsNormalizedDanmuSize(value: Double): Double =
    DanmakuSettingsPolicy.sizeMultiplier(value)

private fun roomSettingsNormalizedDanmuSpeed(value: Double): Double =
    DanmakuSettingsPolicy.speedMultiplier(value)

private fun roomSettingsMultiplierText(value: Double): String =
    DanmakuSettingsPolicy.multiplierText(value)

private fun roomSettingsPercentText(value: Double): String =
    DanmakuSettingsPolicy.percentText(value)

private fun roomSettingsStrokeText(value: Double): String =
    DanmakuSettingsPolicy.strokeText(value)

private fun roomSettingsOneDecimal(value: Double): String {
    val rounded = (value * 10).roundToInt()
    return "${rounded / 10}.${kotlin.math.abs(rounded % 10)}"
}

@Composable
internal fun RoomSettingsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val valueStart = valueRange.start
    val valueEnd = valueRange.endInclusive
    val rangeSize = (valueEnd - valueStart).takeIf { it > 0f } ?: 1f
    val latestValue by rememberUpdatedState(value.coerceIn(valueStart, valueEnd))
    val commitValue by rememberUpdatedState(onValueChange)
    var pendingValue by remember(valueRange, steps) { mutableFloatStateOf(latestValue) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value, valueRange, steps) {
        if (!dragging) pendingValue = latestValue
    }
    // Keep the preview on release until the persisted value arrives, without snapping back.
    val coercedValue = pendingValue.coerceIn(valueStart, valueEnd)
    val fraction = ((coercedValue - valueStart) / rangeSize).coerceIn(0f, 1f)
    val density = LocalDensity.current
    val horizontalInset = 13.dp
    val horizontalInsetPx = with(density) { horizontalInset.toPx() }
    var widthPx by remember { mutableFloatStateOf(1f) }

    fun valueForPosition(x: Float): Float {
        val usableWidth = (widthPx - horizontalInsetPx * 2f).coerceAtLeast(1f)
        val rawFraction = ((x - horizontalInsetPx) / usableWidth).coerceIn(0f, 1f)
        return roomSettingsSliderValueForFraction(rawFraction, valueRange, steps)
    }

    fun commitPendingValue() {
        if (!dragging) return
        dragging = false
        if (pendingValue != latestValue) commitValue(pendingValue)
    }

    fun cancelPendingValue() {
        dragging = false
        pendingValue = latestValue
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(coercedValue, valueRange, steps)
                setProgress { target ->
                    if (!target.isFinite()) false else {
                        pendingValue = roomSettingsSliderSnapValue(target, valueRange, steps)
                        if (pendingValue != latestValue) commitValue(pendingValue)
                        true
                    }
                }
            }
            .pointerInput(valueRange, steps, widthPx) {
                detectTapGestures { offset ->
                    pendingValue = valueForPosition(offset.x)
                    if (pendingValue != latestValue) commitValue(pendingValue)
                }
            }
            .pointerInput(valueRange, steps, widthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        pendingValue = valueForPosition(offset.x)
                    },
                    onDragEnd = { commitPendingValue() },
                    onDragCancel = { cancelPendingValue() },
                    onHorizontalDrag = { change, _ ->
                        pendingValue = valueForPosition(change.position.x)
                        change.consume()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val startX = horizontalInset.toPx()
            val endX = size.width - horizontalInset.toPx()
            val activeX = startX + (endX - startX) * fraction
            val trackStroke = 4.dp.toPx()
            val thumbShadowRadius = 11.5.dp.toPx()
            val thumbBorderRadius = 10.5.dp.toPx()
            val thumbRadius = 9.8.dp.toPx()

            drawLine(
                color = inactiveColor,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = activeColor,
                start = Offset(startX, centerY),
                end = Offset(activeX, centerY),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.08f),
                radius = thumbShadowRadius,
                center = Offset(activeX, centerY + 1.dp.toPx())
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.12f),
                radius = thumbBorderRadius,
                center = Offset(activeX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(activeX, centerY)
            )
        }
    }
}

private fun roomSettingsSliderSnapValue(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
): Float {
    val valueStart = valueRange.start
    val valueEnd = valueRange.endInclusive
    val rangeSize = valueEnd - valueStart
    if (rangeSize <= 0f) return valueStart
    return roomSettingsSliderValueForFraction(
        rawFraction = ((value - valueStart) / rangeSize).coerceIn(0f, 1f),
        valueRange = valueRange,
        steps = steps
    )
}

private fun roomSettingsSliderValueForFraction(
    rawFraction: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
): Float {
    val valueStart = valueRange.start
    val valueEnd = valueRange.endInclusive
    val rangeSize = valueEnd - valueStart
    if (rangeSize <= 0f) return valueStart

    val fraction = rawFraction.coerceIn(0f, 1f)
    val snappedFraction = if (steps <= 0) {
        fraction
    } else {
        val intervals = (steps + 1).coerceAtLeast(1)
        (fraction * intervals).roundToInt() / intervals.toFloat()
    }
    return valueStart + rangeSize * snappedFraction
}
