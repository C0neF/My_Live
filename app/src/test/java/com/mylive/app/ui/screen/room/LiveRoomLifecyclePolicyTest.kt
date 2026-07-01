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
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()
        val lifecycleEffect = source.substringAfter(
            "DisposableEffect(lifecycleOwner, playerController, allowBackgroundPlayback, playerAutoPause, uiState.detail)"
        ).substringBefore("// Room idle auto exit state")
        val resumeBranch = lifecycleEffect.substringAfter("Lifecycle.Event.ON_RESUME ->")
            .substringBefore("else ->")

        assertTrue(source.contains("var resumePlaybackOnForeground"))
        assertTrue(resumeBranch.contains("if (resumePlaybackOnForeground)"))
        assertFalse(resumeBranch.contains("\n                    playerController?.resume()"))
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
