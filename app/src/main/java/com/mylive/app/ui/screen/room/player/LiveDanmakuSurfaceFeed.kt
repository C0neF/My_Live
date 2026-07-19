package com.mylive.app.ui.screen.room.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    clearWhenInactive: Boolean = false
) {
    LaunchedEffect(controller, active, clearWhenInactive, messages) {
        val target = controller
        if (target != null && active) {
            messages.collect { message ->
                if (active) {
                    target.addDanmaku(message)
                }
            }
        } else if (clearWhenInactive) {
            target?.clear()
        }
    }
}

/** Pure policy for whether PiP should feed the surface danmaku controller. */
internal fun shouldFeedPipDanmakuSurface(
    isInPip: Boolean,
    danmuEnable: Boolean,
    pipHideDanmu: Boolean,
    isExiting: Boolean
): Boolean = isInPip && danmuEnable && !pipHideDanmu && !isExiting

/** Pure policy for normal (non-PiP) surface feeding. */
internal fun shouldFeedLiveDanmakuSurface(
    hasController: Boolean,
    isExiting: Boolean
): Boolean = hasController && !isExiting
