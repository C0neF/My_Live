package com.mylive.app.ui.screen.room.player

import android.app.Activity
import android.view.LayoutInflater
import com.mylive.app.R
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mylive.app.ui.theme.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView as Media3PlayerView
import com.mylive.app.core.model.LivePlayQuality
import kotlinx.coroutines.delay

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@AndroidXOptIn(UnstableApi::class)
@Composable
fun PlayerView(
    playerController: PlayerController?,
    modifier: Modifier = Modifier,
    title: String = "",
    onBack: (() -> Unit)? = null,
    // Quality selection
    currentQualityName: String = "画质",
    onQualityClick: (() -> Unit)? = null,
    // Refresh
    onRefreshClick: (() -> Unit)? = null,
    // Danmaku toggling and setting
    danmuEnable: Boolean = true,
    onDanmuToggle: ((Boolean) -> Unit)? = null,
    accentColor: Color? = null,
    // Danmaku setting callbacks
    danmuSize: Double = 16.0,
    onDanmuSizeChange: ((Double) -> Unit)? = null,
    danmuSpeed: Double = 10.0,
    onDanmuSpeedChange: ((Double) -> Unit)? = null,
    danmuArea: Double = 0.8,
    onDanmuAreaChange: ((Double) -> Unit)? = null,
    danmuLineCount: Int = 8,
    danmuDelayMs: Int = 0,
    danmuOpacity: Double = 1.0,
    onDanmuOpacityChange: ((Double) -> Unit)? = null,
    // Other danmaku settings
    danmuFontWeight: Int = 4,
    danmuStrokeWidth: Double = 2.0,
    danmuTopMargin: Double = 0.0,
    danmuBottomMargin: Double = 0.0,
    danmuHideScroll: Boolean = false,
    danmuDedupeEnable: Boolean = false,
    danmuDedupeWindow: Int = 10,
    danmuDedupeStep: Int = 2,
    danmuDedupeStrictMode: Boolean = false,
    scaleMode: Int = 0,
    playerCompatMode: Boolean = false,
    danmuRenderEmoji: Boolean = true,
    chatBubbleStyle: Boolean = false,
    onChatBubbleStyleChange: ((Boolean) -> Unit)? = null,
    onDanmakuControllerCreated: ((DanmakuController) -> Unit)? = null,
    onHorizontalDragDelta: ((Float) -> Unit)? = null,
    onHorizontalDragEnd: (() -> Unit)? = null,
    isFullscreenOverride: Boolean? = null,
    onFullscreenClick: (() -> Unit)? = null,
    onRoomSettingsClick: (() -> Unit)? = null,
    onQuickAccessClick: (() -> Unit)? = null,
    onFollowClick: (() -> Unit)? = null,
    isFollowing: Boolean = false,
    followEnabled: Boolean = true,
    rightPadding: Dp = 0.dp,
    isExiting: Boolean = false
) {
    val stateFlow = playerController?.state
    val state by (stateFlow?.collectAsState() ?: remember { mutableStateOf(PlayerState()) })
    var controlsVisible by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }

    // Screen Lock System state
    var isLocked by remember { mutableStateOf(false) }

    // Sheets visibility states
    var showLineSheet by remember { mutableStateOf(false) }
    var showDanmuSettingsSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val resolvedAccentColor = accentColor ?: MaterialTheme.colorScheme.primary

    val isFullscreen = isFullscreenOverride ?: state.isFullscreen

    // Intercept back key when in fullscreen to exit fullscreen first
    BackHandler(enabled = isFullscreen) {
        onFullscreenClick?.invoke() ?: playerController?.toggleFullscreen()
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
    val resolvedSize = remember(danmuSize) {
        if (danmuSize <= 2.0) (danmuSize * 16.0) else danmuSize
    }
    val resolvedSpeed = remember(danmuSpeed) {
        if (danmuSpeed > 5.0) 1.0 else danmuSpeed
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
                val playerModifier = when (scaleMode) {
                    3 -> Modifier.aspectRatio(4f / 3f)
                    4 -> Modifier.aspectRatio(16f / 9f)
                    else -> Modifier.fillMaxSize()
                }

                key(playerCompatMode) {
                    AndroidView(
                        factory = { ctx ->
                            val layoutRes = if (playerCompatMode) R.layout.player_surface_view else R.layout.player_texture_view
                            LayoutInflater.from(ctx).inflate(layoutRes, null) as Media3PlayerView
                        },
                        update = { view ->
                            view.player = player
                            view.resizeMode = when (scaleMode) {
                                1 -> 3 // RESIZE_MODE_FILL
                                2 -> 4 // RESIZE_MODE_ZOOM
                                else -> 0 // RESIZE_MODE_FIT
                            }
                        },
                        modifier = playerModifier
                    )
                }
            }

            // Danmaku SurfaceView overlay on top of player
            if (danmuEnable) {
                AndroidView(
                    factory = { ctx ->
                        DanmakuSurfaceView(ctx).also { view ->
                            updateControllerConfig(
                                view.controller,
                                resolvedSize,
                                resolvedSpeed,
                                danmuArea,
                                danmuLineCount,
                                danmuDelayMs,
                                danmuOpacity,
                                danmuFontWeight,
                                danmuStrokeWidth,
                                danmuTopMargin,
                                danmuBottomMargin,
                                danmuHideScroll,
                                danmuDedupeEnable,
                                danmuDedupeWindow,
                                danmuDedupeStep,
                                danmuDedupeStrictMode,
                                danmuRenderEmoji
                            )
                            onDanmakuControllerCreated?.invoke(view.controller)
                        }
                    },
                    update = { view ->
                        updateControllerConfig(
                            view.controller,
                            resolvedSize,
                            resolvedSpeed,
                            danmuArea,
                            danmuLineCount,
                            danmuDelayMs,
                            danmuOpacity,
                            danmuFontWeight,
                            danmuStrokeWidth,
                            danmuTopMargin,
                            danmuBottomMargin,
                            danmuHideScroll,
                            danmuDedupeEnable,
                            danmuDedupeWindow,
                            danmuDedupeStep,
                            danmuDedupeStrictMode,
                            danmuRenderEmoji
                        )
                    },
                    onRelease = { view -> view.release() },
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
                        },
                        onLongPress = {
                            if (!isLocked) {
                                playerController?.togglePlayPause()
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

                        var lastY = down.position.y
                        var accumulatedX = 0f
                        var accumulatedY = 0f
                        var hasDecidedDirection = false
                        var isHorizontal = false
                        var isVertical = false

                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
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
                                    } else {
                                        isVertical = true
                                        if (isLeftSide) {
                                            showBrightnessIndicator = true
                                            brightnessValue = playerController?.state?.value?.brightness ?: 0.5f
                                        } else {
                                            showVolumeIndicator = true
                                            volumeValue = playerController?.state?.value?.volume ?: 1f
                                        }
                                    }
                                }
                            }

                            if (hasDecidedDirection) {
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
                                        playerController?.setVolume(delta)
                                        volumeValue = playerController?.state?.value?.volume ?: 1f
                                    }
                                } else if (isHorizontal) {
                                    onHorizontalDragDelta?.invoke(deltaX)
                                }
                            }
                        }

                        if (isHorizontal) {
                            onHorizontalDragEnd?.invoke()
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

        // Controls overlay
        AnimatedVisibility(
            visible = controlsVisible,
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
                            title = title,
                            onBack = onBack
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        PlayerBottomBar(
                            isPlaying = state.isPlaying,
                            onPlayPauseClick = { playerController?.togglePlayPause() },
                            danmuEnable = danmuEnable,
                            onDanmuToggle = { onDanmuToggle?.invoke(!danmuEnable) },
                            onDanmuSettingsClick = { showDanmuSettingsSheet = true },
                            accentColor = resolvedAccentColor,
                            currentQualityName = currentQualityName,
                            onQualityClick = onQualityClick,
                            currentLineName = currentLineName,
                            onLineClick = { showLineSheet = true },
                            isMuted = state.volume == 0f,
                            onMuteToggle = { playerController?.toggleMute() },
                            isFullscreen = isFullscreenOverride ?: state.isFullscreen,
                            onFullscreenClick = { onFullscreenClick?.invoke() ?: playerController?.toggleFullscreen() },
                            onRoomSettingsClick = onRoomSettingsClick,
                            onQuickAccessClick = onQuickAccessClick,
                            onFollowClick = onFollowClick,
                            isFollowing = isFollowing,
                            followEnabled = followEnabled,
                            onRefreshClick = onRefreshClick,
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

        // Loading state
        if (state.isLoading && state.error == null && playerController != null) {
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
            danmuSize = danmuSize,
            onDanmuSizeChange = { onDanmuSizeChange?.invoke(it) },
            danmuSpeed = danmuSpeed,
            onDanmuSpeedChange = { onDanmuSpeedChange?.invoke(it) },
            danmuArea = danmuArea,
            onDanmuAreaChange = { onDanmuAreaChange?.invoke(it) },
            danmuOpacity = danmuOpacity,
            onDanmuOpacityChange = { onDanmuOpacityChange?.invoke(it) },
            chatBubbleStyle = chatBubbleStyle,
            onChatBubbleStyleChange = { onChatBubbleStyleChange?.invoke(it) },
            onDismiss = { showDanmuSettingsSheet = false }
        )
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
        val placeFollowActionOnRight = shouldPlacePlayerFollowActionOnRight(isPortrait)
        val showQualityAction = (!isPortrait || isFullscreen) && onQualityClick != null
        val showLineAction = !isPortrait || isFullscreen
        val showRoomSettingsAction = shouldShowPlayerRoomSettingsAction(isPortrait)
        val showFullscreenAction = shouldShowPlayerFullscreenAction(isPortrait)
        val hasOverflowActions = compact && (
            onRefreshClick != null ||
                showQualityAction ||
                showLineAction ||
                (showRoomSettingsAction && onRoomSettingsClick != null) ||
                onQuickAccessClick != null
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
                        onDismissRequest = { showOverflowMenu = false }
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

private fun updateControllerConfig(
    controller: DanmakuController,
    danmuSize: Double,
    danmuSpeed: Double,
    danmuArea: Double,
    danmuLineCount: Int,
    danmuDelayMs: Int,
    danmuOpacity: Double,
    danmuFontWeight: Int,
    danmuStrokeWidth: Double,
    danmuTopMargin: Double,
    danmuBottomMargin: Double,
    danmuHideScroll: Boolean,
    danmuDedupeEnable: Boolean,
    danmuDedupeWindow: Int,
    danmuDedupeStep: Int,
    danmuDedupeStrictMode: Boolean,
    danmuRenderEmoji: Boolean
) {
    controller.danmuSize = danmuSize.toFloat()
    controller.danmuSpeed = danmuSpeed.toFloat()
    controller.danmuArea = danmuArea.toFloat()
    controller.danmuLineCount = danmuLineCount
    controller.danmuDelayMs = danmuDelayMs
    controller.danmuOpacity = danmuOpacity.toFloat()
    controller.danmuFontWeight = danmuFontWeight
    controller.danmuStrokeWidth = danmuStrokeWidth.toFloat()
    controller.danmuTopMargin = danmuTopMargin.toFloat()
    controller.danmuBottomMargin = danmuBottomMargin.toFloat()
    controller.danmuHideScroll = danmuHideScroll
    controller.dedupeEnabled = danmuDedupeEnable
    controller.dedupeWindowSize = danmuDedupeWindow
    controller.dedupeStepSize = danmuDedupeStep
    controller.dedupeStrictMode = danmuDedupeStrictMode
    controller.danmuRenderEmoji = danmuRenderEmoji
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
