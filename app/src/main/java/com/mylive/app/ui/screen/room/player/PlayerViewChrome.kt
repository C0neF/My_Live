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

@Composable
internal fun PlayerErrorOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val controlsPadding = minOf(60.dp, maxHeight / 3f)
        val scrollState = key(message) { rememberScrollState() }
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = controlsPadding)
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.78f),
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(scrollState)
                )
            }
        }
    }
}

@Composable
internal fun PlayerTopBar(
    title: String,
    onBack: (() -> Unit)?,
    onDiagnosticsClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val topGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.58f),
                Color.Black.copy(alpha = 0.22f),
                Color.Transparent
            )
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(topGradient)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 18.dp),
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
            color = Color.White.copy(alpha = 0.96f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        )
        if (onDiagnosticsClick != null) {
            IconButton(onClick = onDiagnosticsClick) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "播放诊断",
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
internal fun PlayerBottomBar(
    showPauseButton: Boolean,
    onPlayPauseClick: () -> Unit,
    danmuEnable: Boolean,
    onDanmuToggle: () -> Unit,
    onDanmuSettingsClick: () -> Unit,
    accentColor: Color,
    currentQualityName: String,
    onQualityClick: (() -> Unit)?,
    qualityActive: Boolean = false,
    onQualityCenterMeasured: ((Float) -> Unit)? = null,
    currentLineName: String,
    onLineClick: () -> Unit,
    lineActive: Boolean = false,
    onLineCenterMeasured: ((Float) -> Unit)? = null,
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
    isPortrait: Boolean = true,
    modifier: Modifier = Modifier
) {
    val bottomGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.24f),
                Color.Black.copy(alpha = 0.64f)
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(bottomGradient)
    ) {
        val compact = shouldUseCompactPlayerBottomBar(
            availableWidthDp = maxWidth.value.toInt(),
            isPortrait = isPortrait,
            isFullscreen = isFullscreen
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
        val showFullscreenAction = shouldShowPlayerFullscreenAction(isPortrait) || isFullscreen || (LocalAppLayout.current.tablet && !isFullscreen)
        val hasOverflowActions = compact && (
            (!isFullscreen && onRefreshClick != null) ||
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

            if (onRefreshClick != null && (!compact || isFullscreen)) {
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
                    if (showPauseButton) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (showPauseButton) "暂停" else "播放",
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

            if (shouldShowPlayerDanmuSettingsButton(isPortrait, isFullscreen)) {
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
                Surface(
                    onClick = onQualityClick,
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = if (qualityActive) 0.28f else 0.16f),
                    modifier = Modifier.onGloballyPositioned { coords ->
                        onQualityCenterMeasured?.invoke(coords.boundsInWindow().center.x)
                    }
                ) {
                    Text(
                        currentQualityName,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.width(itemGap))
            }

            if (!compact && showLineAction) {
                Surface(
                    onClick = onLineClick,
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = if (lineActive) 0.28f else 0.16f),
                    modifier = Modifier.onGloballyPositioned { coords ->
                        onLineCenterMeasured?.invoke(coords.boundsInWindow().center.x)
                    }
                ) {
                    Text(
                        currentLineName,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
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
                        if (!isFullscreen && onRefreshClick != null) {
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
                                    onQualityClick()
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
                Spacer(modifier = Modifier.width(itemGap))
            }

            if (showFullscreenAction) {
                IconButton(
                    onClick = onFullscreenClick,
                    modifier = Modifier.size(buttonSize)
                ) {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
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

internal fun shouldUseCompactPlayerBottomBar(
    availableWidthDp: Int,
    isPortrait: Boolean,
    isFullscreen: Boolean = false
): Boolean {
    return (!isPortrait || isFullscreen) && availableWidthDp < 480
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

internal fun shouldShowPlayerDanmuSettingsButton(isPortrait: Boolean, isFullscreen: Boolean = false): Boolean {
    return isPortrait && !isFullscreen
}

internal fun shouldShowPlaybackDiagnostics(debugBuild: Boolean, hasController: Boolean): Boolean =
    debugBuild && hasController
