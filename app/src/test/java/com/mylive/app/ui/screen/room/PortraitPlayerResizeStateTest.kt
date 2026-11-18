package com.mylive.app.ui.screen.room

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PortraitPlayerResizeStateTest {
    @Test fun dragConsumesOnlyTheDistanceWithinThePlayerBounds() {
        val state = PortraitPlayerResizeState()
        assertEquals(-120f, state.consumeDragDelta(-120f, 180f, 520f), 0.001f)
        assertEquals(220f / 340f, state.expansionFraction, 0.001f)
        assertEquals(-220f, state.consumeDragDelta(-500f, 180f, 520f), 0.001f)
        assertEquals(0f, state.consumeDragDelta(-20f, 180f, 520f), 0.001f)
        assertEquals(340f, state.consumeDragDelta(500f, 180f, 520f), 0.001f)
        assertEquals(0f, state.consumeDragDelta(20f, 180f, 520f), 0.001f)
    }

    @Test fun invalidOrUnresizableWindowsDoNotConsumeChatScrolling() {
        val state = PortraitPlayerResizeState()
        listOf(
            Triple(Float.NaN, 180f, 520f), Triple(-20f, 180f, 180f),
            Triple(-20f, 180f, Float.POSITIVE_INFINITY), Triple(-20f, -1f, 520f)
        ).forEach { (delta, minimum, maximum) ->
            assertEquals(0f, state.consumeDragDelta(delta, minimum, maximum), 0f)
            assertFalse(state.isResizing)
        }
        assertEquals(1f, state.expansionFraction, 0f)
    }

    @Test fun changingTheViewportPreservesThePreferredProportion() {
        val state = PortraitPlayerResizeState()
        state.consumeDragDelta(-170f, 180f, 520f)
        assertEquals(0.5f, state.expansionFraction, 0.001f)
        assertEquals(-100f, state.consumeDragDelta(-100f, 180f, 380f), 0.001f)
        assertEquals(0f, state.expansionFraction, 0.001f)
    }

    @Test fun programmaticAndHorizontalScrollNeverResizeThePlayer() {
        val state = PortraitPlayerResizeState()
        val connection = PortraitPlayerResizeConnection(state, 180f, 520f)
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, -100f), NestedScrollSource.SideEffect))
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(100f, 0f), NestedScrollSource.UserInput))
        assertEquals(1f, state.expansionFraction, 0f)
        assertEquals(Offset(0f, -100f), connection.onPreScroll(Offset(40f, -100f), NestedScrollSource.UserInput))
        connection.onPreScroll(Offset(20f, 0f), NestedScrollSource.UserInput)
        assertTrue(state.isResizing)
    }

    @Test fun releasingAResizeConsumesItsFlingButChatFlingAtTheLimitIsPreserved() = runBlocking {
        val state = PortraitPlayerResizeState()
        val connection = PortraitPlayerResizeConnection(state, 180f, 520f)
        connection.onPreScroll(Offset(0f, -100f), NestedScrollSource.UserInput)
        assertEquals(Velocity(0f, -800f), connection.onPreFling(Velocity(30f, -800f)))
        assertFalse(state.isResizing)
        connection.onPreScroll(Offset(0f, -500f), NestedScrollSource.UserInput)
        connection.onPreScroll(Offset(0f, -20f), NestedScrollSource.UserInput)
        assertEquals(Velocity.Zero, connection.onPreFling(Velocity(0f, -800f)))
    }
}
