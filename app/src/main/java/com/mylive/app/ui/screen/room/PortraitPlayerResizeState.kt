package com.mylive.app.ui.screen.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** A room owns its preferred size independently of its current window or fullscreen layout. */
@Stable
internal class PortraitPlayerResizeState(expansionFraction: Float = 1f) {
    var expansionFraction by mutableFloatStateOf(
        expansionFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
    )
        private set
    var isResizing by mutableStateOf(false)
        private set

    fun consumeDragDelta(deltaPx: Float, minimumHeightPx: Float, maximumHeightPx: Float): Float {
        if (!deltaPx.isFinite() || !minimumHeightPx.isFinite() || !maximumHeightPx.isFinite() ||
            minimumHeightPx < 0f || maximumHeightPx <= minimumHeightPx
        ) {
            isResizing = false
            return 0f
        }
        val range = maximumHeightPx - minimumHeightPx
        val currentHeight = minimumHeightPx + range * expansionFraction
        val nextHeight = (currentHeight + deltaPx).coerceIn(minimumHeightPx, maximumHeightPx)
        val consumed = nextHeight - currentHeight
        expansionFraction = (nextHeight - minimumHeightPx) / range
        isResizing = consumed != 0f
        return consumed
    }

    fun stopResizing() {
        isResizing = false
    }

    companion object {
        val Saver = Saver<PortraitPlayerResizeState, Float>(
            save = { it.expansionFraction },
            restore = { PortraitPlayerResizeState(it) }
        )
    }
}

@Composable
internal fun rememberPortraitPlayerResizeState(roomKey: Any? = null): PortraitPlayerResizeState =
    rememberSaveable(roomKey, saver = PortraitPlayerResizeState.Saver) { PortraitPlayerResizeState() }

internal class PortraitPlayerResizeConnection(
    private val state: PortraitPlayerResizeState,
    private val minimumHeightPx: Float,
    private val maximumHeightPx: Float
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
        return Offset(0f, state.consumeDragDelta(available.y, minimumHeightPx, maximumHeightPx))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val wasResizing = state.isResizing
        state.stopResizing()
        // A release midway through resizing should leave the chosen size in place.
        return if (wasResizing) Velocity(0f, available.y) else Velocity.Zero
    }
}
