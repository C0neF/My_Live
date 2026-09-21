package com.mylive.app.ui.screen.room.quickaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAccessHeaderScrollPolicyTest {

    @Test
    fun upwardScrollHidesOnlyAfterThreshold() {
        var state = QuickAccessHeaderScrollState(visible = true)

        state = reduceQuickAccessHeaderScroll(state, deltaY = -4f, thresholdPx = 10f)
        assertTrue(state.visible)
        state = reduceQuickAccessHeaderScroll(state, deltaY = -7f, thresholdPx = 10f)
        assertFalse(state.visible)
    }

    @Test
    fun downwardScrollKeepsHeaderHiddenUntilContentReachesTop() {
        var state = QuickAccessHeaderScrollState(visible = false)

        for (distance in listOf(4f, 7f, 200f, 1_000f)) {
            state = reduceQuickAccessHeaderScroll(state, deltaY = distance, thresholdPx = 10f)
            assertFalse(state.visible)
            assertEquals(0f, state.accumulatedDeltaY, 0.001f)
        }
    }

    @Test
    fun reachingTopShowsHeaderWithoutRequiringAnotherFullThresholdDrag() {
        val state = reduceQuickAccessHeaderScroll(
            QuickAccessHeaderScrollState(visible = false),
            deltaY = 0.5f,
            thresholdPx = 10f,
            reachedTop = true
        )

        assertTrue(state.visible)
        assertEquals(0f, state.accumulatedDeltaY, 0.001f)
    }

    @Test
    fun topBoundaryDoesNotRevealHeaderDuringHorizontalOrUpwardMovement() {
        for (distance in listOf(0f, -5f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val state = reduceQuickAccessHeaderScroll(
                QuickAccessHeaderScrollState(visible = false),
                deltaY = distance,
                thresholdPx = 10f,
                reachedTop = true
            )
            assertFalse(state.visible)
        }
    }

    @Test
    fun oppositeDirectionJitterResetsPendingHeaderMotion() {
        var state = QuickAccessHeaderScrollState(visible = true)

        state = reduceQuickAccessHeaderScroll(state, deltaY = -6f, thresholdPx = 10f)
        state = reduceQuickAccessHeaderScroll(state, deltaY = 1f, thresholdPx = 10f)
        state = reduceQuickAccessHeaderScroll(state, deltaY = -5f, thresholdPx = 10f)

        assertTrue(state.visible)
        assertEquals(-5f, state.accumulatedDeltaY, 0.001f)
    }
}
