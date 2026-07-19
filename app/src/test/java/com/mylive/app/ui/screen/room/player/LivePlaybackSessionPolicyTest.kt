package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LivePlaybackSessionPolicyTest {

    @Test
    fun lifecyclePausePolicyMatchesBackgroundAndAutoPauseMatrix() {
        assertTrue(
            shouldLifecyclePauseLivePlayback(
                allowBackgroundPlayback = false,
                playerAutoPause = false
            )
        )
        assertTrue(
            shouldLifecyclePauseLivePlayback(
                allowBackgroundPlayback = true,
                playerAutoPause = true
            )
        )
        assertFalse(
            shouldLifecyclePauseLivePlayback(
                allowBackgroundPlayback = true,
                playerAutoPause = false
            )
        )
    }

    @Test
    fun foregroundServiceOnlyWhenPlaybackKeepsRunningInBackground() {
        assertTrue(
            shouldStartPlaybackForegroundService(
                lifecyclePausesPlayback = false,
                isPlaying = true
            )
        )
        assertFalse(
            shouldStartPlaybackForegroundService(
                lifecyclePausesPlayback = true,
                isPlaying = true
            )
        )
        assertFalse(
            shouldStartPlaybackForegroundService(
                lifecyclePausesPlayback = false,
                isPlaying = false
            )
        )
    }

    @Test
    fun sessionOwnsControllerAttachDetachAndForegroundServiceHandoff() {
        val source = readMainSource("com/mylive/app/ui/screen/room/player/LivePlaybackSession.kt")

        assertTrue(source.contains("class LivePlaybackSession"))
        assertTrue(source.contains("fun attach(viewModel: LiveRoomViewModel)"))
        assertTrue(source.contains("viewModel.bindPlaybackEngine(engine)"))
        assertTrue(source.contains("viewModel.onPlayerControllerReady()"))
        assertTrue(source.contains("viewModel?.unbindPlaybackEngine(engine)"))
        assertTrue(source.contains("resolveLivePlaybackHostPauseAction("))
        assertTrue(source.contains("PlaybackForegroundService.start("))
        assertTrue(source.contains("PlaybackForegroundService.stop("))
        assertTrue(source.contains("fun onHostPause("))
        assertTrue(source.contains("fun onHostResume()"))
        assertTrue(source.contains("if (resumePlaybackOnForeground)"))
        assertTrue(source.contains("controller.release()"))
        assertTrue(source.contains("fun setAutoPipActive(active: Boolean)"))
        assertTrue(source.contains("LivePlaybackHostSignals.setAutoPipOnLeave"))
        assertFalse(source.contains("MainActivity.isPipSupportedAndActive"))
    }

    @Test
    fun hostPauseActionMatrixCoversBackgroundAndAutoPause() {
        assertTrue(
            resolveLivePlaybackHostPauseAction(
                allowBackgroundPlayback = false,
                playerAutoPause = false,
                isPlaying = true
            ) is LivePlaybackHostPauseAction.Pause
        )
        assertTrue(
            resolveLivePlaybackHostPauseAction(
                allowBackgroundPlayback = true,
                playerAutoPause = false,
                isPlaying = true
            ) is LivePlaybackHostPauseAction.StartForegroundService
        )
        assertTrue(
            resolveLivePlaybackHostPauseAction(
                allowBackgroundPlayback = true,
                playerAutoPause = false,
                isPlaying = false
            ) is LivePlaybackHostPauseAction.NoOp
        )
    }

    @Test
    fun liveRoomScreenDelegatesPlaybackOwnershipToSession() {
        val screen = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertTrue(screen.contains("LivePlaybackSession.create("))
        assertTrue(screen.contains("onPlaybackSourceExhausted = {"))
        assertTrue(screen.contains("viewModel.recoverPlaybackAfterSourceFailure()"))
        assertTrue(screen.contains("session.attach(viewModel)"))
        assertTrue(screen.contains("session?.release()") || screen.contains("session.release()"))
        assertTrue(screen.contains("playbackSession?.onHostPause("))
        assertTrue(screen.contains("playbackSession?.onHostResume()"))
        assertTrue(screen.contains("LivePlaybackSession.setAutoPipActive("))
        assertFalse(
            "Screen must not construct PlayerController directly after session extraction",
            screen.contains("PlayerController(\n") || screen.contains("PlayerController(context")
        )
    }

    private fun readMainSource(relativePath: String): String {
        return File("src/main/java/$relativePath").readText()
    }
}
