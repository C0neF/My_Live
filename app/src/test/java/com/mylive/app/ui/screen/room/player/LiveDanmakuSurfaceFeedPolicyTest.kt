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
                isExiting = false
            )
        )
        assertFalse(
            shouldFeedPipDanmakuSurface(
                isInPip = true,
                danmuEnable = true,
                pipHideDanmu = true,
                isExiting = false
            )
        )
        assertFalse(
            shouldFeedPipDanmakuSurface(
                isInPip = false,
                danmuEnable = true,
                pipHideDanmu = false,
                isExiting = false
            )
        )
        assertFalse(
            shouldFeedPipDanmakuSurface(
                isInPip = true,
                danmuEnable = true,
                pipHideDanmu = false,
                isExiting = true
            )
        )
    }

    @Test
    fun normalSurfaceFeedStopsWhileExiting() {
        assertTrue(shouldFeedLiveDanmakuSurface(hasController = true, isExiting = false))
        assertFalse(shouldFeedLiveDanmakuSurface(hasController = true, isExiting = true))
        assertFalse(shouldFeedLiveDanmakuSurface(hasController = false, isExiting = false))
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
}
