package com.mylive.app.ui.screen.room

import com.mylive.app.ui.screen.room.player.shouldResumeLivePlaybackOnForeground
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomLifecyclePolicyTest {

    @Test
    fun foregroundResumeRequiresLifecycleToPauseActivePlayback() {
        assertTrue(
            shouldResumeLivePlaybackOnForeground(
                lifecyclePausedPlayback = true,
                wasPlayingBeforePause = true
            )
        )
        assertFalse(
            shouldResumeLivePlaybackOnForeground(
                lifecyclePausedPlayback = true,
                wasPlayingBeforePause = false
            )
        )
        assertFalse(
            shouldResumeLivePlaybackOnForeground(
                lifecyclePausedPlayback = false,
                wasPlayingBeforePause = true
            )
        )
    }

    @Test
    fun liveRoomDoesNotUnconditionallyResumePlaybackOnForeground() {
        val host = File(
            "src/main/java/com/mylive/app/ui/screen/room/player/LivePlaybackHost.kt"
        ).readText()
        val session = File(
            "src/main/java/com/mylive/app/ui/screen/room/player/LivePlaybackSession.kt"
        ).readText()
        val lifecycleEffect = host.substringAfter("val observer = LifecycleEventObserver")
            .substringBefore("// Some OEMs deliver ON_PAUSE")
        val resumeBranch = lifecycleEffect.substringAfter("Lifecycle.Event.ON_RESUME ->")
            .substringBefore("else ->")

        assertTrue(host.contains("playbackSession?.onHostResume()"))
        assertTrue(resumeBranch.contains("playbackSession?.onHostResume()"))
        assertFalse(resumeBranch.contains("playbackSession?.resume()"))
        assertTrue(session.contains("private var resumePlaybackOnForeground"))
        assertTrue(session.contains("if (resumePlaybackOnForeground)"))
        assertTrue(session.contains("controller.resume()"))
    }

    @Test
    fun topLevelLiveRoomDoesNotKeepUnreachableQuickAccessPanelState() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val topLevelScreen = source.substringAfter("fun LiveRoomScreen(")
            .substringBefore("private fun PortraitLayout(")

        assertFalse(topLevelScreen.contains("var showQuickAccess"))
        assertFalse(topLevelScreen.contains("if (showQuickAccess)"))
    }
}
