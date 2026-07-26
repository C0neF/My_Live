package com.mylive.app.ui.screen.room

import com.mylive.app.ui.motion.AppMotion
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Room entry staggers the *first heavy recomposition* behind the route animation — not the fetch.
 *
 * It used to `delay(320)` before loading anything, which put a fixed third of a second in front of
 * the network, and the network is the long pole (three round trips before the first video frame).
 * The fetch never threatened the animation in the first place: it runs on Dispatchers.IO.
 */
class RoomEntryLatencyPolicyTest {

    @Test
    fun aFetchSlowerThanTheAnimationCostsNoExtraWait() {
        // The common case on a real network: by the time the detail is back, the transition has
        // long finished, so the room renders the moment the data exists.
        assertEquals(0L, routeTransitionRemainingMillis(elapsedMillis = 800))
        assertEquals(
            0L,
            routeTransitionRemainingMillis(
                elapsedMillis = AppMotion.LiveRoomDataStartupDelayMillis.toLong()
            )
        )
    }

    @Test
    fun aFetchFasterThanTheAnimationWaitsOutOnlyTheRemainder() {
        // Cache hit or LAN-fast response: still let the animation finish, but only the part of it
        // that is actually left.
        assertEquals(
            AppMotion.LiveRoomDataStartupDelayMillis - 100L,
            routeTransitionRemainingMillis(elapsedMillis = 100)
        )
    }

    @Test
    fun anInstantFetchWaitsTheWholeWindowAndNoMore() {
        assertEquals(
            AppMotion.LiveRoomDataStartupDelayMillis.toLong(),
            routeTransitionRemainingMillis(elapsedMillis = 0)
        )
    }

    @Test
    fun theWindowIsNeverNegative() {
        // A clock that jumped, or a very slow load, must not turn into a negative delay.
        assertEquals(0L, routeTransitionRemainingMillis(elapsedMillis = Long.MAX_VALUE / 2))
    }

    @Test
    fun theWindowStillCoversTheRouteAnimation() {
        // The stagger only makes sense while it outlasts the transition it is hiding behind.
        assert(
            AppMotion.LiveRoomDataStartupDelayMillis >= AppMotion.LiveRoomEnterDurationMillis
        ) {
            "data stagger (${AppMotion.LiveRoomDataStartupDelayMillis}ms) must cover the route " +
                "animation (${AppMotion.LiveRoomEnterDurationMillis}ms)"
        }
    }
}
