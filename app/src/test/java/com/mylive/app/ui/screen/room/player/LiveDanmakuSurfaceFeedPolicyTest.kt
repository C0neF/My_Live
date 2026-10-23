package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveDanmakuSurfaceFeedPolicyTest {

    @Test
    fun pipSurfaceFeedRequiresPipEnabledAndVisibleDanmaku() {
        assertTrue(
            shouldFeedPipDanmakuSurface(
                isInPip = true,
                danmuEnable = true,
                pipHideDanmu = false,
                routeActive = true
            )
        )
        assertFalse(
            shouldFeedPipDanmakuSurface(
                isInPip = true,
                danmuEnable = true,
                pipHideDanmu = true,
                routeActive = true
            )
        )
        assertFalse(
            shouldFeedPipDanmakuSurface(
                isInPip = false,
                danmuEnable = true,
                pipHideDanmu = false,
                routeActive = true
            )
        )
        assertFalse(
            shouldFeedPipDanmakuSurface(
                isInPip = true,
                danmuEnable = true,
                pipHideDanmu = false,
                routeActive = false
            )
        )
    }

    @Test
    fun normalSurfaceFeedRequiresAnActiveRoute() {
        assertTrue(shouldFeedLiveDanmakuSurface(hasController = true, routeActive = true))
        assertFalse(shouldFeedLiveDanmakuSurface(hasController = true, routeActive = false))
        assertFalse(shouldFeedLiveDanmakuSurface(hasController = false, routeActive = true))
    }

    @Test
    fun liveRoomUsesSurfaceFeedModuleInsteadOfInlineCollectors() {
        val screen = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val feed = File(
            "src/main/java/com/mylive/app/ui/screen/room/player/LiveDanmakuSurfaceFeed.kt"
        ).readText()

        assertTrue(feed.contains("fun LiveDanmakuSurfaceFeed("))
        assertTrue(feed.contains("messages.collect"))
        assertTrue(feed.contains("target.addDanmaku(message)"))
        assertTrue(screen.contains("LiveDanmakuSurfaceFeed("))
        assertTrue(screen.contains("shouldFeedPipDanmakuSurface("))
        assertTrue(screen.contains("shouldFeedLiveDanmakuSurface("))
        assertFalse(
            "portrait/landscape/PiP must not each own raw newDanmakuMessages collectors",
            Regex("""viewModel\.newDanmakuMessages\.collect\s*\{""")
                .containsMatchIn(screen)
        )
    }
    @Test
    fun screenDanmakuIsHiddenDuringPauseBufferingOrPlaybackError() {
        assertTrue(shouldDisplayPlaybackDanmaku(PlayerState(isPlaying = true, isLoading = false)))
        assertFalse(shouldDisplayPlaybackDanmaku(PlayerState(isPlaying = false, isLoading = false)))
        assertFalse(shouldDisplayPlaybackDanmaku(PlayerState(isPlaying = true, isLoading = true)))
        assertFalse(shouldDisplayPlaybackDanmaku(PlayerState(isPlaying = true, isLoading = false, error = "failed")))
    }
}
