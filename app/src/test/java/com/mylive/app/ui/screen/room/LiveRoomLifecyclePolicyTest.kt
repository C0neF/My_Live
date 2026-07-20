package com.mylive.app.ui.screen.room

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
        val screen = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val session = File(
            "src/main/java/com/mylive/app/ui/screen/room/player/LivePlaybackSession.kt"
        ).readText()
        val lifecycleEffect = screen.substringAfter(
            "DisposableEffect(\n        lifecycleOwner,\n        playbackSession,"
        ).substringBefore("// Room idle auto exit state")
        val resumeBranch = lifecycleEffect.substringAfter("Lifecycle.Event.ON_RESUME ->")
            .substringBefore("else ->")

        assertTrue(screen.contains("playbackSession?.onHostResume()"))
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
