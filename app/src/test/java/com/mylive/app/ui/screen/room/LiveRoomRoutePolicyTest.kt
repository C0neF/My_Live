package com.mylive.app.ui.screen.room

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomRoutePolicyTest {

    @Test
    fun liveRoomScreenPassesNavigation3RouteKeyToViewModel() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt"
        ).readText()

        assertTrue(
            "Navigation3 route key must be explicitly applied to LiveRoomViewModel",
            source.contains("viewModel.openRoute(key.roomId, key.siteId)")
        )
    }

    @Test
    fun liveRoomViewModelReportsPlaybackSetupFailuresToPlayer() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt"
        ).readText()

        assertTrue(
            "empty quality list must not leave PlayerState.isLoading at its initial value",
            source.contains("暂无可播放画质")
        )
        assertTrue(
            "play URL fetch failures must be surfaced to PlayerController",
            source.contains("playerController?.showError")
        )
    }

    @Test
    fun liveRoomViewModelIgnoresStaleRouteCallbacks() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt"
        ).readText()

        assertTrue(
            "room detail loading must be bound to the route that started it",
            source.contains("private suspend fun loadRoomDetail(route:")
        )
        assertTrue(
            "play quality loading must ignore callbacks from a previous route",
            source.contains("private suspend fun loadPlayQualities(detail: LiveRoomDetail, route:")
        )
        assertTrue(
            "play URL loading must ignore callbacks from a previous route",
            source.contains("private suspend fun playWithQuality(detail: LiveRoomDetail, quality: LivePlayQuality, route:")
        )
        assertTrue(
            "danmaku startup must ignore callbacks from a previous route",
            source.contains("private fun startDanmaku(detail: LiveRoomDetail, route:")
        )
        assertTrue(
            "all async room callbacks need a shared active route check",
            source.contains("private fun isActiveRoute(route:")
        )
    }

    @Test
    fun liveRoomViewModelDoesNotHideTargetSiteFailuresAsRoomMissing() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt"
        ).readText()

        assertTrue(
            "when a route specifies siteId, target-site errors must be preserved instead of becoming 未找到直播间",
            source.contains("targetSiteError")
        )
        assertTrue(
            "target-site errors should be rethrown so the UI can show the real loading failure",
            source.contains("throw targetSiteError")
        )
    }
}
