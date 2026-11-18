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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PortraitLayout(
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
