package com.mylive.app.ui.screen.room.player

import android.app.Activity
import android.graphics.Rect
import android.os.SystemClock
import android.view.LayoutInflater
import com.mylive.app.R
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
    actions: PlayerViewActions = PlayerViewActions(),
    modifier: Modifier = Modifier
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

    // Resolve danmu scale/speed defaults correctly
    val resolvedSize = remember(danmakuConfig.size) {
        if (danmakuConfig.size <= 2.0) {
            danmakuConfig.size * 16.0
        } else {
            danmakuConfig.size
        }
    }
    val resolvedSpeed = remember(danmakuConfig.speed) {
        if (danmakuConfig.speed > 5.0) 1.0 else danmakuConfig.speed
    }
    val resolvedDanmakuConfig = remember(danmakuConfig, resolvedSize, resolvedSpeed) {
        danmakuConfig.copy(size = resolvedSize, speed = resolvedSpeed)
    }

    // Line list reactive states
    val currentLineIndex by remember(state.currentUrl) {
        derivedStateOf { playerController?.getCurrentUrlIndex() ?: 0 }
    }
    val urls by remember(state.currentUrl) {
        derivedStateOf { playerController?.getUrls() ?: emptyList() }
    }
    val currentLineName = if (urls.isNotEmpty()) "线路 ${currentLineIndex + 1}" else "线路"

    // Danmaku quick settings and inline controls read primary/primaryContainer from
    // the theme, so wrap the entire player content once to propagate the platform accent.
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(primary = resolvedAccentColor)
    ) {
    Box(modifier = modifier.background(Color.Black)) {
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
                    else -> Modifier.fillMaxSize()
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
                        modifier = playerModifier.onGloballyPositioned { coordinates ->
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
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                            controlsVisible = showControls
                        },
                        onDoubleTap = {
                            if (!isLocked) {
                                playerController?.toggleFullscreen()
                            }
                        }
                    )
                }
                .pointerInput(isLocked) {
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
                                            brightnessValue = playerController?.state?.value?.brightness ?: 0.5f
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
                // Locked state controls overlay (Only dual edge floating lock buttons)
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

                    // Right lock button
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
            } else {
                // Normal controls overlay (TopBar + BottomBar + Dual lock buttons)
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        PlayerTopBar(
                            title = viewState.title,
                            onBack = actions.onBack
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        PlayerBottomBar(
                            isPlaying = state.isPlaying,
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
                            onRoomSettingsClick = actions.onRoomSettingsClick,
                            onQuickAccessClick = actions.onQuickAccessClick,
                            onFollowClick = actions.onFollowClick,
                            isFollowing = viewState.isFollowing,
                            followEnabled = viewState.followEnabled,
                            onRefreshClick = actions.onRefreshClick,
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
                            },
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

                    // Right edge lock button (unlocked state)
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

@Composable
private fun PlayerErrorOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 72.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.78f),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    onBack: (() -> Unit)?
) {
    val topGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.8f),
                Color.Black.copy(alpha = 0.4f),
                Color.Transparent
            )
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(topGradient)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
        }

        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun PlayerBottomBar(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    danmuEnable: Boolean,
    onDanmuToggle: () -> Unit,
    onDanmuSettingsClick: () -> Unit,
    accentColor: Color,
    currentQualityName: String,
    onQualityClick: (() -> Unit)?,
    currentLineName: String,
    onLineClick: () -> Unit,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    isFullscreen: Boolean,
    onFullscreenClick: () -> Unit,
    onRoomSettingsClick: (() -> Unit)? = null,
    onQuickAccessClick: (() -> Unit)? = null,
    onFollowClick: (() -> Unit)? = null,
    isFollowing: Boolean = false,
    followEnabled: Boolean = true,
    onRefreshClick: (() -> Unit)? = null,
    onDiagnosticsClick: (() -> Unit)? = null,
    isPortrait: Boolean = true
) {
    val bottomGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.4f),
                Color.Black.copy(alpha = 0.8f)
            )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(bottomGradient)
    ) {
        val compact = shouldUseCompactPlayerBottomBar(
            availableWidthDp = maxWidth.value.toInt(),
            isPortrait = isPortrait
        )
        val buttonSize = if (compact) 40.dp else 48.dp
        val iconSize = if (compact) 22.dp else 24.dp
        val itemGap = if (compact) 4.dp else 8.dp
        val horizontalPadding = if (compact) 8.dp else 16.dp
        var showOverflowMenu by remember { mutableStateOf(false) }
        val overflowMenuScrollState = rememberScrollState()
        val overflowMenuColor = MenuDefaults.containerColor
        val placeFollowActionOnRight = shouldPlacePlayerFollowActionOnRight(isPortrait)
        val showQualityAction = (!isPortrait || isFullscreen) && onQualityClick != null
        val showLineAction = !isPortrait || isFullscreen
        val showRoomSettingsAction = shouldShowPlayerRoomSettingsAction(isPortrait)
        val showFullscreenAction = shouldShowPlayerFullscreenAction(isPortrait) || (LocalAppLayout.current.tablet && !isFullscreen)
        val hasOverflowActions = compact && (
            onRefreshClick != null ||
                showQualityAction ||
                showLineAction ||
                (showRoomSettingsAction && onRoomSettingsClick != null) ||
                onQuickAccessClick != null ||
                onDiagnosticsClick != null
            )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = if (compact) 18.dp else 24.dp,
                    bottom = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!placeFollowActionOnRight && onFollowClick != null) {
                PlayerFollowActionButton(
                    isFollowing = isFollowing,
                    followEnabled = followEnabled,
                    accentColor = accentColor,
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                    onFollowClick = onFollowClick
                )
                Spacer(modifier = Modifier.width(itemGap))
            }

            if (!compact && onRefreshClick != null) {
                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier.size(buttonSize)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }
                Spacer(modifier = Modifier.width(itemGap))
            }

            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(buttonSize)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
            Spacer(modifier = Modifier.width(itemGap))

            IconButton(onClick = onDanmuToggle, modifier = Modifier.size(buttonSize)) {
                Icon(
                    if (danmuEnable) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                    contentDescription = "弹幕开关",
                    tint = resolvePlayerDanmuButtonTint(
                        danmuEnable = danmuEnable,
                        accentColor = accentColor
                    ),
                    modifier = Modifier.size(iconSize)
                )
            }
            Spacer(modifier = Modifier.width(itemGap))

            if (shouldShowPlayerDanmuSettingsButton(isPortrait)) {
                IconButton(
                    onClick = onDanmuSettingsClick,
                    modifier = Modifier.size(buttonSize)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "弹幕设置",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }
                Spacer(modifier = Modifier.width(itemGap))
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!compact && showQualityAction) {
                TextButton(
                    onClick = onQualityClick!!,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text(currentQualityName, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.width(itemGap))
            }

            if (!compact && showLineAction) {
                TextButton(
                    onClick = onLineClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text(currentLineName, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.width(itemGap))
            }

            IconButton(
                onClick = onMuteToggle,
                modifier = Modifier.size(buttonSize)
            ) {
                Icon(
                    if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "静音",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
            Spacer(modifier = Modifier.width(itemGap))

            if (!compact && showRoomSettingsAction && onRoomSettingsClick != null) {
                IconButton(
                    onClick = onRoomSettingsClick,
                    modifier = Modifier.size(buttonSize)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }
                Spacer(modifier = Modifier.width(itemGap))
            }

            if (!compact && onQuickAccessClick != null) {
                IconButton(
                    onClick = onQuickAccessClick,
                    modifier = Modifier.size(buttonSize)
                ) {
                    Icon(
                        Icons.Default.PlaylistAdd,
                        contentDescription = "快速入口",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }
                Spacer(modifier = Modifier.width(itemGap))
            }

            if (placeFollowActionOnRight && onFollowClick != null) {
                PlayerFollowActionButton(
                    isFollowing = isFollowing,
                    followEnabled = followEnabled,
                    accentColor = accentColor,
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                    onFollowClick = onFollowClick
                )
                Spacer(modifier = Modifier.width(itemGap))
            }

            if (showFullscreenAction) {
                IconButton(
                    onClick = onFullscreenClick,
                    modifier = Modifier.size(buttonSize)
                ) {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "全屏",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }
                Spacer(modifier = Modifier.width(itemGap))
            }

            if (hasOverflowActions) {
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.size(buttonSize)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = Color.White,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .playerOverflowMenuEdgeFades(
                                scrollState = overflowMenuScrollState,
                                edgeColor = overflowMenuColor
                            ),
                        scrollState = overflowMenuScrollState,
                        containerColor = overflowMenuColor
                    ) {
                        if (onRefreshClick != null) {
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onRefreshClick()
                                }
                            )
                        }
                        if (showQualityAction) {
                            DropdownMenuItem(
                                text = { Text("画质：$currentQualityName") },
                                onClick = {
                                    showOverflowMenu = false
                                    onQualityClick?.invoke()
                                }
                            )
                        }
                        if (showLineAction) {
                            DropdownMenuItem(
                                text = { Text("线路：$currentLineName") },
                                onClick = {
                                    showOverflowMenu = false
                                    onLineClick()
                                }
                            )
                        }
                        if (showRoomSettingsAction && onRoomSettingsClick != null) {
                            DropdownMenuItem(
                                text = { Text("设置") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onRoomSettingsClick()
                                }
                            )
                        }
                        if (onQuickAccessClick != null) {
                            DropdownMenuItem(
                                text = { Text("快速入口") },
                                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onQuickAccessClick()
                                }
                            )
                        }
                        if (onDiagnosticsClick != null) {
                            DropdownMenuItem(
                                text = { Text("播放诊断") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onDiagnosticsClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerFollowActionButton(
    isFollowing: Boolean,
    followEnabled: Boolean,
    accentColor: Color,
    buttonSize: Dp,
    iconSize: Dp,
    onFollowClick: () -> Unit
) {
    IconButton(
        onClick = onFollowClick,
        enabled = followEnabled,
        modifier = Modifier.size(buttonSize)
    ) {
        Icon(
            if (isFollowing) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isFollowing) "已关注" else "关注",
            tint = accentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

internal fun resolvePlayerDanmuButtonTint(
    danmuEnable: Boolean,
    accentColor: Color
): Color {
    return if (danmuEnable) accentColor else Color.White
}

internal data class PlayerOverflowMenuScrollEdges(
    val showTop: Boolean,
    val showBottom: Boolean
)

internal fun resolvePlayerOverflowMenuScrollEdges(
    scrollValue: Int,
    maxScrollValue: Int
): PlayerOverflowMenuScrollEdges {
    val boundedMax = maxScrollValue.coerceAtLeast(0)
    val boundedValue = scrollValue.coerceIn(0, boundedMax)
    return PlayerOverflowMenuScrollEdges(
        showTop = boundedValue > 0,
        showBottom = boundedValue < boundedMax
    )
}

private fun Modifier.playerOverflowMenuEdgeFades(
    scrollState: ScrollState,
    edgeColor: Color
): Modifier = drawWithContent {
    drawContent()
    val edges = resolvePlayerOverflowMenuScrollEdges(
        scrollValue = scrollState.value,
        maxScrollValue = scrollState.maxValue
    )
    val edgeHeight = minOf(18.dp.toPx(), size.height)
    if (edges.showTop) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(edgeColor.copy(alpha = 0.9f), Color.Transparent),
                startY = 0f,
                endY = edgeHeight
            ),
            size = Size(size.width, edgeHeight)
        )
    }
    if (edges.showBottom) {
        val top = size.height - edgeHeight
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, edgeColor.copy(alpha = 0.9f)),
                startY = top,
                endY = size.height
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, edgeHeight)
        )
    }
}

internal fun shouldUseCompactPlayerBottomBar(availableWidthDp: Int, isPortrait: Boolean): Boolean {
    return !isPortrait && availableWidthDp < 480
}

internal fun shouldPlacePlayerFollowActionOnRight(isPortrait: Boolean): Boolean {
    return !isPortrait
}

internal fun shouldShowPlayerRoomSettingsAction(isPortrait: Boolean): Boolean {
    return isPortrait
}

internal fun shouldShowPlayerFullscreenAction(isPortrait: Boolean): Boolean {
    return isPortrait
}

internal fun shouldShowPlayerDanmuSettingsButton(isPortrait: Boolean): Boolean {
    return isPortrait
}

internal fun shouldShowPlaybackDiagnostics(debugBuild: Boolean, hasController: Boolean): Boolean =
    debugBuild && hasController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackDiagnosticsBottomSheet(
    snapshot: PlaybackDiagnosticSnapshot,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "播放诊断",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            snapshot.entries.forEach { entry ->
                ListItem(
                    headlineContent = { Text(entry.label) },
                    supportingContent = { Text(entry.value) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineBottomSheet(
    lines: List<String>,
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
                text = "选择线路",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider()

            lines.forEachIndexed { index, url ->
                val isSelected = index == currentIndex
                val isFlv = url.contains(".flv", ignoreCase = true)
                val typeLabel = if (isFlv) "FLV" else "HLS"

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "线路 ${index + 1}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                }
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DanmakuSettingsBottomSheet(
    danmuSize: Double,
    onDanmuSizeChange: (Double) -> Unit,
    danmuSpeed: Double,
    onDanmuSpeedChange: (Double) -> Unit,
    danmuArea: Double,
    onDanmuAreaChange: (Double) -> Unit,
    danmuOpacity: Double,
    onDanmuOpacityChange: (Double) -> Unit,
    chatBubbleStyle: Boolean,
    onChatBubbleStyleChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "弹幕快捷设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Size Selector
                Text("弹幕字号", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                val sizeValues = listOf(0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.5)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sizeValues.forEach { size ->
                        FilterChip(
                            selected = danmuSize == size,
                            onClick = { onDanmuSizeChange(size) },
                            label = { Text("${size}x") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Speed Selector
                Text("弹幕速度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                val speedValues = listOf(0.6, 0.8, 1.0, 1.2, 1.5)
                val speedLabels = listOf("极慢", "较慢", "正常", "较快", "最快")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    speedValues.forEachIndexed { idx, speed ->
                        FilterChip(
                            selected = danmuSpeed == speed,
                            onClick = { onDanmuSpeedChange(speed) },
                            label = { Text(speedLabels[idx]) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Area Selector
                Text("显示区域", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                val areaValues = listOf(0.25, 0.333, 0.5, 0.667, 1.0)
                val areaLabels = listOf("1/4屏", "1/3屏", "1/2屏", "2/3屏", "全屏")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    areaValues.forEachIndexed { idx, area ->
                        FilterChip(
                            selected = danmuArea == area,
                            onClick = { onDanmuAreaChange(area) },
                            label = { Text(areaLabels[idx]) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Opacity Selector
                Text("不透明度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                val opacityValues = listOf(0.2, 0.4, 0.6, 0.8, 1.0)
                val opacityLabels = listOf("20%", "40%", "60%", "80%", "100%")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    opacityValues.forEachIndexed { idx, opacity ->
                        FilterChip(
                            selected = danmuOpacity == opacity,
                            onClick = { onDanmuOpacityChange(opacity) },
                            label = { Text(opacityLabels[idx]) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Bubble style toggle
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("气泡样式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "聊天消息使用气泡背景",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = chatBubbleStyle,
                        onCheckedChange = onChatBubbleStyleChange
                    )
                }
            }
        }
    }
}

@Composable
private fun IndicatorOverlay(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.7f),
        modifier = modifier
            .padding(16.dp)
            .width(72.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
