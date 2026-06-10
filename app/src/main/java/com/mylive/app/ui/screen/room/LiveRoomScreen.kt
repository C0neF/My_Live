package com.mylive.app.ui.screen.room

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.mylive.app.R
import com.mylive.app.core.common.buildLiveMessageDisplaySpans
import com.mylive.app.core.common.normalizeLiveMessageDisplaySpans
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.core.model.LiveMessageSpan
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.ui.emoji.EmojiImage
import com.mylive.app.ui.screen.room.player.PlayerController
import com.mylive.app.ui.screen.room.player.PlayerView
import com.mylive.app.ui.screen.room.player.DanmakuController
import com.mylive.app.ui.screen.room.player.PlayerState
import com.mylive.app.ui.screen.settings.SettingsViewModel
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clipToBounds
import com.mylive.app.ui.screen.follow.FollowViewModel
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.screen.room.quickaccess.QuickAccessExtraTab
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOutCubic
import kotlinx.coroutines.launch
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    val rawUiState by viewModel.uiState.collectAsState()
    val uiState = liveRoomUiStateForRoute(
        uiState = rawUiState,
        viewModelRoomId = viewModel.roomId,
        viewModelSiteId = viewModel.siteId,
        routeRoomId = key.roomId,
        routeSiteId = key.siteId,
        routeInitialIsFollowing = key.initialIsFollowing
    )
    val danmakuMessages by viewModel.danmakuMessages.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val activity = context as? Activity

    var isExiting by remember { mutableStateOf(false) }
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scaleMode by settingsViewModel.scaleMode.collectAsState()
    val playerCompatMode by settingsViewModel.playerCompatMode.collectAsState()
    val pipHideDanmu by settingsViewModel.pipHideDanmu.collectAsState(initial = false)
    val chatBubbleStyle by settingsViewModel.chatBubbleStyle.collectAsState()
    val pipDanmuEnable by settingsViewModel.danmuEnable.collectAsState(initial = true)
    val pipDanmuSize by settingsViewModel.danmuSize.collectAsState(initial = 16.0)
    val pipDanmuSpeed by settingsViewModel.danmuSpeed.collectAsState(initial = 10.0)
    val pipDanmuArea by settingsViewModel.danmuArea.collectAsState(initial = 0.8)
    val pipDanmuLineCount by settingsViewModel.danmuLineCount.collectAsState(initial = 8)
    val pipDanmuDelay by settingsViewModel.danmuDelay.collectAsState(initial = 0.0)
    val pipDanmuDelayBySiteJson by settingsViewModel.danmuDelayBySiteJson.collectAsState(initial = "{}")
    val pipDanmuOpacity by settingsViewModel.danmuOpacity.collectAsState(initial = 1.0)
    val pipDanmuFontWeight by settingsViewModel.danmuFontWeight.collectAsState(initial = 4)
    val pipDanmuStrokeWidth by settingsViewModel.danmuStrokeWidth.collectAsState(initial = 2.0)
    val pipDanmuTopMargin by settingsViewModel.danmuTopMargin.collectAsState(initial = 0.0)
    val pipDanmuBottomMargin by settingsViewModel.danmuBottomMargin.collectAsState(initial = 0.0)
    val pipDanmuHideScroll by settingsViewModel.danmuHideScroll.collectAsState(initial = false)
    val pipDanmuDedupeEnable by settingsViewModel.danmuDedupeEnable.collectAsState(initial = false)
    val pipDanmuDedupeWindow by settingsViewModel.danmuDedupeWindow.collectAsState(initial = 10)
    val pipDanmuDedupeStep by settingsViewModel.danmuDedupeStep.collectAsState(initial = 2)
    val pipDanmuDedupeStrictMode by settingsViewModel.danmuDedupeStrictMode.collectAsState(initial = false)
    val pipDanmuRenderEmoji by settingsViewModel.danmuRenderEmoji.collectAsState(initial = true)

    val componentActivity = context as? androidx.activity.ComponentActivity
    var isInPip by remember { mutableStateOf(componentActivity?.isInPictureInPictureMode == true) }
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

    DisposableEffect(componentActivity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPip = info.isInPictureInPictureMode
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    LaunchedEffect(pipDanmakuController, isInPip, pipDanmuEnable, pipHideDanmu, isExiting) {
        val controller = pipDanmakuController
        if (controller != null && isInPip && pipDanmuEnable && !pipHideDanmu && !isExiting) {
            viewModel.newDanmakuMessages.collect { msg ->
                if (isInPip && pipDanmuEnable && !pipHideDanmu && !isExiting) {
                    controller.addDanmaku(msg)
                }
            }
        } else {
            controller?.clear()
        }
    }

    val autoPipOnExit by settingsViewModel.autoPipOnExit.collectAsState(initial = false)
    val autoFullScreen by settingsViewModel.autoFullScreen.collectAsState(initial = false)
    DisposableEffect(autoPipOnExit) {
        com.mylive.app.MainActivity.isPipSupportedAndActive = autoPipOnExit
        onDispose {
            com.mylive.app.MainActivity.isPipSupportedAndActive = false
        }
    }

    var playerController by remember { mutableStateOf<PlayerController?>(null) }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            playerController?.stop()
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(AppMotion.LiveRoomPlayerStartupDelayMillis.toLong())
        val hardwareDecodeEnabled = viewModel.settingsRepository.hardwareDecode.first()
        val forceHttps = viewModel.settingsRepository.playerForceHttps.first()
        val pc = PlayerController(context, hardwareDecodeEnabled, forceHttps)
        playerController = pc
        viewModel.playerController = pc
        viewModel.onPlayerControllerReady()
    }

    DisposableEffect(playerController) {
        val pc = playerController
        onDispose {
            pc?.release()
        }
    }

    // Sync playback auto-pause & background play settings with Lifecycle
    val allowBackgroundPlayback by settingsViewModel.allowBackgroundPlayback.collectAsState()
    val playerAutoPause by settingsViewModel.playerAutoPause.collectAsState()
    val playerForceHttps by settingsViewModel.playerForceHttps.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(playerController, playerForceHttps) {
        playerController?.setForceHttps(playerForceHttps)
    }

    DisposableEffect(lifecycleOwner, playerController, allowBackgroundPlayback, playerAutoPause, uiState.detail) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val p = playerController?.player
                    if (!allowBackgroundPlayback || playerAutoPause) {
                        playerController?.pause()
                    } else if (p != null && p.isPlaying) {
                        com.mylive.app.service.PlaybackForegroundService.start(
                            context,
                            p,
                            uiState.detail?.title ?: "",
                            uiState.detail?.userName ?: "",
                            viewModel.siteId
                        )
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    com.mylive.app.service.PlaybackForegroundService.stop(context)
                    playerController?.resume()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            com.mylive.app.service.PlaybackForegroundService.stop(context)
        }
    }

    // Room idle auto exit state
    val roomAutoExitDuration by settingsViewModel.roomAutoExitDuration.collectAsState()
    val lastInteractionTime = remember { AtomicLong(System.currentTimeMillis()) }

    val playerState = playerController?.state?.collectAsState()?.value ?: PlayerState()
    val isFullscreen = playerState.isFullscreen
    var autoFullscreenAppliedRoute by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(autoFullScreen, playerController, uiState.detail, key.roomId, key.siteId, isFullscreen) {
        val route = key.siteId to key.roomId
        if (
            autoFullScreen &&
            playerController != null &&
            uiState.detail != null &&
            autoFullscreenAppliedRoute != route &&
            !isFullscreen
        ) {
            autoFullscreenAppliedRoute = route
            playerController?.toggleFullscreen()
        }
    }

    // Handle fullscreen: change orientation + hide/show system bars
    LaunchedEffect(isFullscreen) {
        val act = activity ?: return@LaunchedEffect
        if (isFullscreen) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(act.window, false)
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.setDecorFitsSystemWindows(act.window, true)
            WindowCompat.getInsetsController(act.window, act.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }



    // Shared back handler: stop playback, restore orientation, then navigate
    val handleBack: () -> Unit = remember(navigator, playerController) {
        {
            if (!isExiting) {
                isExiting = true
                // Restore portrait orientation and show system bars only if currently in landscape
                if (activity?.resources?.configuration?.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    activity.window?.let { window ->
                        WindowCompat.setDecorFitsSystemWindows(window, true)
                        WindowCompat.getInsetsController(window, window.decorView)
                            .show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                if (!navigator.goBack()) {
                    playerController?.pause()
                }
            }
        }
    }

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

    BackHandler {
        if (isFullscreen) {
            playerController?.toggleFullscreen()
        } else {
            handleBack()
        }
    }

    // Quality selection bottom sheet
    var showQualitySheet by remember { mutableStateOf(false) }
    var showQuickAccess by remember { mutableStateOf(false) }

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

    if (showQuickAccess) {
        com.mylive.app.ui.screen.room.quickaccess.QuickAccessPanel(
            currentSiteId = viewModel.siteId,
            currentRoomId = viewModel.roomId,
            currentCategoryId = uiState.detail?.categoryId,
            onNavigateToRoom = { siteId, roomId, initialIsFollowing ->
                showQuickAccess = false
                navigator.navigate(
                    Route.LiveRoomDetail(
                        roomId = roomId,
                        siteId = siteId,
                        initialIsFollowing = initialIsFollowing
                    ),
                    singleTop = true,
                    popUpToRoute = Route.Index::class.java,
                    inclusive = false
                )
            },
            onDismiss = { showQuickAccess = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                modifier = Modifier.fillMaxSize(),
                title = uiState.detail?.title ?: "",
                onBack = {},
                currentQualityName = currentQualityName,
                onQualityClick = {},
                onRefreshClick = {},
                danmuEnable = pipDanmuEnable && !pipHideDanmu,
                onDanmuToggle = {},
                accentColor = roomPlatformAccentColor,
                danmuSize = pipDanmuSize,
                onDanmuSizeChange = {},
                danmuSpeed = pipDanmuSpeed,
                onDanmuSpeedChange = {},
                danmuArea = pipDanmuArea,
                onDanmuAreaChange = {},
                danmuLineCount = pipDanmuLineCount,
                danmuDelayMs = resolvedPipDanmuDelay,
                danmuOpacity = pipDanmuOpacity,
                onDanmuOpacityChange = {},
                danmuFontWeight = pipDanmuFontWeight,
                danmuStrokeWidth = pipDanmuStrokeWidth,
                danmuTopMargin = pipDanmuTopMargin,
                danmuBottomMargin = pipDanmuBottomMargin,
                danmuHideScroll = pipDanmuHideScroll,
                danmuDedupeEnable = pipDanmuDedupeEnable,
                danmuDedupeWindow = pipDanmuDedupeWindow,
                danmuDedupeStep = pipDanmuDedupeStep,
                danmuDedupeStrictMode = pipDanmuDedupeStrictMode,
                scaleMode = scaleMode,
                playerCompatMode = playerCompatMode,
                danmuRenderEmoji = pipDanmuRenderEmoji,
                chatBubbleStyle = chatBubbleStyle,
                onChatBubbleStyleChange = { settingsViewModel.setChatBubbleStyle(it) },
                onDanmakuControllerCreated = { pipDanmakuController = it },
                isExiting = isExiting
            )
        } else if (isLandscape || isFullscreen) {
            LandscapeLayout(
                uiState = uiState,
                danmakuMessages = danmakuMessages,
                viewModel = viewModel,
                accentSiteId = accentSiteId,
                navigator = navigator,
                playerController = playerController,
                activity = activity,
                onQualityClick = { showQualitySheet = true },
                isExiting = isExiting,
                onBack = {
                    if (isFullscreen) {
                        playerController?.toggleFullscreen()
                    } else {
                        handleBack()
                    }
                }
            )
        } else {
            PortraitLayout(
                uiState = uiState,
                danmakuMessages = danmakuMessages,
                viewModel = viewModel,
                accentSiteId = accentSiteId,
                navigator = navigator,
                playerController = playerController,
                activity = activity,
                onQualityClick = { showQualitySheet = true },
                isExiting = isExiting,
                onBack = handleBack
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitLayout(
    uiState: LiveRoomUiState,
    danmakuMessages: List<DisplayLiveMessage>,
    viewModel: LiveRoomViewModel,
    accentSiteId: String,
    navigator: Navigator,
    playerController: PlayerController?,
    activity: Activity?,
    onQualityClick: () -> Unit,
    isExiting: Boolean = false,
    onBack: () -> Unit = {}
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val danmuEnable by settingsViewModel.danmuEnable.collectAsState()
    val danmuSize by settingsViewModel.danmuSize.collectAsState()
    val danmuSpeed by settingsViewModel.danmuSpeed.collectAsState()
    val danmuArea by settingsViewModel.danmuArea.collectAsState()
    val danmuLineCount by settingsViewModel.danmuLineCount.collectAsState()
    val danmuDelay by settingsViewModel.danmuDelay.collectAsState()
    val danmuDelayBySiteJson by settingsViewModel.danmuDelayBySiteJson.collectAsState()
    val danmuOpacity by settingsViewModel.danmuOpacity.collectAsState()
    val danmuFontWeight by settingsViewModel.danmuFontWeight.collectAsState()
    val danmuStrokeWidth by settingsViewModel.danmuStrokeWidth.collectAsState()
    val danmuTopMargin by settingsViewModel.danmuTopMargin.collectAsState()
    val danmuBottomMargin by settingsViewModel.danmuBottomMargin.collectAsState()
    val danmuHideScroll by settingsViewModel.danmuHideScroll.collectAsState()
    val danmuDedupeEnable by settingsViewModel.danmuDedupeEnable.collectAsState()
    val danmuDedupeWindow by settingsViewModel.danmuDedupeWindow.collectAsState()
    val danmuDedupeStep by settingsViewModel.danmuDedupeStep.collectAsState()
    val danmuDedupeStrictMode by settingsViewModel.danmuDedupeStrictMode.collectAsState()
    val scaleMode by settingsViewModel.scaleMode.collectAsState()
    val playerCompatMode by settingsViewModel.playerCompatMode.collectAsState()
    val danmuRenderEmoji by settingsViewModel.danmuRenderEmoji.collectAsState()

    val chatTextSize by settingsViewModel.chatTextSize.collectAsState()
    val chatTextGap by settingsViewModel.chatTextGap.collectAsState()
    val chatBubbleStyle by settingsViewModel.chatBubbleStyle.collectAsState()
    val superChatSortDesc by settingsViewModel.superChatSortDesc.collectAsState()

    val superChats by viewModel.superChats.collectAsState()
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

    if (showQuickAccess) {
        com.mylive.app.ui.screen.room.quickaccess.QuickAccessPanel(
            currentSiteId = viewModel.siteId,
            currentRoomId = viewModel.roomId,
            currentCategoryId = uiState.detail?.categoryId,
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
                        initialIsFollowing = initialIsFollowing
                    ),
                    singleTop = true,
                    popUpToRoute = Route.Index::class.java,
                    inclusive = false
                )
            },
            onDismiss = { showQuickAccess = false }
        )
    }

    activeAuxiliaryPanel?.let { panel ->
        PortraitAuxiliaryPanelSheet(
            panel = panel,
            uiState = uiState,
            viewModel = viewModel,
            navigator = navigator,
            settingsViewModel = settingsViewModel,
            superChatSortDesc = superChatSortDesc,
            onDismiss = { activeAuxiliaryPanel = null }
        )
    }

    LaunchedEffect(danmakuController, isExiting) {
        if (danmakuController != null && !isExiting) {
            viewModel.newDanmakuMessages.collect { msg ->
                if (!isExiting) {
                    danmakuController?.addDanmaku(msg)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Player area (16:9) with danmaku overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            val currentQualityName = uiState.playQualities.getOrNull(uiState.currentQualityIndex)?.quality ?: "画质"
            PlayerView(
                playerController = playerController,
                modifier = Modifier.fillMaxSize(),
                title = uiState.detail?.title ?: "",
                onBack = onBack,
                currentQualityName = currentQualityName,
                onQualityClick = onQualityClick,
                onRefreshClick = { viewModel.refreshPlay() },
                danmuEnable = danmuEnable,
                onDanmuToggle = { settingsViewModel.setDanmuEnable(it) },
                accentColor = roomPlatformAccentColor,
                danmuSize = danmuSize,
                onDanmuSizeChange = { settingsViewModel.setDanmuSize(it) },
                danmuSpeed = danmuSpeed,
                onDanmuSpeedChange = { settingsViewModel.setDanmuSpeed(it) },
                danmuArea = danmuArea,
                onDanmuAreaChange = { settingsViewModel.setDanmuArea(it) },
                danmuLineCount = danmuLineCount,
                danmuDelayMs = resolvedDanmuDelay,
                danmuOpacity = danmuOpacity,
                onDanmuOpacityChange = { settingsViewModel.setDanmuOpacity(it) },
                danmuFontWeight = danmuFontWeight,
                danmuStrokeWidth = danmuStrokeWidth,
                danmuTopMargin = danmuTopMargin,
                danmuBottomMargin = danmuBottomMargin,
                danmuHideScroll = danmuHideScroll,
                danmuDedupeEnable = danmuDedupeEnable,
                danmuDedupeWindow = danmuDedupeWindow,
                danmuDedupeStep = danmuDedupeStep,
                danmuDedupeStrictMode = danmuDedupeStrictMode,
                scaleMode = scaleMode,
                playerCompatMode = playerCompatMode,
                danmuRenderEmoji = danmuRenderEmoji,
                chatBubbleStyle = chatBubbleStyle,
                onChatBubbleStyleChange = { settingsViewModel.setChatBubbleStyle(it) },
                onDanmakuControllerCreated = { danmakuController = it },
                isExiting = isExiting
            )
        }

        CompactPortraitRoomHeader(
            detail = uiState.detail,
            isFollowing = uiState.isFollowing,
            isFollowStatusKnown = uiState.isFollowStatusKnown,
            accentColor = roomPlatformAccentColor,
            onToggleFollow = { viewModel.toggleFollow() },
            onRefreshClick = { viewModel.refreshPlay() },
            onSettingsClick = { activeAuxiliaryPanel = PortraitLiveRoomPanel.SETTINGS },
            onQuickAccessClick = { showQuickAccess = true }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ChatPanel(
            messages = danmakuMessages,
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background),
            hostName = uiState.detail?.userName ?: "",
            chatTextSize = chatTextSize,
            chatTextGap = chatTextGap,
            chatBubbleStyle = chatBubbleStyle
        )
    }
}

@Composable
private fun LiveRoomTabPage(
    tabType: LiveRoomTabType?,
    uiState: LiveRoomUiState,
    danmakuMessages: List<DisplayLiveMessage>,
    viewModel: LiveRoomViewModel,
    navigator: Navigator,
    settingsViewModel: SettingsViewModel,
    chatTextSize: Double,
    chatTextGap: Double,
    chatBubbleStyle: Boolean,
    superChatSortDesc: Boolean,
    modifier: Modifier = Modifier
) {
    when (tabType) {
        LiveRoomTabType.CHAT -> {
            ChatPanel(
                messages = danmakuMessages,
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
                modifier = modifier
            )
        }
        LiveRoomTabType.SETTINGS -> {
            RoomSettingsPanel(
                viewModel = settingsViewModel,
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
private fun LandscapeLayout(
    uiState: LiveRoomUiState,
    danmakuMessages: List<DisplayLiveMessage>,
    viewModel: LiveRoomViewModel,
    accentSiteId: String,
    navigator: Navigator,
    playerController: PlayerController?,
    activity: Activity?,
    onQualityClick: () -> Unit,
    isExiting: Boolean = false,
    onBack: () -> Unit = {}
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val danmuEnable by settingsViewModel.danmuEnable.collectAsState()
    val danmuSize by settingsViewModel.danmuSize.collectAsState()
    val danmuSpeed by settingsViewModel.danmuSpeed.collectAsState()
    val danmuArea by settingsViewModel.danmuArea.collectAsState()
    val danmuLineCount by settingsViewModel.danmuLineCount.collectAsState()
    val danmuDelay by settingsViewModel.danmuDelay.collectAsState()
    val danmuDelayBySiteJson by settingsViewModel.danmuDelayBySiteJson.collectAsState()
    val danmuOpacity by settingsViewModel.danmuOpacity.collectAsState()
    val danmuFontWeight by settingsViewModel.danmuFontWeight.collectAsState()
    val danmuStrokeWidth by settingsViewModel.danmuStrokeWidth.collectAsState()
    val danmuTopMargin by settingsViewModel.danmuTopMargin.collectAsState()
    val danmuBottomMargin by settingsViewModel.danmuBottomMargin.collectAsState()
    val danmuHideScroll by settingsViewModel.danmuHideScroll.collectAsState()
    val danmuDedupeEnable by settingsViewModel.danmuDedupeEnable.collectAsState()
    val danmuDedupeWindow by settingsViewModel.danmuDedupeWindow.collectAsState()
    val danmuDedupeStep by settingsViewModel.danmuDedupeStep.collectAsState()
    val danmuDedupeStrictMode by settingsViewModel.danmuDedupeStrictMode.collectAsState()
    val scaleMode by settingsViewModel.scaleMode.collectAsState()
    val playerCompatMode by settingsViewModel.playerCompatMode.collectAsState()
    val danmuRenderEmoji by settingsViewModel.danmuRenderEmoji.collectAsState()

    val chatTextSize by settingsViewModel.chatTextSize.collectAsState()
    val chatTextGap by settingsViewModel.chatTextGap.collectAsState()
    val chatBubbleStyle by settingsViewModel.chatBubbleStyle.collectAsState()
    val superChatSortDesc by settingsViewModel.superChatSortDesc.collectAsState()

    val liveRoomTabSort by settingsViewModel.liveRoomTabSort.collectAsState()
    val superChats by viewModel.superChats.collectAsState()
    val activeSuperChatCount = remember(superChats) {
        countActiveSuperChats(superChats, System.currentTimeMillis())
    }
    val roomTabs = remember(viewModel.siteId, liveRoomTabSort) {
        val list = mutableListOf<LiveRoomTabType>()
        list.add(LiveRoomTabType.CHAT)
        list.add(LiveRoomTabType.FOLLOW)
        list
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
    val landscapePagerState = rememberPagerState(initialPage = 0, pageCount = { roomTabs.size })
    val selectedLandscapeTab = landscapePagerState.currentPage.coerceIn(0, roomTabs.lastIndex.coerceAtLeast(0))

    val density = LocalDensity.current
    val panelWidthPx = remember { with(density) { 320.dp.toPx() } }
    val panelOffset = remember { Animatable(panelWidthPx) }
    val coroutineScope = rememberCoroutineScope()
    val showSidePanel = panelOffset.value < panelWidthPx
    val panelWidth = remember(panelOffset.value) {
        with(density) { (panelWidthPx - panelOffset.value).toDp() }
    }

    LaunchedEffect(roomTabs.size) {
        if (landscapePagerState.currentPage > roomTabs.lastIndex) {
            landscapePagerState.scrollToPage(roomTabs.lastIndex.coerceAtLeast(0))
        }
    }

    BackHandler(enabled = showSidePanel) {
        coroutineScope.launch {
            panelOffset.animateTo(
                targetValue = panelWidthPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    var danmakuController by remember { mutableStateOf<DanmakuController?>(null) }

    LaunchedEffect(danmakuController, isExiting) {
        if (danmakuController != null && !isExiting) {
            viewModel.newDanmakuMessages.collect { msg ->
                if (!isExiting) {
                    danmakuController?.addDanmaku(msg)
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Player (takes remaining space)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black)
        ) {
            val currentQualityName = uiState.playQualities.getOrNull(uiState.currentQualityIndex)?.quality ?: "画质"
            PlayerView(
                playerController = playerController,
                modifier = Modifier.fillMaxSize(),
                title = uiState.detail?.title ?: "",
                onBack = onBack,
                currentQualityName = currentQualityName,
                onQualityClick = onQualityClick,
                onRefreshClick = { viewModel.refreshPlay() },
                danmuEnable = danmuEnable,
                onDanmuToggle = { settingsViewModel.setDanmuEnable(it) },
                accentColor = roomPlatformAccentColor,
                danmuSize = danmuSize,
                onDanmuSizeChange = { settingsViewModel.setDanmuSize(it) },
                danmuSpeed = danmuSpeed,
                onDanmuSpeedChange = { settingsViewModel.setDanmuSpeed(it) },
                danmuArea = danmuArea,
                onDanmuAreaChange = { settingsViewModel.setDanmuArea(it) },
                danmuLineCount = danmuLineCount,
                danmuDelayMs = resolvedDanmuDelay,
                danmuOpacity = danmuOpacity,
                onDanmuOpacityChange = { settingsViewModel.setDanmuOpacity(it) },
                danmuFontWeight = danmuFontWeight,
                danmuStrokeWidth = danmuStrokeWidth,
                danmuTopMargin = danmuTopMargin,
                danmuBottomMargin = danmuBottomMargin,
                danmuHideScroll = danmuHideScroll,
                danmuDedupeEnable = danmuDedupeEnable,
                danmuDedupeWindow = danmuDedupeWindow,
                danmuDedupeStep = danmuDedupeStep,
                danmuDedupeStrictMode = danmuDedupeStrictMode,
                scaleMode = scaleMode,
                playerCompatMode = playerCompatMode,
                danmuRenderEmoji = danmuRenderEmoji,
                chatBubbleStyle = chatBubbleStyle,
                onChatBubbleStyleChange = { settingsViewModel.setChatBubbleStyle(it) },
                onDanmakuControllerCreated = { danmakuController = it },
                onHorizontalDragDelta = { deltaX ->
                    coroutineScope.launch {
                        val newOffset = (panelOffset.value + deltaX).coerceIn(0f, panelWidthPx)
                        panelOffset.snapTo(newOffset)
                    }
                },
                onHorizontalDragEnd = {
                    coroutineScope.launch {
                        val threshold = panelWidthPx * 0.7f
                        if (panelOffset.value < threshold) {
                            panelOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        } else {
                            panelOffset.animateTo(
                                targetValue = panelWidthPx,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    }
                }
            )
        }

        // Side panel (optional)
        if (showSidePanel) {
            Box(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(0.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .requiredWidth(320.dp)
                        .fillMaxHeight()
                ) {
            // Tab selection for Side Panel
            TabRow(
                selectedTabIndex = selectedLandscapeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = roomPlatformAccentColor,
                indicator = { tabPositions ->
                    if (selectedLandscapeTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedLandscapeTab]),
                            color = roomPlatformAccentColor
                        )
                    }
                }
            ) {
                roomTabs.forEachIndexed { index, tabType ->
                    val title = when (tabType) {
                        LiveRoomTabType.CHAT -> "聊天"
                        LiveRoomTabType.SUPER_CHAT -> {
                            val label = if (viewModel.siteId == "huya") "头条" else "SC"
                            if (activeSuperChatCount > 0) "$label($activeSuperChatCount)" else label
                        }
                        LiveRoomTabType.FOLLOW -> "关注"
                        LiveRoomTabType.SETTINGS -> "设置"
                    }
                    Tab(
                        selected = selectedLandscapeTab == index,
                        onClick = {
                            coroutineScope.launch {
                                val diff = index - landscapePagerState.currentPage
                                landscapePagerState.animateScrollToPage(
                                    page = index,
                                    animationSpec = AppMotion.pagerSpec(diff)
                                )
                            }
                        },
                        text = { Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)) }
                    )
                }
            }

            HorizontalPager(
                state = landscapePagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                LiveRoomTabPage(
                    tabType = roomTabs.getOrNull(page),
                    uiState = uiState,
                    danmakuMessages = danmakuMessages,
                    viewModel = viewModel,
                    navigator = navigator,
                    settingsViewModel = settingsViewModel,
                    chatTextSize = chatTextSize,
                    chatTextGap = chatTextGap,
                    chatBubbleStyle = chatBubbleStyle,
                    superChatSortDesc = superChatSortDesc,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
        } // end if (showSidePanel)
    }
}

// ── Room Info Bar ───────────────────────────────────────────────

@Composable
private fun CompactPortraitRoomHeader(
    detail: com.mylive.app.core.model.LiveRoomDetail?,
    isFollowing: Boolean,
    isFollowStatusKnown: Boolean,
    accentColor: Color,
    onToggleFollow: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onQuickAccessClick: () -> Unit
) {
    val toolbarIconTint = resolveLiveRoomToolbarIconTint(MaterialTheme.colorScheme.onSurface)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (detail == null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(15.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                        .padding(1.dp)
                ) {
                    AsyncImage(
                        model = detail.userAvatar,
                        contentDescription = detail.userName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.userName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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
            IconButton(onClick = onQuickAccessClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "快速入口",
                    tint = toolbarIconTint,
                    modifier = Modifier.size(18.dp)
                )
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
    IconButton(
        onClick = {
            if (enabled) {
                onClick()
            }
        },
        modifier = modifier
    ) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitAuxiliaryPanelSheet(
    panel: PortraitLiveRoomPanel,
    uiState: LiveRoomUiState,
    viewModel: LiveRoomViewModel,
    navigator: Navigator,
    settingsViewModel: SettingsViewModel,
    superChatSortDesc: Boolean,
    onDismiss: () -> Unit
) {
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = maxSheetHeight)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (panel) {
                        PortraitLiveRoomPanel.SUPER_CHAT -> if (viewModel.siteId == "huya") "头条" else "SC"
                        PortraitLiveRoomPanel.FOLLOW -> "关注列表"
                        PortraitLiveRoomPanel.SETTINGS -> "设置"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭"
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    PortraitLiveRoomPanel.SETTINGS -> {
                        RoomSettingsPanel(
                            viewModel = settingsViewModel,
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
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.room_quality_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider()

            qualities.forEachIndexed { index, quality ->
                val isSelected = index == currentIndex
                Surface(
                    onClick = { onSelect(index) },
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else Color.Transparent
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
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
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
    messages: List<DisplayLiveMessage>,
    modifier: Modifier = Modifier,
    hostName: String = "",
    chatTextSize: Double = 14.0,
    chatTextGap: Double = 4.0,
    chatBubbleStyle: Boolean = false
) {
    val listState = rememberLazyListState()
    var previousLastMessageId by remember { mutableStateOf<Long?>(null) }
    var autoScrollDisabled by remember { mutableStateOf(false) }
    var showLatestButton by remember { mutableStateOf(false) }
    var userTouchingChat by remember { mutableStateOf(false) }
    var displayMessages by remember { mutableStateOf<List<DisplayLiveMessage>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val isAtBottomNow by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            lastVisible == null || lastVisible.index >= info.totalItemsCount - 2
        }
    }
    val firstVisibleItemIndex = listState.firstVisibleItemIndex
    val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset

    // Key on the last message (not messages.size): once the ring buffer saturates at its cap,
    // size stays constant and a size-keyed effect would stop firing, freezing auto-scroll in
    // busy rooms. Auto-follow is controlled by user scroll state, matching dart_simple_live:
    // once the user scrolls away, new messages show the latest button instead of pulling back.
    LaunchedEffect(messages.lastOrNull()?.id, messages.size, autoScrollDisabled) {
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
        if (shouldAutoScrollChat(
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
        firstVisibleItemIndex,
        firstVisibleItemScrollOffset
    ) {
        autoScrollDisabled = reduceChatAutoScrollDisabled(
            currentDisabled = autoScrollDisabled,
            isNearBottom = isAtBottomNow,
            userScrolledAwayFromBottom = userTouchingChat && !isAtBottomNow
        )
        if (!autoScrollDisabled) {
            showLatestButton = false
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
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
            contentPadding = PaddingValues(vertical = 4.dp),
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

        AnimatedVisibility(
            visible = showLatestButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
        ) {
            SmallFloatingActionButton(
                modifier = Modifier.border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                    CircleShape
                ),
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
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 6.dp,
                    focusedElevation = 4.dp,
                    hoveredElevation = 4.dp
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
            val liveMessageColor = remember(message.color) {
                val r = (message.color.r / 255f).coerceIn(0f, 1f)
                val g = (message.color.g / 255f).coerceIn(0f, 1f)
                val b = (message.color.b / 255f).coerceIn(0f, 1f)
                Color(r, g, b)
            }
            val colorPolicy = remember(message.color) {
                resolveChatMessageColorPolicy(message.color)
            }
            val userNameColor = Color.Gray
            val messageTextColor = if (colorPolicy.applyMessageColorToText) {
                liveMessageColor
            } else {
                Color.Unspecified
            }

            val isHost = message.userName.isNotEmpty() && message.userName == hostName

            val (annotatedString, inlineContent) = remember(message, isHost, userNameColor, messageTextColor, isSystem) {
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
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE91E63),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 4.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "主播",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = userNameColor)) {
                        append(message.userName)
                    }
                    builder.withStyle(SpanStyle(color = Color.Gray)) {
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
                            color = Color.Gray
                        ),
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                    )
                } else {
                    Text(
                        text = annotatedString,
                        inlineContent = inlineContent,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = chatTextSize.sp,
                            lineHeight = (chatTextSize * 1.4).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }

            if (chatBubbleStyle && !isSystem) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        textContent()
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
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
                    .padding(vertical = 4.dp)
            ) {
                val gradient = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(gradient)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🎁 ", fontSize = 14.sp)
                        Text(
                            text = message.userName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = " ${message.message}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ── Host About Panel ────────────────────────────────────────────

@Composable
fun HostAboutPanel(
    detail: com.mylive.app.core.model.LiveRoomDetail?,
    modifier: Modifier = Modifier
) {
    detail ?: return
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Notice Card
        if (!detail.notice.isNullOrBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📢", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "主播公告",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = detail.notice!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Introduction Card
        if (!detail.introduction.isNullOrBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "关于主播",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = detail.introduction!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Stream Details Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "直播间信息",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    InfoRow(label = "直播房间号", value = detail.roomId)
                    detail.showTime?.let {
                        InfoRow(label = "开播时间", value = it)
                    }
                    InfoRow(
                        label = "当前状态",
                        value = if (detail.status) "直播中" else "未开播"
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(topColor)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = item.face,
                    contentDescription = item.userName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.userName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF333333),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "￥${item.price}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
                Text(
                    text = "$countdown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bottomColor)
                    .padding(8.dp)
            ) {
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
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
    val superChats by viewModel.superChats.collectAsState()

    // Trigger cleanup of expired super chats every second
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.removeExpiredSuperChats()
            kotlinx.coroutines.delay(1000L)
        }
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(sortedChats, key = { it.id ?: "${it.userName}_${it.startTime}_${it.message.hashCode()}" }) { item ->
                SuperChatCard(item = item)
            }
        }
    }
}

// ── Follow User Item ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUserItem(
    item: FollowUserEntity,
    isPlaying: Boolean,
    onTap: () -> Unit
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

                Spacer(modifier = Modifier.height(4.dp))

                // Room ID
                Text(
                    text = "房间号: ${item.roomId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Live status indicator badge
            val statusText = if (item.liveStatus == 1) "直播中" else "未开播"
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
    viewModel: FollowViewModel = hiltViewModel(),
    currentRoomId: String,
    currentSiteId: String,
    modifier: Modifier = Modifier
) {
    val follows by viewModel.follows.collectAsState()
    val updating by viewModel.updatingStatus.collectAsState()

    var filterIndex by remember { mutableIntStateOf(0) } // 0: 全部, 1: 直播中, 2: 未开播

    // Refresh follow status on panel load
    LaunchedEffect(Unit) {
        viewModel.updateFollowStatus()
    }

    val filteredFollows = remember(follows, filterIndex) {
        when (filterIndex) {
            1 -> follows.filter { it.liveStatus == 1 }
            2 -> follows.filter { it.liveStatus == 2 }
            else -> follows
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        // Filter options row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
        }

        if (filteredFollows.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredFollows) { item ->
                    val isPlaying = item.roomId == currentRoomId && item.siteId == currentSiteId
                    FollowUserItem(
                        item = item,
                        isPlaying = isPlaying,
                        onTap = {
                            navigator.navigate(
                                Route.LiveRoomDetail(
                                    roomId = item.roomId,
                                    siteId = item.siteId,
                                    initialIsFollowing = true
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
    viewModel: SettingsViewModel = hiltViewModel(),
    isHuyaOrBilibili: Boolean,
    siteId: String = "",
    onOpenShieldSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chatTextSize by viewModel.chatTextSize.collectAsState()
    val chatTextGap by viewModel.chatTextGap.collectAsState()
    val chatBubbleStyle by viewModel.chatBubbleStyle.collectAsState()
    val superChatSortDesc by viewModel.superChatSortDesc.collectAsState()
    val danmuEnable by viewModel.danmuEnable.collectAsState()
    val danmuRenderEmoji by viewModel.danmuRenderEmoji.collectAsState()
    val danmuSize by viewModel.danmuSize.collectAsState()
    val danmuSpeed by viewModel.danmuSpeed.collectAsState()
    val danmuArea by viewModel.danmuArea.collectAsState()
    val danmuLineCount by viewModel.danmuLineCount.collectAsState()
    val danmuDelay by viewModel.danmuDelay.collectAsState()
    val danmuDelayBySiteJson by viewModel.danmuDelayBySiteJson.collectAsState()
    val danmuOpacity by viewModel.danmuOpacity.collectAsState()
    val danmuStrokeWidth by viewModel.danmuStrokeWidth.collectAsState()
    val danmuTopMargin by viewModel.danmuTopMargin.collectAsState()
    val danmuBottomMargin by viewModel.danmuBottomMargin.collectAsState()
    val danmuDedupeEnable by viewModel.danmuDedupeEnable.collectAsState()
    val danmuDedupeWindow by viewModel.danmuDedupeWindow.collectAsState()
    val danmuDedupeStep by viewModel.danmuDedupeStep.collectAsState()
    val danmuDedupeStrictMode by viewModel.danmuDedupeStrictMode.collectAsState()
    val danmuShieldEnable by viewModel.danmuShieldEnable.collectAsState()
    val danmuKeywordShieldEnable by viewModel.danmuKeywordShieldEnable.collectAsState()
    val danmuUserShieldEnable by viewModel.danmuUserShieldEnable.collectAsState()
    val autoFullScreen by viewModel.autoFullScreen.collectAsState()
    val autoPipOnExit by viewModel.autoPipOnExit.collectAsState()
    val pipHideDanmu by viewModel.pipHideDanmu.collectAsState()
    val normalizedDanmuSize = roomSettingsNormalizedDanmuSize(danmuSize)
    val normalizedDanmuSpeed = roomSettingsNormalizedDanmuSpeed(danmuSpeed)
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
        expandedSectionNames = if (expanded) {
            (expandedSectionNames + sectionKey.name).distinct()
        } else {
            expandedSectionNames.filterNot { it == sectionKey.name }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            RoomSettingsCollapsibleSection(
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSwitchRow(
                        title = "渲染表情",
                        subtitle = "在播放器弹幕中显示平台表情图片",
                        checked = danmuRenderEmoji,
                        onCheckedChange = { viewModel.setDanmuRenderEmoji(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSliderRow(
                        title = "弹幕字号",
                        valueText = roomSettingsMultiplierText(normalizedDanmuSize),
                        value = normalizedDanmuSize.toFloat(),
                        onValueChange = { viewModel.setDanmuSize(it.toDouble()) },
                        valueRange = 0.8f..1.5f,
                        steps = 6
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSliderRow(
                        title = "弹幕速度",
                        valueText = roomSettingsMultiplierText(normalizedDanmuSpeed),
                        value = normalizedDanmuSpeed.toFloat(),
                        onValueChange = { viewModel.setDanmuSpeed(it.toDouble()) },
                        valueRange = 0.6f..1.5f,
                        steps = 8
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSliderRow(
                        title = "显示区域",
                        valueText = roomSettingsPercentText(danmuArea),
                        value = danmuArea.toFloat(),
                        onValueChange = { viewModel.setDanmuArea(it.toDouble()) },
                        valueRange = 0.25f..1.0f,
                        steps = 14
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSliderRow(
                        title = "显示几行",
                        valueText = if (danmuLineCount <= 0) "不显示" else "$danmuLineCount 行",
                        value = danmuLineCount.toFloat(),
                        onValueChange = { viewModel.setDanmuLineCount(it.toInt()) },
                        valueRange = 0f..12f,
                        steps = 11
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSliderRow(
                        title = "弹幕透明度",
                        valueText = roomSettingsPercentText(danmuOpacity),
                        value = danmuOpacity.toFloat(),
                        onValueChange = { viewModel.setDanmuOpacity(it.toDouble()) },
                        valueRange = 0.2f..1.0f,
                        steps = 7
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSliderRow(
                        title = "弹幕描边",
                        valueText = roomSettingsStrokeText(danmuStrokeWidth),
                        value = danmuStrokeWidth.toFloat(),
                        onValueChange = { viewModel.setDanmuStrokeWidth(it.toDouble()) },
                        valueRange = 0f..4f,
                        steps = 7
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSliderRow(
                        title = "全局弹幕延迟",
                        valueText = "${danmuDelay.toInt()} ms",
                        value = danmuDelay.toFloat(),
                        onValueChange = { viewModel.setDanmuDelay(it.toDouble()) },
                        valueRange = 0f..5000f,
                        steps = 49
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSliderRow(
                        title = "顶部安全边距",
                        valueText = "${danmuTopMargin.toInt()} dp",
                        value = danmuTopMargin.toFloat(),
                        onValueChange = { viewModel.setDanmuTopMargin(it.toDouble()) },
                        valueRange = 0f..48f,
                        steps = 11
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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
        }

        item {
            RoomSettingsCollapsibleSection(
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSwitchRow(
                        title = "退出时自动小窗",
                        subtitle = "按 Home 键或系统手势退到后台时进入小窗",
                        checked = autoPipOnExit,
                        onCheckedChange = { viewModel.setAutoPipOnExit(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSwitchRow(
                        title = "进入小窗隐藏弹幕",
                        subtitle = "小窗播放时隐藏播放器滚动弹幕",
                        checked = pipHideDanmu,
                        onCheckedChange = { viewModel.setPipHideDanmu(it) }
                    )
                }
            }
        }

        item {
            RoomSettingsCollapsibleSection(
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
                            Text(text = "文字大小", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = "${chatTextSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        RoomSettingsSlider(
                            value = chatTextSize.toFloat(),
                            onValueChange = { viewModel.setChatTextSize(it.toDouble()) },
                            valueRange = 8f..36f,
                            steps = 27
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Gap slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "上下间隔", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = "${chatTextGap.toInt()} dp", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        RoomSettingsSlider(
                            value = chatTextGap.toFloat(),
                            onValueChange = { viewModel.setChatTextGap(it.toDouble()) },
                            valueRange = 0f..12f,
                            steps = 11
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Bubble style switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "气泡样式", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = "使用类似社交聊天的气泡框渲染弹幕", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = chatBubbleStyle,
                            onCheckedChange = { viewModel.setChatBubbleStyle(it) }
                        )
                    }

                    if (isHuyaOrBilibili) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // SC sort desc
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "SC排序", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
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
        }

        item {
            RoomSettingsCollapsibleSection(
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSwitchRow(
                        title = "关键词屏蔽",
                        subtitle = "按普通文本或 /.../ 正则过滤聊天弹幕",
                        checked = danmuKeywordShieldEnable,
                        onCheckedChange = { viewModel.setDanmuKeywordShieldEnable(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RoomSettingsSwitchRow(
                        title = "用户屏蔽",
                        subtitle = "按全平台或当前平台用户名过滤聊天弹幕",
                        checked = danmuUserShieldEnable,
                        onCheckedChange = { viewModel.setDanmuUserShieldEnable(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenShieldSettings),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "打开屏蔽管理", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
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
        }

        item {
            RoomSettingsCollapsibleSection(
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
                            Text(text = "重复弹幕过滤", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = "同一用户在最近若干条内重复刷同一句时只显示一次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = danmuDedupeEnable,
                            onCheckedChange = { viewModel.setDanmuDedupeEnable(it) }
                        )
                    }

                    if (danmuDedupeEnable) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Strict deduplication mode switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "严格去重模式", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(text = "全局过滤不同观众发送的相同弹幕（不区分发送人）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = danmuDedupeStrictMode,
                                onCheckedChange = { viewModel.setDanmuDedupeStrictMode(it) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Deduplicate window slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "过滤窗口", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(text = "${danmuDedupeWindow} 条", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            RoomSettingsSlider(
                                value = danmuDedupeWindow.toFloat(),
                                onValueChange = { viewModel.setDanmuDedupeWindow(it.toInt()) },
                                valueRange = 1f..100f,
                                steps = 98
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "过滤步长", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(text = "$danmuDedupeStep 条", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            RoomSettingsSlider(
                                value = danmuDedupeStep.toFloat(),
                                onValueChange = { viewModel.setDanmuDedupeStep(it.toInt()) },
                                valueRange = 1f..20f,
                                steps = 18
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomSettingsCollapsibleSection(
    sectionKey: RoomSettingsSectionKey,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180, easing = EaseOutCubic),
        label = "roomSettingsSectionArrow_${sectionKey.name}"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = 220, easing = EaseOutCubic)
            ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { onExpandedChange(!expanded) })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起$title" else "展开$title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    rotationZ = arrowRotation
                }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 220, easing = EaseOutCubic),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(durationMillis = 140)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 180, easing = EaseOutCubic),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(durationMillis = 120))
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(content = content)
            }
        }
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
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
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
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(text = valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        RoomSettingsSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

private fun roomSettingsNormalizedDanmuSize(value: Double): Double =
    if (value <= 2.0) value.coerceIn(0.8, 1.5) else (value / 16.0).coerceIn(0.8, 1.5)

private fun roomSettingsNormalizedDanmuSpeed(value: Double): Double =
    if (value > 5.0) 1.0 else value.coerceIn(0.6, 1.5)

private fun roomSettingsMultiplierText(value: Double): String =
    "${roomSettingsOneDecimal(value)}x"

private fun roomSettingsPercentText(value: Double): String =
    "${(value.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

private fun roomSettingsStrokeText(value: Double): String =
    if (value <= 0.0) "无" else "${roomSettingsOneDecimal(value)} px"

private fun roomSettingsOneDecimal(value: Double): String {
    val rounded = (value * 10).roundToInt()
    return "${rounded / 10}.${kotlin.math.abs(rounded % 10)}"
}

@Composable
private fun RoomSettingsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
    val thumbRingColor = MaterialTheme.colorScheme.surface
    val valueStart = valueRange.start
    val valueEnd = valueRange.endInclusive
    val rangeSize = (valueEnd - valueStart).takeIf { it > 0f } ?: 1f
    val coercedValue = value.coerceIn(valueStart, valueEnd)
    val fraction = ((coercedValue - valueStart) / rangeSize).coerceIn(0f, 1f)
    val density = LocalDensity.current
    val horizontalInset = 13.dp
    val horizontalInsetPx = with(density) { horizontalInset.toPx() }
    var widthPx by remember { mutableFloatStateOf(1f) }

    fun commitPosition(x: Float) {
        val usableWidth = (widthPx - horizontalInsetPx * 2f).coerceAtLeast(1f)
        val rawFraction = ((x - horizontalInsetPx) / usableWidth).coerceIn(0f, 1f)
        onValueChange(roomSettingsSliderValueForFraction(rawFraction, valueRange, steps))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(coercedValue, valueRange, steps)
                setProgress { target ->
                    onValueChange(roomSettingsSliderSnapValue(target, valueRange, steps))
                    true
                }
            }
            .pointerInput(valueRange, steps, widthPx) {
                detectTapGestures { offset -> commitPosition(offset.x) }
            }
            .pointerInput(valueRange, steps, widthPx) {
                detectDragGestures(
                    onDragStart = { offset -> commitPosition(offset.x) },
                    onDrag = { change, _ ->
                        commitPosition(change.position.x)
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
            val trackStroke = 6.dp.toPx()
            val thumbRadius = 7.dp.toPx()
            val thumbRingRadius = 10.dp.toPx()
            val thumbHaloRadius = 15.dp.toPx()

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
                color = activeColor.copy(alpha = 0.16f),
                radius = thumbHaloRadius,
                center = Offset(activeX, centerY)
            )
            drawCircle(
                color = thumbRingColor,
                radius = thumbRingRadius,
                center = Offset(activeX, centerY)
            )
            drawCircle(
                color = activeColor,
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
