package com.mylive.app.ui.screen.room.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow playback port used by [com.mylive.app.ui.screen.room.LiveRoomViewModel].
 * Hides ExoPlayer ownership, surface, FGS, and decoder rebuild details.
 */
interface LivePlaybackEngine {
    val state: StateFlow<PlayerState>

    fun play(
        urls: List<String>,
        headers: Map<String, String>? = null,
        startIndex: Int = 0,
        resetSourceRefreshAttempt: Boolean = true
    )

    fun stop()

    fun showError(message: String)
}

/** Host-background decision after folding settings and current play state. */
sealed class LivePlaybackHostPauseAction {
    data object Pause : LivePlaybackHostPauseAction()
    data object StartForegroundService : LivePlaybackHostPauseAction()
    data object NoOp : LivePlaybackHostPauseAction()
}

/**
 * Pure host-pause matrix for unit tests and [LivePlaybackSession].
 */
internal fun resolveLivePlaybackHostPauseAction(
    allowBackgroundPlayback: Boolean,
    playerAutoPause: Boolean,
    isPlaying: Boolean
): LivePlaybackHostPauseAction {
    val lifecyclePausesPlayback = shouldLifecyclePauseLivePlayback(
        allowBackgroundPlayback = allowBackgroundPlayback,
        playerAutoPause = playerAutoPause
    )
    return when {
        lifecyclePausesPlayback -> LivePlaybackHostPauseAction.Pause
        shouldStartPlaybackForegroundService(
            lifecyclePausesPlayback = lifecyclePausesPlayback,
            isPlaying = isPlaying
        ) -> LivePlaybackHostPauseAction.StartForegroundService
        else -> LivePlaybackHostPauseAction.NoOp
    }
}
