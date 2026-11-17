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

internal fun currentEpochMillis(): Long = System.currentTimeMillis()

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

    // Quality selection bottom sheet
    var showQualitySheet by remember { mutableStateOf(false) }

    if (showQualitySheet && uiState.playQualities.isNotEmpty()) {
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

