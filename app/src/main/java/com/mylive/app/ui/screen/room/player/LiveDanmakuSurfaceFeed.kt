package com.mylive.app.ui.screen.room.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mylive.app.core.model.LiveMessage
import kotlinx.coroutines.flow.SharedFlow

/**
 * Binds a live-message event stream to a [DanmakuController] surface.
 *
 * Hides the collect/clear fan-out that previously lived in portrait, landscape, and PiP branches.
 * Chat list snapshots stay on a separate StateFlow path and are intentionally not handled here.
 */
@Composable
fun LiveDanmakuSurfaceFeed(
    messages: SharedFlow<LiveMessage>,
    controller: DanmakuController?,
    active: Boolean,
    clearWhenInactive: Boolean = false,
    playbackState: StateFlow<PlayerState>? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val playerState by (playbackState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(PlayerState(isPlaying = true, isLoading = false)) })
    val displayActive = active && shouldDisplayPlaybackDanmaku(playerState)
    LaunchedEffect(controller, displayActive, clearWhenInactive, messages, lifecycleOwner) {
        val target = controller
        if (target != null && displayActive) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    messages.collect { message -> target.addDanmaku(message) }
                } finally {
                    target.clear()
                }
            }
        } else if (clearWhenInactive || !displayActive) {
            target?.clear()
        }
    }
}

/** Pure policy for whether PiP should feed the surface danmaku controller. */
internal fun shouldFeedPipDanmakuSurface(
    isInPip: Boolean,
    danmuEnable: Boolean,
    pipHideDanmu: Boolean,
    routeActive: Boolean
): Boolean = isInPip && danmuEnable && !pipHideDanmu && routeActive

/** Pure policy for normal (non-PiP) surface feeding. */
internal fun shouldFeedLiveDanmakuSurface(
    hasController: Boolean,
    routeActive: Boolean,
    enabled: Boolean = true
): Boolean = hasController && routeActive && enabled

internal fun shouldDisplayPlaybackDanmaku(state: PlayerState): Boolean =
    state.isPlaying && !state.isLoading && state.error == null
