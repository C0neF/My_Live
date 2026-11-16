package com.mylive.app.ui.screen.room.player

import android.app.Activity
import android.graphics.Rect
import android.os.SystemClock
import android.view.LayoutInflater
import com.mylive.app.R
import com.mylive.app.core.model.DanmakuSettingsPolicy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.mylive.app.BuildConfig
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import com.mylive.app.ui.layout.LocalAppLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView as Media3PlayerView
import com.mylive.app.core.model.LivePlayQuality
import kotlinx.coroutines.delay

internal const val SystemVolumeGestureSensitivity = 2f

internal const val SystemVolumeGestureThrottleMs = 200L

internal fun systemVolumeForVerticalDrag(
    startVolume: Float,
    totalDragY: Float,
    heightPx: Float
): Float {
    val delta = -totalDragY / heightPx.coerceAtLeast(1f) * SystemVolumeGestureSensitivity
    return (startVolume + delta).coerceIn(0f, 1f)
}

internal fun quantizeSystemVolumeGesture(volume: Float): Float {
    val quantizedPercent = (
        volume.coerceIn(0f, 1f) * 100f / 5f
    ).roundToInt() * 5f
    return (quantizedPercent / 100f).coerceIn(0f, 1f)
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@AndroidXOptIn(UnstableApi::class)
@Composable
fun PlayerView(
    playerController: PlayerController?,
    viewState: PlayerViewUiState,
    modifier: Modifier = Modifier,
    actions: PlayerViewActions = PlayerViewActions()
) {
    val stateFlow = playerController?.state
    val state by (stateFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(PlayerState()) })
    val danmakuConfig = viewState.danmaku
    val danmakuActions = actions.danmaku
    var controlsVisible by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }

    // Screen Lock System state
    var isLocked by remember { mutableStateOf(false) }

    // Sheets visibility states
    var showLineSheet by remember { mutableStateOf(false) }
    var showDanmuSettingsSheet by remember { mutableStateOf(false) }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }
    var diagnosticsSnapshot by remember { mutableStateOf<PlaybackDiagnosticSnapshot?>(null) }
    var diagnosticDanmakuController by remember { mutableStateOf<DanmakuController?>(null) }
    LaunchedEffect(showDiagnosticsSheet, playerController, viewState.currentQualityName, diagnosticDanmakuController) {
        if (!showDiagnosticsSheet) return@LaunchedEffect
        while (true) {
            diagnosticsSnapshot = playerController?.playbackDiagnostics(viewState.currentQualityName)
                ?.withDanmaku(diagnosticDanmakuController?.diagnostics())
            delay(1000)
        }
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val resolvedAccentColor = viewState.accentColor ?: MaterialTheme.colorScheme.primary

    val isFullscreen = viewState.fullscreenOverride ?: state.isFullscreen
    // Buffering still has an active play request. EOF/idle can retain playWhenReady=true,
    // but their control remains Play so the existing retry behavior stays available.
    val showPauseButton = state.error == null && (
        state.isPlaying || state.playWhenReady && (
            state.playbackState == Player.STATE_READY || state.playbackState == Player.STATE_BUFFERING
        )
    )

    // Intercept back key when in fullscreen to exit fullscreen first (still allowed while locked).
    BackHandler(enabled = isFullscreen) {
        actions.onFullscreenClick?.invoke() ?: playerController?.toggleFullscreen()
    }

    LaunchedEffect(isLocked) {
        actions.onControlsLockChange?.invoke(isLocked)
    }

    DisposableEffect(actions.onControlsLockChange) {
        onDispose {
            actions.onControlsLockChange?.invoke(false)
        }
    }

    // Leave lock state when back to pure portrait non-fullscreen player.
    LaunchedEffect(isFullscreen, isPortrait) {
        if (!isFullscreen && isPortrait && isLocked) {
            isLocked = false
        }
    }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls, state.isPlaying, isLocked) {
        if (showControls && state.isPlaying) {
            delay(3000)
            showControls = false
            controlsVisible = false
        }
    }

    val resolvedDanmakuConfig = remember(danmakuConfig) {
        danmakuConfig.normalized()
    }

    val currentLineIndex = state.currentLineIndex
    val urls = state.urls
    val currentLineName = if (urls.isNotEmpty()) "线路 ${currentLineIndex + 1}" else "线路"

    // Danmaku quick settings and inline controls read primary/primaryContainer from
    // the theme, so wrap the entire player content once to propagate the platform accent.
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(primary = resolvedAccentColor)
    ) {
    Box(modifier = modifier) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .semantics {
                contentDescription = if (state.firstFrameRendered) "直播画面已显示" else "等待直播画面"
                testTagsAsResourceId = true
            }
            .testTag(if (state.firstFrameRendered) "player_video_rendered" else "player_video_waiting")
    ) {
        // Video surface
        val player = playerController?.player
        if (player != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val playerModifier = when (viewState.scaleMode) {
                    3 -> Modifier.aspectRatio(4f / 3f)
                    4 -> Modifier.aspectRatio(16f / 9f)
                    else -> if (!isFullscreen && isPortraitVideo(state.videoAspectRatio)) {
                        Modifier.aspectRatio(requireNotNull(state.videoAspectRatio))
                    } else {
                        Modifier.fillMaxSize()
                    }
                }

                key(viewState.playerCompatMode) {
                    AndroidView(
                        factory = { ctx ->
                            val layoutRes = if (viewState.playerCompatMode) {
                                R.layout.player_surface_view
                            } else {
                                R.layout.player_texture_view
                            }
                            LayoutInflater.from(ctx).inflate(layoutRes, null) as Media3PlayerView
                        },
                        update = { view ->
                            view.player = player
                            view.resizeMode = when (viewState.scaleMode) {
                                1 -> 3 // RESIZE_MODE_FILL
                                2 -> 4 // RESIZE_MODE_ZOOM
                                else -> 0 // RESIZE_MODE_FIT
                            }
                        },
                        // Detach player from the platform view before the AndroidView is
                        // destroyed (layout mode / compat surface swap / leave room).
                        onRelease = { view ->
                            view.player = null
                        },
                        // Publish the video bounds so the system can animate picture-in-picture
                        // from the actual video rather than cross-fading the whole window.
                        modifier = playerModifier.testTag("player_native_surface").onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            LivePlaybackHostSignals.setSourceRectHint(
                                Rect(
                                    bounds.left.roundToInt(),
                                    bounds.top.roundToInt(),
                                    bounds.right.roundToInt(),
                                    bounds.bottom.roundToInt()
                                ).takeIf { !it.isEmpty }
                            )
                        }
                    )
                }

                DisposableEffect(Unit) {
                    onDispose { LivePlaybackHostSignals.setSourceRectHint(null) }
                }
            }

            // Danmaku SurfaceView overlay on top of player
            if (viewState.danmakuEnabled) {
                AndroidView(
                    factory = { ctx ->
                        DanmakuSurfaceView(ctx).also { view ->
                            diagnosticDanmakuController = view.controller
                            applyDanmakuConfig(view.controller, resolvedDanmakuConfig)
                            danmakuActions.onControllerCreated?.invoke(view.controller)
                        }
                    },
                    update = { view ->
                        applyDanmakuConfig(view.controller, resolvedDanmakuConfig)
                    },
                    onRelease = { view ->
                        view.release()
                        if (diagnosticDanmakuController === view.controller) diagnosticDanmakuController = null
                        danmakuActions.onControllerReleased?.invoke(view.controller)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Combined gesture and tap overlay
        var showVolumeIndicator by remember { mutableStateOf(false) }
        var volumeValue by remember { mutableFloatStateOf(0f) }
        var showBrightnessIndicator by remember { mutableStateOf(false) }
        var brightnessValue by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked, playerController) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                            controlsVisible = showControls
                        },
                        onDoubleTap = {
                            if (!isLocked) {
                                actions.onFullscreenClick?.invoke()
                                    ?: playerController?.toggleFullscreen()
                            }
                        }
                    )
                }
                .pointerInput(isLocked, playerController) {
                    if (isLocked) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val isLeftSide = startX < size.width / 2f
                        val canStartVerticalGesture = down.position.y in
                            (size.height * 0.25f)..(size.height * 0.75f)
                        val velocityTracker = VelocityTracker().apply {
                            addPosition(down.uptimeMillis, down.position)
                        }

                        var lastY = down.position.y
                        var gestureStartVolume = playerController?.state?.value?.volume ?: 1f
                        var totalVerticalDragY = 0f
                        var lastAppliedSystemVolume = -1f
                        var lastSystemVolumeAppliedAt = Long.MIN_VALUE
                        var pendingSystemVolume: Float? = null
                        var accumulatedX = 0f
                        var accumulatedY = 0f
                        var hasDecidedDirection = false
                        var isHorizontal = false
                        var isVertical = false

                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) break

                            val positionChange = change.position - change.previousPosition
                            val deltaX = positionChange.x
                            val deltaY = positionChange.y
                            accumulatedX += deltaX
                            accumulatedY += deltaY

                            if (!hasDecidedDirection) {
                                val touchSlop = 24f
                                if (Math.abs(accumulatedX) > touchSlop || Math.abs(accumulatedY) > touchSlop) {
                                    hasDecidedDirection = true
                                    if (Math.abs(accumulatedX) > Math.abs(accumulatedY)) {
                                        isHorizontal = true
                                    } else if (canStartVerticalGesture) {
                                        isVertical = true
                                        if (isLeftSide) {
                                            showBrightnessIndicator = true
                                            val act = context as? Activity
                                            brightnessValue = act?.let(::currentWindowOrSystemBrightness)
                                                ?: playerController?.state?.value?.brightness
                                                ?: 0.5f
                                        } else {
                                            showVolumeIndicator = true
                                            gestureStartVolume = playerController?.currentSystemMediaVolume() ?: 1f
                                            volumeValue = gestureStartVolume
                                        }
                                    }
                                }
                            }

                            if (hasDecidedDirection && (isVertical || isHorizontal)) {
                                change.consume()
                                if (isVertical) {
                                    val currentY = change.position.y
                                    val dragAmount = currentY - lastY
                                    lastY = currentY
                                    val delta = -dragAmount / size.height.toFloat()
                                    val act = context as? Activity
                                    if (isLeftSide && act != null) {
                                        playerController?.setBrightness(act, delta)
                                        brightnessValue = playerController?.state?.value?.brightness ?: 0.5f
                                    } else {
                                        totalVerticalDragY += dragAmount
                                        volumeValue = quantizeSystemVolumeGesture(
                                            systemVolumeForVerticalDrag(
                                            startVolume = gestureStartVolume,
                                            totalDragY = totalVerticalDragY,
                                            heightPx = size.height.toFloat()
                                            )
                                        )
                                        val now = SystemClock.uptimeMillis()
                                        val canApplyImmediately =
                                            lastSystemVolumeAppliedAt == Long.MIN_VALUE ||
                                                now - lastSystemVolumeAppliedAt >= SystemVolumeGestureThrottleMs
                                        if (
                                            playerController != null &&
                                            volumeValue != lastAppliedSystemVolume &&
                                            canApplyImmediately
                                        ) {
                                            playerController.setSystemVolumeDirect(volumeValue)
                                            lastAppliedSystemVolume = volumeValue
                                            lastSystemVolumeAppliedAt = now
                                            pendingSystemVolume = null
                                        } else if (volumeValue != lastAppliedSystemVolume) {
                                            pendingSystemVolume = volumeValue
                                        }
                                    }
                                } else if (isHorizontal) {
                                    actions.onHorizontalDragDelta?.invoke(deltaX)
                                }
                            }
                        }

                        if (
                            isVertical &&
                            !isLeftSide &&
                            pendingSystemVolume != null &&
                            pendingSystemVolume != lastAppliedSystemVolume
                        ) {
                            playerController?.setSystemVolumeDirect(pendingSystemVolume!!)
                        }

                        if (isHorizontal) {
                            actions.onHorizontalDragEnd?.invoke(
                                velocityTracker.calculateVelocity().x
                            )
                        }

                        showVolumeIndicator = false
                        showBrightnessIndicator = false
                    }
                }
        ) {
            // Volume indicator overlay
            if (showVolumeIndicator) {
                IndicatorOverlay(
                    icon = if (volumeValue > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    value = "${(volumeValue * 100).toInt()}%",
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Brightness indicator overlay
            if (showBrightnessIndicator) {
                IndicatorOverlay(
                    icon = Icons.Default.Sun,
                    value = "${(brightnessValue * 100).toInt()}%",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Controls overlay (never shown in the PiP window, which only hosts the video surface)
        AnimatedVisibility(
            visible = shouldShowPlayerControls(
                controlsVisible = controlsVisible,
                isInPictureInPicture = viewState.isInPictureInPicture
            ),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (isLocked) {
                // Fullscreen keeps one lock action; the embedded player retains both edges.
                Box(modifier = Modifier.fillMaxSize()) {
                    // Left lock button
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 24.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable {
                                isLocked = false
                                showControls = true
                                controlsVisible = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "解锁",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (!isFullscreen) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 24.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable {
                                    isLocked = false
                                    showControls = true
                                    controlsVisible = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "解锁",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            } else {
                // Normal controls overlay.
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        PlayerTopBar(
                            title = viewState.title,
                            onBack = actions.onBack,
                            onDiagnosticsClick = if (
                                shouldShowPlaybackDiagnostics(
                                    debugBuild = BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "qa",
                                    hasController = playerController != null
                                )
                            ) {
                                {
                                    diagnosticsSnapshot = playerController?.playbackDiagnostics(
                                        viewState.currentQualityName
                                    )
                                    showDiagnosticsSheet = diagnosticsSnapshot != null
                                }
                            } else {
                                null
                            }
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        PlayerBottomBar(
                            showPauseButton = showPauseButton,
                            onPlayPauseClick = { playerController?.togglePlayPause() },
                            danmuEnable = viewState.danmakuEnabled,
                            onDanmuToggle = {
                                danmakuActions.onEnabledChange?.invoke(
                                    !viewState.danmakuEnabled
                                )
                            },
                            onDanmuSettingsClick = { showDanmuSettingsSheet = true },
                            accentColor = resolvedAccentColor,
                            currentQualityName = viewState.currentQualityName,
                            onQualityClick = actions.onQualityClick,
                            currentLineName = currentLineName,
                            onLineClick = { showLineSheet = true },
                            isMuted = state.volume == 0f,
                            onMuteToggle = { playerController?.toggleMute() },
                            isFullscreen = viewState.fullscreenOverride ?: state.isFullscreen,
                            onFullscreenClick = {
                                actions.onFullscreenClick?.invoke()
                                    ?: playerController?.toggleFullscreen()
                            },
                            onRoomSettingsClick = actions.onRoomSettingsClick.takeUnless { isFullscreen },
                            onQuickAccessClick = actions.onQuickAccessClick,
                            onFollowClick = actions.onFollowClick.takeUnless { isFullscreen },
                            isFollowing = viewState.isFollowing,
                            followEnabled = viewState.followEnabled,
                            onRefreshClick = actions.onRefreshClick,
                            isPortrait = isPortrait
                        )
                    }

                    // Left edge lock button (unlocked state)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 24.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { isLocked = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "锁屏",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (!isFullscreen) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 24.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { isLocked = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "锁屏",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Loading state (a 48dp spinner would dominate the small PiP window)
        if (
            shouldShowPlayerLoading(
                isLoading = state.isLoading,
                hasError = state.error != null,
                hasController = playerController != null,
                isInPictureInPicture = viewState.isInPictureInPicture
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Error state
        if (state.error != null) {
            PlayerErrorOverlay(message = state.error ?: "")
        }
    }
    }

    // Line switcher bottom sheet
    if (showLineSheet && urls.isNotEmpty()) {
        LineBottomSheet(
            lines = urls,
            currentIndex = currentLineIndex,
            onSelect = { index ->
                playerController?.changeLine(index)
                showLineSheet = false
            },
            onDismiss = { showLineSheet = false }
        )
    }

    // Danmaku quick settings bottom sheet
    if (showDanmuSettingsSheet) {
        DanmakuSettingsBottomSheet(
            danmuSize = danmakuConfig.size,
            onDanmuSizeChange = { danmakuActions.onSizeChange?.invoke(it) },
            danmuSpeed = danmakuConfig.speed,
            onDanmuSpeedChange = { danmakuActions.onSpeedChange?.invoke(it) },
            danmuArea = danmakuConfig.area,
            onDanmuAreaChange = { danmakuActions.onAreaChange?.invoke(it) },
            danmuOpacity = danmakuConfig.opacity,
            onDanmuOpacityChange = { danmakuActions.onOpacityChange?.invoke(it) },
            chatBubbleStyle = viewState.chatBubbleStyle,
            onChatBubbleStyleChange = {
                danmakuActions.onChatBubbleStyleChange?.invoke(it)
            },
            onDismiss = { showDanmuSettingsSheet = false }
        )
    }
    if (showDiagnosticsSheet) {
        diagnosticsSnapshot?.let { snapshot ->
            PlaybackDiagnosticsBottomSheet(
                snapshot = snapshot,
                onDismiss = { showDiagnosticsSheet = false }
            )
        }
    }
    }
}

