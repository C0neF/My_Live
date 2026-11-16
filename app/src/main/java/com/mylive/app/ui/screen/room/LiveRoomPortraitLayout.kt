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
    AdaptiveLiveRoomLayout(
        splitPane = tabletSplit,
        chatWidthDp = LocalAppLayout.current.liveChatWidthDp,
        limitStackedPlayerHeight = LocalAppLayout.current.tablet,
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

        },
        chat = { chatModifier ->
        ChatPanel(
            messagesFlow = viewModel.danmakuMessages,
            noticeFlow = viewModel.danmakuNotice,
            modifier = chatModifier.testTag("live_room_chat").background(
                if (tabletSplit) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background
            ),
            hostName = uiState.detail?.userName ?: "",
            chatTextSize = chatTextSize,
            chatTextGap = chatTextGap,
            chatBubbleStyle = chatBubbleStyle,
            playerResizing = playerResizeState.isResizing
        )
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

