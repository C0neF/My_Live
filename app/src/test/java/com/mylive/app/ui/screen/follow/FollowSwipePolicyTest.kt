package com.mylive.app.ui.screen.follow

import org.junit.Assert.assertEquals
import org.junit.Test

class FollowSwipePolicyTest {

    @Test
    fun actionButtonsAreHiddenWhenSwipeIsClosed() {
        assertEquals(0f, followSwipeActionAlpha(offsetX = 0f), 0.001f)
        assertEquals(0f, followSwipeActionAlpha(offsetX = 50f), 0.001f)
        assertEquals(0f, followSwipeActionAlpha(offsetX = -0.5f), 0.001f)
    }

    @Test
    fun actionButtonsUseFixedLayoutAsSoonAsSwipeOpens() {
        assertEquals(1f, followSwipeActionAlpha(offsetX = -2f), 0.001f)
        assertEquals(1f, followSwipeActionAlpha(offsetX = -200f), 0.001f)
    }

    @Test
    fun settledOffsetSnapsOpenAfterOneThirdReveal() {
        assertEquals(0f, followSwipeSettledOffset(offsetX = -60f, totalReveal = 200f), 0.001f)
        assertEquals(-200f, followSwipeSettledOffset(offsetX = -80f, totalReveal = 200f), 0.001f)
    }

    @Test
    fun settledOffsetClosesForInvalidRevealWidth() {
        assertEquals(0f, followSwipeSettledOffset(offsetX = -100f, totalReveal = 0f), 0.001f)
    }
}
