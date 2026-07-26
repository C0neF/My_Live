package com.mylive.app.ui.screen.room.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val host = File(
            "src/main/java/com/mylive/app/ui/screen/room/player/LivePlaybackHost.kt"
        ).readText()
        assertTrue(
            host.contains("componentActivity?.isInPictureInPictureMode == true")
        )
        assertTrue(host.contains("playbackSession?.onHostResume()"))
    }

    @Test
    fun thePipWindowRendersOnlyTheVideoSurface() {
        // Controls and the loading spinner are laid out at full size and would cover the whole
        // floating window. Asserted through the rules themselves rather than by matching the
        // rendering code's text, which broke on an unrelated reformat.
        assertFalse(
            shouldShowPlayerControls(controlsVisible = true, isInPictureInPicture = true)
        )
        assertFalse(
            shouldShowPlayerLoading(
                isLoading = true,
                hasError = false,
                hasController = true,
                isInPictureInPicture = true
            )
        )

        // ...and both are drawn normally in the full-size window.
        assertTrue(
            shouldShowPlayerControls(controlsVisible = true, isInPictureInPicture = false)
        )
        assertTrue(
            shouldShowPlayerLoading(
                isLoading = true,
                hasError = false,
                hasController = true,
                isInPictureInPicture = false
            )
        )
    }

    @Test
    fun aSurfaceIsPictureInPictureOnlyWhenItsCallerSaysSo() {
        // Default false: every non-PiP call site gets the full-size treatment without opting in.
        assertFalse(PlayerViewUiState().isInPictureInPicture)
        assertTrue(PlayerViewUiState(isInPictureInPicture = true).isInPictureInPicture)
    }

    @Test
    fun theRoomDeclaresItsPipSurface() {
        // Wiring check: some call site has to actually pass the flag. Kept to a single stable
        // named-argument token instead of slicing the file into branches by brace text.
        val screen = File(
            "src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt"
        ).readText()
        assertTrue(screen.contains("isInPictureInPicture = true"))
    }
}
