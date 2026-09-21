package com.mylive.app.ui.screen.room.player

import androidx.compose.ui.graphics.Color

/**
 * Everything PlayerView renders that is owned by its caller.
 */
data class PlayerViewUiState(
    val title: String = "",
    val currentQualityName: String = "画质",
    val danmakuEnabled: Boolean = true,
    val danmaku: DanmakuConfig = DanmakuConfig(),
    val accentColor: Color? = null,
    val scaleMode: Int = 0,
    val playerCompatMode: Boolean = false,
    val chatBubbleStyle: Boolean = false,
    val fullscreenOverride: Boolean? = null,
    val isFollowing: Boolean = false,
    val followEnabled: Boolean = true,
    val isInPictureInPicture: Boolean = false
)

/**
 * Whether the transport/quality/title chrome should be drawn.
 *
 * Picture-in-picture renders a thumbnail-sized window: only the video surface (and danmaku, when
 * enabled) belong there. Controls and lock buttons are laid out at full size and would cover the
 * whole floating window, so they are suppressed entirely regardless of the auto-hide timer.
 */
internal fun shouldShowPlayerControls(
    controlsVisible: Boolean,
    isInPictureInPicture: Boolean
): Boolean = controlsVisible && !isInPictureInPicture

/**
 * Whether the buffering spinner should be drawn.
 *
 * Suppressed in picture-in-picture for the same reason as the controls: a 48dp spinner dominates
 * the floating window. Also suppressed once an error is showing (the error surface owns the
 * window) or before a controller exists (nothing is loading yet).
 */
internal fun shouldShowPlayerLoading(
    isLoading: Boolean,
    hasError: Boolean,
    hasController: Boolean,
    isInPictureInPicture: Boolean
): Boolean = isLoading && !hasError && hasController && !isInPictureInPicture

/**
 * Danmaku mutations are grouped separately from player and room actions.
 */
data class PlayerDanmakuActions(
    val onEnabledChange: ((Boolean) -> Unit)? = null,
    val onSizeChange: ((Double) -> Unit)? = null,
    val onSpeedChange: ((Double) -> Unit)? = null,
    val onAreaChange: ((Double) -> Unit)? = null,
    val onOpacityChange: ((Double) -> Unit)? = null,
    val onChatBubbleStyleChange: ((Boolean) -> Unit)? = null,
    val onControllerCreated: ((DanmakuController) -> Unit)? = null,
    val onControllerReleased: ((DanmakuController) -> Unit)? = null
)

/**
 * Events emitted by PlayerView. Defaults make read-only/PiP surfaces explicit
 * without a wall of empty lambdas.
 */
data class PlayerViewActions(
    val onBack: (() -> Unit)? = null,
    val onQualityClick: (() -> Unit)? = null,
    val onRefreshClick: (() -> Unit)? = null,
    val danmaku: PlayerDanmakuActions = PlayerDanmakuActions(),
    val onFullscreenClick: (() -> Unit)? = null,
    val onControlsLockChange: ((Boolean) -> Unit)? = null,
    val onRoomSettingsClick: (() -> Unit)? = null,
    val onQuickAccessClick: (() -> Unit)? = null,
    val onFollowClick: (() -> Unit)? = null,
    val onHorizontalDragDelta: ((Float) -> Unit)? = null,
    val onHorizontalDragEnd: ((velocityX: Float) -> Unit)? = null
)
