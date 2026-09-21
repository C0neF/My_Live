package com.mylive.app.ui.screen.room

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/** Keep list/slider gestures inside the content; only the separate handle can drag the sheet. */
internal fun Modifier.roomPanelContentScrollIsolation(): Modifier = composed {
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                Offset(x = 0f, y = available.y)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(x = 0f, y = available.y)
        }
    }
    nestedScroll(connection).pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var total = Offset.Zero
            var directionDecided = false
            var verticalGesture = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                total += change.position - change.previousPosition
                if (!directionDecided && (abs(total.x) > viewConfiguration.touchSlop || abs(total.y) > viewConfiguration.touchSlop)) {
                    directionDecided = true
                    verticalGesture = abs(total.y) > abs(total.x)
                }
                if (verticalGesture && !change.isConsumed) change.consume()
                if (!change.pressed) break
            }
        }
    }
}

internal fun Modifier.roomPanelDismissHandleGesture(
    onDownwardDragStarted: () -> Unit,
    onGestureActiveChange: (Boolean) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onGestureActiveChange(true)
        try {
            var total = Offset.Zero
            var decided = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                total += change.position - change.previousPosition
                if (!decided && (abs(total.x) > viewConfiguration.touchSlop || abs(total.y) > viewConfiguration.touchSlop)) {
                    decided = true
                    if (total.y > 0 && total.y > abs(total.x)) onDownwardDragStarted()
                }
                if (!change.pressed) break
            }
        } finally {
            onGestureActiveChange(false)
        }
    }
}
