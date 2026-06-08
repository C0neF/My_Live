package com.mylive.app.ui.screen.room

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clipToBounds
import com.mylive.app.ui.screen.follow.FollowViewModel
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.motion.AppMotion
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOutCubic
import kotlinx.coroutines.launch
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicLong


enum class LiveRoomTabType {
    CHAT, SUPER_CHAT, FOLLOW, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRoomScreen(
    navigator: Navigator,
    key: Route.LiveRoomDetail
) {
    val viewModel: LiveRoomViewModel = hiltViewModel()
    LaunchedEffect(key.roomId, key.siteId) {
        viewModel.openRoute(key.roomId, key.siteId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val danmakuMessages by viewModel.danmakuMessages.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val activity = context as? Activity

    var isExiting by remember { mutableStateOf(false) }
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scaleMode by settingsViewModel.scaleMode.collectAsState()
    val playerCompatMode by settingsViewModel.playerCompatMode.collectAsState()

    val componentActivity = context as? androidx.activity.ComponentActivity
    var isInPip by remember { mutableStateOf(componentActivity?.isInPictureInPictureMode == true) }

    DisposableEffect(componentActivity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPip = info.isInPictureInPictureMode
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    val autoPipOnExit by settingsViewModel.autoPipOnExit.collectAsState(initial = false)
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
            onNavigateToRoom = { siteId, roomId ->
                showQuickAccess = false
                navigator.navigate(
                    Route.LiveRoomDetail(roomId = roomId, siteId = siteId),
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
                danmuEnable = false,
                onDanmuToggle = {},
                danmuSize = 16.0,
                onDanmuSizeChange = {},
                danmuSpeed = 10.0,
                onDanmuSpeedChange = {},
                danmuArea = 0.8,
                onDanmuAreaChange = {},
                danmuOpacity = 1.0,
                onDanmuOpacityChange = {},
                danmuFontWeight = 4,
                danmuStrokeWidth = 2.0,
                danmuHideScroll = false,
                danmuDedupeEnable = false,
                danmuDedupeWindow = 20,
                danmuDedupeStrictMode = false,
                scaleMode = scaleMode,
                playerCompatMode = playerCompatMode,
                danmuRenderEmoji = false,
                onDanmakuControllerCreated = {},
                isExiting = isExiting
            )
        } else if (isLandscape || isFullscreen) {
            LandscapeLayout(
                uiState = uiState,
                danmakuMessages = danmakuMessages,
                viewModel = viewModel,
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
    val danmuOpacity by settingsViewModel.danmuOpacity.collectAsState()
    val danmuFontWeight by settingsViewModel.danmuFontWeight.collectAsState()
    val danmuStrokeWidth by settingsViewModel.danmuStrokeWidth.collectAsState()
    val danmuHideScroll by settingsViewModel.danmuHideScroll.collectAsState()
    val danmuDedupeEnable by settingsViewModel.danmuDedupeEnable.collectAsState()
    val danmuDedupeWindow by settingsViewModel.danmuDedupeWindow.collectAsState()
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
    val roomTabs = remember(viewModel.siteId, superChats.size, liveRoomTabSort) {
        val defaultList = listOf("chat", "super_chat", "follow", "settings")
        val sortedKeys = liveRoomTabSort.split(",").filter { it.isNotBlank() }
        val orderedKeys = (sortedKeys + defaultList).distinct()
        
        val list = mutableListOf<LiveRoomTabType>()
        orderedKeys.forEach { key ->
            when (key) {
                "chat" -> list.add(LiveRoomTabType.CHAT)
                "super_chat" -> {
                    if (viewModel.siteId == "bilibili" || viewModel.siteId == "huya") {
                        list.add(LiveRoomTabType.SUPER_CHAT)
                    }
                }
                "follow" -> list.add(LiveRoomTabType.FOLLOW)
                "settings" -> list.add(LiveRoomTabType.SETTINGS)
            }
        }
        list
    }
    val tabPagerState = rememberPagerState(initialPage = 0, pageCount = { roomTabs.size })
    val tabCoroutineScope = rememberCoroutineScope()
    val selectedTab = tabPagerState.currentPage.coerceIn(0, roomTabs.lastIndex.coerceAtLeast(0))

    LaunchedEffect(roomTabs.size) {
        if (tabPagerState.currentPage > roomTabs.lastIndex) {
            tabPagerState.scrollToPage(roomTabs.lastIndex.coerceAtLeast(0))
        }
    }

    var danmakuController by remember { mutableStateOf<DanmakuController?>(null) }
    var showQuickAccess by remember { mutableStateOf(false) }

    if (showQuickAccess) {
        com.mylive.app.ui.screen.room.quickaccess.QuickAccessPanel(
            currentSiteId = viewModel.siteId,
            currentRoomId = viewModel.roomId,
            currentCategoryId = uiState.detail?.categoryId,
            onNavigateToRoom = { siteId, roomId ->
                showQuickAccess = false
                navigator.navigate(
                    Route.LiveRoomDetail(roomId = roomId, siteId = siteId),
                    singleTop = true,
                    popUpToRoute = Route.Index::class.java,
                    inclusive = false
                )
            },
            onDismiss = { showQuickAccess = false }
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
                danmuSize = danmuSize,
                onDanmuSizeChange = { settingsViewModel.setDanmuSize(it) },
                danmuSpeed = danmuSpeed,
                onDanmuSpeedChange = { settingsViewModel.setDanmuSpeed(it) },
                danmuArea = danmuArea,
                onDanmuAreaChange = { settingsViewModel.setDanmuArea(it) },
                danmuOpacity = danmuOpacity,
                onDanmuOpacityChange = { settingsViewModel.setDanmuOpacity(it) },
                danmuFontWeight = danmuFontWeight,
                danmuStrokeWidth = danmuStrokeWidth,
                danmuHideScroll = danmuHideScroll,
                danmuDedupeEnable = danmuDedupeEnable,
                danmuDedupeWindow = danmuDedupeWindow,
                danmuDedupeStrictMode = danmuDedupeStrictMode,
                scaleMode = scaleMode,
                playerCompatMode = playerCompatMode,
                danmuRenderEmoji = danmuRenderEmoji,
                onDanmakuControllerCreated = { danmakuController = it },
                isExiting = isExiting
            )
        }

        // Room info bar
        RoomInfoBar(
            detail = uiState.detail,
            onlineCount = uiState.onlineCount,
            isFollowing = uiState.isFollowing,
            onToggleFollow = { viewModel.toggleFollow() },
            onRefreshClick = { viewModel.refreshPlay() },
            onQuickAccessClick = { showQuickAccess = true }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)



        // Tab selection
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            roomTabs.forEachIndexed { index, tabType ->
                val title = when (tabType) {
                    LiveRoomTabType.CHAT -> "聊天"
                    LiveRoomTabType.SUPER_CHAT -> {
                        val label = if (viewModel.siteId == "huya") "头条" else "SC"
                        if (superChats.isNotEmpty()) "$label(${superChats.size})" else label
                    }
                    LiveRoomTabType.FOLLOW -> "关注"
                    LiveRoomTabType.SETTINGS -> "设置"
                }
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        tabCoroutineScope.launch {
                            val diff = index - tabPagerState.currentPage
                            tabPagerState.animateScrollToPage(
                                page = index,
                                animationSpec = AppMotion.pagerSpec(diff)
                            )
                        }
                    },
                    text = { Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)) }
                )
            }
        }

        HorizontalPager(
            state = tabPagerState,
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
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
    val danmuOpacity by settingsViewModel.danmuOpacity.collectAsState()
    val danmuFontWeight by settingsViewModel.danmuFontWeight.collectAsState()
    val danmuStrokeWidth by settingsViewModel.danmuStrokeWidth.collectAsState()
    val danmuHideScroll by settingsViewModel.danmuHideScroll.collectAsState()
    val danmuDedupeEnable by settingsViewModel.danmuDedupeEnable.collectAsState()
    val danmuDedupeWindow by settingsViewModel.danmuDedupeWindow.collectAsState()
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
    val roomTabs = remember(viewModel.siteId, superChats.size, liveRoomTabSort) {
        val defaultList = listOf("chat", "super_chat", "follow", "settings")
        val sortedKeys = liveRoomTabSort.split(",").filter { it.isNotBlank() }
        val orderedKeys = (sortedKeys + defaultList).distinct()
        
        val list = mutableListOf<LiveRoomTabType>()
        orderedKeys.forEach { key ->
            when (key) {
                "chat" -> list.add(LiveRoomTabType.CHAT)
                "super_chat" -> {
                    if (viewModel.siteId == "bilibili" || viewModel.siteId == "huya") {
                        list.add(LiveRoomTabType.SUPER_CHAT)
                    }
                }
                "follow" -> list.add(LiveRoomTabType.FOLLOW)
                "settings" -> list.add(LiveRoomTabType.SETTINGS)
            }
        }
        list
    }
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
                danmuSize = danmuSize,
                onDanmuSizeChange = { settingsViewModel.setDanmuSize(it) },
                danmuSpeed = danmuSpeed,
                onDanmuSpeedChange = { settingsViewModel.setDanmuSpeed(it) },
                danmuArea = danmuArea,
                onDanmuAreaChange = { settingsViewModel.setDanmuArea(it) },
                danmuOpacity = danmuOpacity,
                onDanmuOpacityChange = { settingsViewModel.setDanmuOpacity(it) },
                danmuFontWeight = danmuFontWeight,
                danmuStrokeWidth = danmuStrokeWidth,
                danmuHideScroll = danmuHideScroll,
                danmuDedupeEnable = danmuDedupeEnable,
                danmuDedupeWindow = danmuDedupeWindow,
                danmuDedupeStrictMode = danmuDedupeStrictMode,
                scaleMode = scaleMode,
                playerCompatMode = playerCompatMode,
                danmuRenderEmoji = danmuRenderEmoji,
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
            // User info header in landscape side panel (beautifully refined)
            uiState.detail?.let { detail ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = detail.userName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (uiState.onlineCount > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "👁 ${uiState.onlineCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.toggleFollow() }) {
                        Icon(
                            imageVector = if (uiState.isFollowing) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (uiState.isFollowing) stringResource(R.string.room_following)
                            else stringResource(R.string.room_follow),
                            tint = if (uiState.isFollowing) Color.Red else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            // Tab selection for Side Panel
            TabRow(
                selectedTabIndex = selectedLandscapeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedLandscapeTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedLandscapeTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                roomTabs.forEachIndexed { index, tabType ->
                    val title = when (tabType) {
                        LiveRoomTabType.CHAT -> "聊天"
                        LiveRoomTabType.SUPER_CHAT -> {
                            val label = if (viewModel.siteId == "huya") "头条" else "SC"
                            if (superChats.isNotEmpty()) "$label(${superChats.size})" else label
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
private fun RoomInfoBar(
    detail: com.mylive.app.core.model.LiveRoomDetail?,
    onlineCount: Int,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onRefreshClick: () -> Unit,
    onQuickAccessClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (detail == null) {
            // Placeholder/Skeleton state
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }

            // Refresh placeholder
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Follow placeholder
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        } else {
            // Normal state
            // Circular host avatar with premium border
            Box(
                modifier = Modifier
                    .size(36.dp)
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

            // Host name + Online count stacked vertically
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.userName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (onlineCount > 0) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "👁 $onlineCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Actions: Refresh + Follow
            TextButton(
                onClick = onRefreshClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "刷新",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            FilledTonalButton(
                onClick = onToggleFollow,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = if (isFollowing) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFollowing) stringResource(R.string.room_following)
                    else stringResource(R.string.room_follow),
                    modifier = Modifier.size(14.dp),
                    tint = if (isFollowing) Color.Red else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isFollowing) stringResource(R.string.room_following)
                    else stringResource(R.string.room_follow),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
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
                shape = CircleShape
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
            val userNameColor = if (colorPolicy.applyMessageColorToUserName) {
                liveMessageColor
            } else {
                Color.Unspecified
            }
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
                            imageUrls = message.imageUrls
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
                        shape = RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 12.dp,
                            bottomStart = 12.dp,
                            bottomEnd = 12.dp
                        ),
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
fun SuperChatCard(item: LiveSuperChatMessage) {
    val cardColor = remember(item.backgroundColor) {
        runCatching {
            Color(android.graphics.Color.parseColor(item.backgroundColor))
        }.getOrDefault(Color(0xFFFFD600))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.face.isNotEmpty()) {
                            AsyncImage(
                                model = item.face,
                                contentDescription = item.userName,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = item.userName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "￥${item.price}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            // Message Body
            if (item.message.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardColor.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = item.message,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
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
    val superChats by viewModel.superChats.collectAsState()

    // Trigger cleanup of expired super chats every second
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.removeExpiredSuperChats()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val sortedChats = remember(superChats, superChatSortDesc) {
        if (superChatSortDesc) {
            superChats.sortedByDescending { it.endTime }
        } else {
            superChats.sortedBy { it.endTime }
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
                                Route.LiveRoomDetail(roomId = item.roomId, siteId = item.siteId),
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
    modifier: Modifier = Modifier
) {
    val chatTextSize by viewModel.chatTextSize.collectAsState()
    val chatTextGap by viewModel.chatTextGap.collectAsState()
    val chatBubbleStyle by viewModel.chatBubbleStyle.collectAsState()
    val playerShowSuperChat by viewModel.playerShowSuperChat.collectAsState()
    val superChatSortDesc by viewModel.superChatSortDesc.collectAsState()
    val danmuDedupeEnable by viewModel.danmuDedupeEnable.collectAsState()
    val danmuDedupeWindow by viewModel.danmuDedupeWindow.collectAsState()
    val danmuDedupeStrictMode by viewModel.danmuDedupeStrictMode.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "聊天区设置",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        Slider(
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
                        Slider(
                            value = chatTextGap.toFloat(),
                            onValueChange = { viewModel.setChatTextGap(it.toDouble()) },
                            valueRange = 0f..12f,
                            steps = 12
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

                        // Player show SC switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "播放器中显示SC", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(text = "开启后在播放区域也显示SC悬浮栏", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = playerShowSuperChat,
                                onCheckedChange = { viewModel.setPlayerShowSuperChat(it) }
                            )
                        }

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
            Text(
                text = "弹幕过滤设置",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                Text(text = "严父去重模式", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
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
                            Slider(
                                value = danmuDedupeWindow.toFloat(),
                                onValueChange = { viewModel.setDanmuDedupeWindow(it.toInt()) },
                                valueRange = 1f..100f,
                                steps = 99
                            )
                        }
                    }
                }
            }
        }
    }
}
