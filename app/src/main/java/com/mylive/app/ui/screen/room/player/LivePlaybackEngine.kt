package com.mylive.app.ui.screen.room.player

import kotlinx.coroutines.flow.StateFlow
import com.mylive.app.core.model.LivePlayUrl

/**
 * Narrow playback port used by [com.mylive.app.ui.screen.room.LiveRoomViewModel].
 * Hides ExoPlayer ownership, surface, FGS, and decoder rebuild details.
 */
interface LivePlaybackEngine {
    val state: StateFlow<PlayerState>
    /** Snapshot before asynchronous source acquisition; null keeps other engines compatible. */
    val playbackPauseGeneration: Long? get() = null
    fun beginRoomLoad(startedAtMillis: Long) {}

    fun play(
        urls: List<String>,
        headers: Map<String, String>? = null,
        startIndex: Int = 0,
        origin: PlaybackRequestOrigin = PlaybackRequestOrigin.UserSelection
    )

    /** Carries per-source video hints while preserving the simpler port for other engines. */
    fun play(source: LivePlayUrl, origin: PlaybackRequestOrigin = PlaybackRequestOrigin.UserSelection) {
        play(source.urls, source.headers, origin = origin)
    }

    /** A resolved source retains the pause boundary of the request that obtained it. */
    fun play(source: LivePlayUrl, origin: PlaybackRequestOrigin, pauseGeneration: Long?) {
        play(source, origin)
    }

    fun stop()

    /** Stops the old connection while retaining the intent for an automatic source refresh. */
    fun stopForSourceRecovery() { stop() }

    fun showError(message: String)
}

enum class PlaybackRequestOrigin {
    UserSelection,
    SourceRecovery
}

/** Binding implemented by the room state owner without exposing it to the player module. */
interface LivePlaybackSessionBinding {
    fun bindPlaybackEngine(engine: LivePlaybackEngine)
    fun unbindPlaybackEngine(engine: LivePlaybackEngine)
    fun onPlaybackEngineReady()
}

/** Host-background decision after folding settings and current play state. */
sealed class LivePlaybackHostPauseAction {
    data object Pause : LivePlaybackHostPauseAction()
    data object StartForegroundService : LivePlaybackHostPauseAction()
    data object NoOp : LivePlaybackHostPauseAction()
}

sealed class LivePlaybackStartAction {
    data object StartNow : LivePlaybackStartAction()
    data object DeferUntilResume : LivePlaybackStartAction()
    data object StartWithForegroundService : LivePlaybackStartAction()
}

/**
 * Pure host-pause matrix for unit tests and [LivePlaybackSession].
 */
internal fun resolveLivePlaybackHostPauseAction(
    allowBackgroundPlayback: Boolean,
    playerAutoPause: Boolean,
    hasPlaybackIntent: Boolean,
    inPictureInPicture: Boolean = false
): LivePlaybackHostPauseAction {
    // A PiP window is still an on-screen video surface: ON_PAUSE there is not a background
    // transition, so playback must keep running without any foreground-service handoff.
    if (inPictureInPicture) return LivePlaybackHostPauseAction.NoOp
    val lifecyclePausesPlayback = shouldLifecyclePauseLivePlayback(
        allowBackgroundPlayback = allowBackgroundPlayback,
        playerAutoPause = playerAutoPause
    )
    return when {
        lifecyclePausesPlayback -> LivePlaybackHostPauseAction.Pause
        shouldStartPlaybackForegroundService(
            lifecyclePausesPlayback = lifecyclePausesPlayback,
            isPlaying = hasPlaybackIntent
        ) -> LivePlaybackHostPauseAction.StartForegroundService
        else -> LivePlaybackHostPauseAction.NoOp
    }
}


internal fun resolveLivePlaybackStartAction(
    hostPaused: Boolean,
    allowBackgroundPlayback: Boolean,
    playerAutoPause: Boolean
): LivePlaybackStartAction {
    if (!hostPaused) return LivePlaybackStartAction.StartNow
    return if (shouldLifecyclePauseLivePlayback(allowBackgroundPlayback, playerAutoPause)) {
        LivePlaybackStartAction.DeferUntilResume
    } else {
        LivePlaybackStartAction.StartWithForegroundService
    }
}
