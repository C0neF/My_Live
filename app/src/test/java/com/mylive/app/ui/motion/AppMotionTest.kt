package com.mylive.app.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMotionTest {

    @Test
    fun routeTimingMatchesMyLiveGetxCupertinoBaseline() {
        assertEquals(300, AppMotion.RouteDurationMillis)
        assertEquals(300, AppMotion.RoutePopDurationMillis)
        assertEquals(1f / 3f, AppMotion.RouteParallaxFactor, 0.001f)
        assertEquals(333, AppMotion.routeParallaxOffset(1000))
    }

    @Test
    fun liveRoomRouteUsesSharedMyLiveGetxCupertinoMotion() {
        assertEquals(AppMotion.RouteDurationMillis, AppMotion.LiveRoomEnterDurationMillis)
        assertEquals(AppMotion.RoutePopDurationMillis, AppMotion.LiveRoomExitDurationMillis)
        assertEquals(AppMotion.RouteParallaxFactor, AppMotion.LiveRoomParallaxFactor, 0.001f)
        assertSame(AppMotion.MyLivePrimaryForwardEasing, AppMotion.LiveRoomEnterEasing)
        assertSame(AppMotion.MyLivePrimaryPopEasing, AppMotion.LiveRoomExitEasing)
    }

    @Test
    fun liveRoomParallaxKeepsFlutterCupertinoDepthCue() {
        assertEquals(1f / 3f, AppMotion.LiveRoomParallaxFactor, 0.001f)
        assertEquals(333, AppMotion.liveRoomParallaxOffset(1000))
        assertEquals(AppMotion.routeParallaxOffset(1000), AppMotion.liveRoomParallaxOffset(1000))
    }

    @Test
    fun myLiveCupertinoUsesDifferentPrimaryAndSecondaryCurves() {
        assertSame(AppMotion.MyLivePrimaryForwardEasing, AppMotion.LiveRoomEnterEasing)
        assertSame(AppMotion.MyLivePrimaryPopEasing, AppMotion.LiveRoomExitEasing)
        assertNotSame(AppMotion.MyLiveSecondaryForwardEasing, AppMotion.MyLivePrimaryForwardEasing)
        assertNotSame(AppMotion.MyLiveSecondaryPopEasing, AppMotion.MyLivePrimaryPopEasing)
    }

    @Test
    fun liveRoomStartupWorkStartsAfterEnterTransition() {
        assertEquals(320, AppMotion.LiveRoomDataStartupDelayMillis)
        assertEquals(360, AppMotion.LiveRoomPlayerStartupDelayMillis)
        assertTrue(AppMotion.LiveRoomDataStartupDelayMillis > AppMotion.LiveRoomEnterDurationMillis)
        assertTrue(AppMotion.LiveRoomPlayerStartupDelayMillis > AppMotion.LiveRoomDataStartupDelayMillis)
    }

    @Test
    fun pagerDurationIsBoundedForNearAndFarTabs() {
        assertEquals(220, AppMotion.pagerDurationMillis(0))
        assertEquals(220, AppMotion.pagerDurationMillis(1))
        assertEquals(220, AppMotion.pagerDurationMillis(-1))
        assertEquals(260, AppMotion.pagerDurationMillis(2))
        assertEquals(260, AppMotion.pagerDurationMillis(-3))
    }

    @Test
    fun farPagerJumpUsesTargetAdjacentPage() {
        assertEquals(null, AppMotion.preJumpPageForTarget(currentPage = 0, targetPage = 1))
        assertEquals(null, AppMotion.preJumpPageForTarget(currentPage = 2, targetPage = 1))
        assertEquals(2, AppMotion.preJumpPageForTarget(currentPage = 0, targetPage = 3))
        assertEquals(1, AppMotion.preJumpPageForTarget(currentPage = 3, targetPage = 0))
    }

    @Test
    fun indexDirectionReportsForwardBackwardAndSamePage() {
        assertEquals(1, AppMotion.indexDirection(fromIndex = 0, toIndex = 2))
        assertEquals(-1, AppMotion.indexDirection(fromIndex = 3, toIndex = 1))
        assertEquals(0, AppMotion.indexDirection(fromIndex = 2, toIndex = 2))
    }

    @Test
    fun parallaxOffsetUsesOneThirdOfContainerWidth() {
        assertEquals(333, AppMotion.routeParallaxOffset(1000))
        assertTrue(AppMotion.routeParallaxOffset(1) >= 0)
    }
}
