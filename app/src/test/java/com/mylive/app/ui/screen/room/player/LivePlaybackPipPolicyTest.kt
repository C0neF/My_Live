package com.mylive.app.ui.screen.room.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Picture-in-picture is a visible playback surface, not a background state, and the floating
 * window is only large enough for the video itself.
 */
class LivePlaybackPipPolicyTest {

    @Test
    fun pictureInPictureNeverPausesPlaybackNorStartsTheForegroundService() {
        val settingCombinations = listOf(
            true to true,
            true to false,
            false to true,
            false to false
        )
        for ((allowBackgroundPlayback, playerAutoPause) in settingCombinations) {
            for (hasPlaybackIntent in listOf(true, false)) {
                assertEquals(
                    "allowBackground=$allowBackgroundPlayback autoPause=$playerAutoPause playing=$hasPlaybackIntent",
                    LivePlaybackHostPauseAction.NoOp,
                    resolveLivePlaybackHostPauseAction(
                        allowBackgroundPlayback = allowBackgroundPlayback,
                        playerAutoPause = playerAutoPause,
                        hasPlaybackIntent = hasPlaybackIntent,
                        inPictureInPicture = true
                    )
                )
            }
        }
    }

    @Test
    fun leavingTheRoomWithoutPipStillHonorsTheBackgroundPolicy() {
        assertEquals(
            LivePlaybackHostPauseAction.Pause,
            resolveLivePlaybackHostPauseAction(
                allowBackgroundPlayback = false,
                playerAutoPause = true,
                hasPlaybackIntent = true,
                inPictureInPicture = false
            )
        )
        assertEquals(
            LivePlaybackHostPauseAction.StartForegroundService,
            resolveLivePlaybackHostPauseAction(
                allowBackgroundPlayback = true,
                playerAutoPause = false,
                hasPlaybackIntent = true,
                inPictureInPicture = false
            )
        )
    }

    @Test
    fun hostPauseShortCircuitsWhilePictureInPictureIsShowing() {
        val source = File(
            "src/main/java/com/mylive/app/ui/screen/room/player/LivePlaybackSession.kt"
        ).readText()
        val onHostPause = source.substringAfter("fun onHostPause(")
        assertTrue(onHostPause.contains("inPictureInPicture: Boolean = false"))
        val body = onHostPause.substringAfter("pausedHostPolicy = policy")
        val pipShortCircuit = body.indexOf("if (inPictureInPicture) {")
        val hostPausedFlag = body.indexOf("hostPaused = true")
        assertTrue(pipShortCircuit >= 0)
        assertTrue(pipShortCircuit < hostPausedFlag)
    }

    @Test
    fun theRoomReportsPictureInPictureWhenTheHostPauses() {
        val screen = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt"
        ).readText()
        assertTrue(
            screen.contains("inPictureInPicture = componentActivity?.isInPictureInPictureMode == true")
        )
        assertTrue(screen.contains("playbackSession?.onHostResume()"))
    }

    @Test
    fun thePipWindowRendersOnlyTheVideoSurface() {
        val screen = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt"
        ).readText()
        // The PiP layout branch is the one that ends at the landscape/fullscreen branch.
        val pipBranch = screen.substringBefore("} else if (isLandscape || isFullscreen) {")
            .substringAfterLast("if (isInPip) {")
        assertTrue(pipBranch.contains("isInPipMode = true"))

        val player = File(
            "src/main/java/com/mylive/app/ui/screen/room/player/PlayerView.kt"
        ).readText()
        assertTrue(player.contains("isInPipMode: Boolean = false"))
        // Controls, lock buttons and the loading spinner are all suppressed in the small window.
        assertTrue(player.contains("visible = controlsVisible && !isInPipMode"))
        assertTrue(
            player.contains(
                "if (state.isLoading && state.error == null && playerController != null && !isInPipMode) {"
            )
        )
    }
}
