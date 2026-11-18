package com.mylive.app.ui.screen.room.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView as Media3PlayerView
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import com.mylive.app.ui.motion.popoverEnter
import com.mylive.app.ui.motion.popoverExit
import kotlinx.coroutines.delay

internal const val SystemVolumeGestureSensitivity = 2f

internal const val SystemVolumeGestureThrottleMs = 200L

private enum class PlayerCompactPopupType {
    QUALITY,
    LINE
}

private data class PlayerCompactPopupOption(
    val label: String,
    val badge: String? = null
)

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

    // Sheets & compact selector popup states
    var showLineSheet by remember { mutableStateOf(false) }
    var activeCompactPopup by remember { mutableStateOf<PlayerCompactPopupType?>(null) }
    var qualityAnchorCenterXInWindow by remember { mutableFloatStateOf(Float.NaN) }
    var lineAnchorCenterXInWindow by remember { mutableFloatStateOf(Float.NaN) }
    var playerLeftInWindow by remember { mutableFloatStateOf(0f) }
    var playerWidthPx by remember { mutableIntStateOf(0) }
    var playerHeightPx by remember { mutableIntStateOf(0) }
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
    val density = LocalDensity.current
    val motionPolicy = LocalAppMotionPolicy.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val resolvedAccentColor = viewState.accentColor ?: MaterialTheme.colorScheme.primary

    val isFullscreen = viewState.fullscreenOverride ?: state.isFullscreen
    val horizontalDragEnabled = !(isPortrait && isFullscreen) && actions.onHorizontalDragDelta != null
    val useCompactSelectorPopup = isFullscreen || !isPortrait
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
    BackHandler(enabled = activeCompactPopup != null) {
        activeCompactPopup = null
    }
    BackHandler(enabled = showDiagnosticsSheet) {
        showDiagnosticsSheet = false
    }

    LaunchedEffect(isLocked) {
        if (isLocked) {
            activeCompactPopup = null
            showDiagnosticsSheet = false
        }
        actions.onControlsLockChange?.invoke(isLocked)
    }

    LaunchedEffect(useCompactSelectorPopup, viewState.isInPictureInPicture) {
        if (viewState.isInPictureInPicture) {
            showDiagnosticsSheet = false
        }
        if (!useCompactSelectorPopup || viewState.isInPictureInPicture) {
            activeCompactPopup = null
        } else {
            showLineSheet = false
        }
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

    val compactPopupOpen = activeCompactPopup != null
    // Auto-hide controls after 3 seconds (paused while a compact selector popup is open)
    LaunchedEffect(showControls, state.isPlaying, isLocked, compactPopupOpen) {
        if (showControls && state.isPlaying && !compactPopupOpen) {
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
    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            playerLeftInWindow = coords.boundsInWindow().left
            playerWidthPx = coords.size.width
            playerHeightPx = coords.size.height
        }
    ) {
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
                .pointerInput(isLocked, playerController, horizontalDragEnabled) {
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
                                        isHorizontal = horizontalDragEnabled
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
                            playerController?.setSystemVolumeDirect(pendingSystemVolume)
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
            AnimatedVisibility(
                visible = showVolumeIndicator,
                enter = popoverEnter(motionPolicy),
                exit = popoverExit(motionPolicy),
                modifier = Modifier.align(Alignment.Center)
            ) {
                IndicatorOverlay(
                    icon = if (volumeValue > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    value = "${(volumeValue * 100).toInt()}%"
                )
            }

            // Brightness indicator overlay
            AnimatedVisibility(
                visible = showBrightnessIndicator,
                enter = popoverEnter(motionPolicy),
                exit = popoverExit(motionPolicy),
                modifier = Modifier.align(Alignment.Center)
            ) {
                IndicatorOverlay(
                    icon = Icons.Default.Sun,
                    value = "${(brightnessValue * 100).toInt()}%"
                )
            }
        }

        // Controls overlay (never shown in the PiP window, which only hosts the video surface)
        AnimatedVisibility(
            visible = shouldShowPlayerControls(
                controlsVisible = controlsVisible,
                isInPictureInPicture = viewState.isInPictureInPicture
            ),
            enter = fadeIn(animationSpec = motionPolicy.effectsSpec()),
            exit = fadeOut(animationSpec = motionPolicy.effectsSpec())
        ) {
            val topChromeSlide = Modifier.animateEnterExit(
                enter = if (motionPolicy.reducedMotion) EnterTransition.None else
                    slideInVertically(animationSpec = motionPolicy.contentSpec()) { -it / 3 },
                exit = if (motionPolicy.reducedMotion) ExitTransition.None else
                    slideOutVertically(animationSpec = motionPolicy.contentSpec()) { -it / 3 }
            )
            val bottomChromeSlide = Modifier.animateEnterExit(
                enter = if (motionPolicy.reducedMotion) EnterTransition.None else
                    slideInVertically(animationSpec = motionPolicy.contentSpec()) { it / 3 },
                exit = if (motionPolicy.reducedMotion) ExitTransition.None else
                    slideOutVertically(animationSpec = motionPolicy.contentSpec()) { it / 3 }
            )
            AnimatedContent(
                targetState = isLocked,
                transitionSpec = {
                    fadeIn(motionPolicy.effectsSpec()) togetherWith fadeOut(motionPolicy.effectsSpec())
                },
                label = "playerControlsLockState"
            ) { locked ->
            if (locked) {
                // Fullscreen keeps one lock action; the embedded player retains both edges.
                Box(modifier = Modifier.fillMaxSize()) {
                    // Left lock button
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.38f))
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
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (!isFullscreen) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.38f))
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
                                modifier = Modifier.size(18.dp)
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
                                    if (showDiagnosticsSheet) {
                                        showDiagnosticsSheet = false
                                    } else {
                                        activeCompactPopup = null
                                        diagnosticsSnapshot = playerController?.playbackDiagnostics(
                                            viewState.currentQualityName
                                        )?.withDanmaku(diagnosticDanmakuController?.diagnostics())
                                        showDiagnosticsSheet = diagnosticsSnapshot != null
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = topChromeSlide
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        val canUseCompactQuality = useCompactSelectorPopup &&
                            viewState.qualities.isNotEmpty() &&
                            actions.onQualitySelect != null
                        val effectiveQualityClick: (() -> Unit)? = when {
                            canUseCompactQuality -> {
                                {
                                    showControls = true
                                    controlsVisible = true
                                    activeCompactPopup = if (activeCompactPopup == PlayerCompactPopupType.QUALITY) {
                                        null
                                    } else {
                                        PlayerCompactPopupType.QUALITY
                                    }
                                }
                            }
                            actions.onQualityClick != null -> actions.onQualityClick
                            else -> null
                        }

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
                            onQualityClick = effectiveQualityClick,
                            qualityActive = activeCompactPopup == PlayerCompactPopupType.QUALITY,
                            onQualityCenterMeasured = { qualityAnchorCenterXInWindow = it },
                            currentLineName = currentLineName,
                            onLineClick = {
                                if (useCompactSelectorPopup) {
                                    if (urls.isNotEmpty()) {
                                        showControls = true
                                        controlsVisible = true
                                        activeCompactPopup = if (activeCompactPopup == PlayerCompactPopupType.LINE) {
                                            null
                                        } else {
                                            PlayerCompactPopupType.LINE
                                        }
                                    }
                                } else {
                                    showLineSheet = true
                                }
                            },
                            lineActive = activeCompactPopup == PlayerCompactPopupType.LINE,
                            onLineCenterMeasured = { lineAnchorCenterXInWindow = it },
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
                            isPortrait = isPortrait,
                            modifier = bottomChromeSlide
                        )
                    }

                    // Left edge lock button (unlocked state)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.34f))
                            .clickable { isLocked = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "锁屏",
                            tint = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (!isFullscreen) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.34f))
                                .clickable { isLocked = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "锁屏",
                                tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            }
        }

        // Loading state (a 48dp spinner would dominate the small PiP window)
        AnimatedVisibility(
            visible = shouldShowPlayerLoading(
                isLoading = state.isLoading,
                hasError = state.error != null,
                hasController = playerController != null,
                isInPictureInPicture = viewState.isInPictureInPicture
            ),
            enter = fadeIn(motionPolicy.effectsSpec()) +
                if (motionPolicy.reducedMotion) EnterTransition.None else scaleIn(motionPolicy.contentSpec(), initialScale = 0.90f),
            exit = fadeOut(motionPolicy.effectsSpec()) +
                if (motionPolicy.reducedMotion) ExitTransition.None else scaleOut(motionPolicy.contentSpec(), targetScale = 0.90f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Error state
        var cachedErrorMessage by remember { mutableStateOf("") }
        if (state.error != null) cachedErrorMessage = state.error ?: ""
        AnimatedVisibility(
            visible = state.error != null,
            enter = popoverEnter(motionPolicy),
            exit = popoverExit(motionPolicy)
        ) {
            PlayerErrorOverlay(message = cachedErrorMessage)
        }

        // Compact floating Apple-style selector popover for fullscreen / landscape Quality & Line
        val popupType = activeCompactPopup
        var rememberedPopupType by remember { mutableStateOf<PlayerCompactPopupType?>(null) }
        if (popupType != null) rememberedPopupType = popupType
        val showCompactQuality = popupType == PlayerCompactPopupType.QUALITY &&
            viewState.qualities.isNotEmpty() &&
            actions.onQualitySelect != null
        val showCompactLine = popupType == PlayerCompactPopupType.LINE && urls.isNotEmpty()
        LaunchedEffect(popupType, viewState.qualities.size, urls.size) {
            if (popupType == PlayerCompactPopupType.QUALITY && viewState.qualities.isEmpty()) {
                activeCompactPopup = null
            } else if (popupType == PlayerCompactPopupType.LINE && urls.isEmpty()) {
                activeCompactPopup = null
            }
        }
        AnimatedVisibility(
            visible = (showCompactQuality || showCompactLine) && !viewState.isInPictureInPicture,
            enter = fadeIn(motionPolicy.effectsSpec()),
            exit = fadeOut(motionPolicy.effectsSpec())
        ) {
            val displayPopupType = popupType ?: rememberedPopupType
            val displayQuality = displayPopupType == PlayerCompactPopupType.QUALITY &&
                viewState.qualities.isNotEmpty() &&
                actions.onQualitySelect != null
            val displayLine = displayPopupType == PlayerCompactPopupType.LINE && urls.isNotEmpty()
            val popupWidth = 168.dp
            val popupWidthPx = with(density) { popupWidth.toPx() }
            val edgeMarginPx = with(density) { 12.dp.toPx() }
            val maxPopupHeight = with(density) {
                (playerHeightPx.toDp() - 72.dp).coerceIn(112.dp, 224.dp)
            }
            val rawCenterXInWindow = if (displayQuality) {
                qualityAnchorCenterXInWindow
            } else {
                lineAnchorCenterXInWindow
            }
            val anchorXInPlayer = rawCenterXInWindow - playerLeftInWindow
            val hasMeasuredAnchor = rawCenterXInWindow.isFinite() &&
                playerWidthPx > 0 &&
                anchorXInPlayer > 0f &&
                anchorXInPlayer <= playerWidthPx.toFloat()
            val offsetX = if (hasMeasuredAnchor) {
                val maxLeft = (playerWidthPx - popupWidthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
                (anchorXInPlayer - popupWidthPx / 2f).coerceIn(edgeMarginPx, maxLeft).roundToInt()
            } else {
                0
            }
            val popoverMotion = Modifier.animateEnterExit(
                enter = if (motionPolicy.reducedMotion) EnterTransition.None else
                    scaleIn(
                        animationSpec = motionPolicy.contentSpec(),
                        initialScale = 0.92f,
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    ) + slideInVertically(animationSpec = motionPolicy.contentSpec()) { it / 5 },
                exit = if (motionPolicy.reducedMotion) ExitTransition.None else
                    scaleOut(
                        animationSpec = motionPolicy.contentSpec(),
                        targetScale = 0.92f,
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    ) + slideOutVertically(animationSpec = motionPolicy.contentSpec()) { it / 5 }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(displayPopupType) {
                        detectTapGestures { activeCompactPopup = null }
                    }
            ) {
                val placementModifier = if (hasMeasuredAnchor) {
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 54.dp)
                        .offset { IntOffset(offsetX, 0) }
                        .then(popoverMotion)
                } else {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 54.dp)
                        .then(popoverMotion)
                }
                if (displayQuality) {
                    PlayerCompactSelectorPopover(
                        title = stringResource(R.string.room_quality_sheet_title),
                        options = viewState.qualities.map { PlayerCompactPopupOption(label = it.quality) },
                        currentIndex = viewState.currentQualityIndex,
                        maxHeight = maxPopupHeight,
                        width = popupWidth,
                        testTag = "player_compact_quality_popup",
                        onSelect = { index ->
                            actions.onQualitySelect(index)
                            activeCompactPopup = null
                        },
                        modifier = placementModifier
                    )
                } else if (displayLine) {
                    PlayerCompactSelectorPopover(
                        title = "选择线路",
                        options = urls.mapIndexed { index, url ->
                            val isFlv = url.contains(".flv", ignoreCase = true)
                            PlayerCompactPopupOption(
                                label = "线路 ${index + 1}",
                                badge = if (isFlv) "FLV" else "HLS"
                            )
                        },
                        currentIndex = currentLineIndex,
                        maxHeight = maxPopupHeight,
                        width = popupWidth,
                        testTag = "player_compact_line_popup",
                        onSelect = { index ->
                            playerController?.changeLine(index)
                            activeCompactPopup = null
                        },
                        modifier = placementModifier
                    )
                }
            }
        }

        // Inline floating playback diagnostics overlay for fullscreen / landscape (avoids system bar flash)
        var cachedDiagnosticsSnapshot by remember { mutableStateOf<PlaybackDiagnosticSnapshot?>(null) }
        if (diagnosticsSnapshot != null) cachedDiagnosticsSnapshot = diagnosticsSnapshot
        AnimatedVisibility(
            visible = showDiagnosticsSheet && useCompactSelectorPopup && !viewState.isInPictureInPicture && diagnosticsSnapshot != null,
            enter = fadeIn(motionPolicy.effectsSpec()),
            exit = fadeOut(motionPolicy.effectsSpec())
        ) {
            cachedDiagnosticsSnapshot?.let { snapshot ->
                val maxDiagHeight = with(density) {
                    (playerHeightPx.toDp() - 36.dp).coerceIn(180.dp, 340.dp)
                }
                val maxDiagWidth = with(density) {
                    (playerWidthPx.toDp() - 32.dp).coerceIn(240.dp, 352.dp)
                }
                val cardMotion = Modifier.animateEnterExit(
                    enter = if (motionPolicy.reducedMotion) EnterTransition.None else
                        scaleIn(animationSpec = motionPolicy.contentSpec(), initialScale = 0.94f),
                    exit = if (motionPolicy.reducedMotion) ExitTransition.None else
                        scaleOut(animationSpec = motionPolicy.contentSpec(), targetScale = 0.94f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                        .pointerInput(Unit) {
                            detectTapGestures { showDiagnosticsSheet = false }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PlaybackDiagnosticsFloatingCard(
                        snapshot = snapshot,
                        maxWidth = maxDiagWidth,
                        maxHeight = maxDiagHeight,
                        onDismiss = { showDiagnosticsSheet = false },
                        modifier = cardMotion
                    )
                }
            }
        }
    }
    }

    // Line switcher bottom sheet (non-fullscreen portrait fallback)
    if (showLineSheet && urls.isNotEmpty() && !useCompactSelectorPopup) {
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
    if (showDiagnosticsSheet && !useCompactSelectorPopup && !viewState.isInPictureInPicture) {
        diagnosticsSnapshot?.let { snapshot ->
            PlaybackDiagnosticsFloatingDialog(
                snapshot = snapshot,
                onDismiss = { showDiagnosticsSheet = false }
            )
        }
    }
    }
}

@Composable
private fun PlayerCompactSelectorPopover(
    title: String,
    options: List<PlayerCompactPopupOption>,
    currentIndex: Int,
    maxHeight: Dp,
    width: Dp,
    testTag: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(width)
            .heightIn(max = maxHeight)
            .pointerInput(Unit) { detectTapGestures { /* Consume taps inside popover */ } }
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xE8222225),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.09f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == currentIndex
                    Surface(
                        onClick = { onSelect(index) },
                        color = if (isSelected) Color.White.copy(alpha = 0.10f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 13.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = option.label,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                option.badge?.let { badge ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = Color.White.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = badge,
                                            color = Color.White.copy(alpha = 0.72f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                    if (index < options.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 13.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.07f)
                        )
                    }
                }
            }
        }
    }
}
