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
        assertTrue(source.contains("fun attach(binding: LivePlaybackSessionBinding)"))
        assertTrue(source.contains("binding.bindPlaybackEngine(engine)"))
        assertTrue(source.contains("binding.onPlaybackEngineReady()"))
        assertTrue(source.contains("binding?.unbindPlaybackEngine(engine)"))
        assertTrue(source.contains("resolveLivePlaybackHostPauseAction("))
        assertTrue(source.contains("PlaybackForegroundService.start("))
        assertTrue(source.contains("PlaybackForegroundService.stop("))
        assertTrue(source.contains("fun onHostPause("))
        assertTrue(source.contains("fun onHostResume()"))
        assertTrue(source.contains("if (resumePlaybackOnForeground)"))
        assertTrue(source.contains("controller.release()"))
        assertTrue(source.contains("fun setAutoPipActive(owner: Any, active: Boolean)"))
        assertTrue(source.contains("LivePlaybackHostSignals.setAutoPipOnLeave"))
        assertFalse(source.contains("MainActivity.isPipSupportedAndActive"))
    }

    @Test
    fun hostPauseActionMatrixCoversBackgroundAndAutoPause() {
        assertTrue(
            resolveLivePlaybackHostPauseAction(
                allowBackgroundPlayback = false,
                playerAutoPause = false,
                hasPlaybackIntent = true
            ) is LivePlaybackHostPauseAction.Pause
        )
        assertTrue(
            resolveLivePlaybackHostPauseAction(
                allowBackgroundPlayback = true,
                playerAutoPause = false,
                hasPlaybackIntent = true
            ) is LivePlaybackHostPauseAction.StartForegroundService
        )
        assertTrue(
            resolveLivePlaybackHostPauseAction(
                allowBackgroundPlayback = true,
                playerAutoPause = false,
                hasPlaybackIntent = false
            ) is LivePlaybackHostPauseAction.NoOp
        )
    }

    @Test
    fun playbackStartWhileHostPausedFollowsBackgroundPolicy() {
        assertTrue(
            resolveLivePlaybackStartAction(
                hostPaused = false,
                allowBackgroundPlayback = false,
                playerAutoPause = false
            ) is LivePlaybackStartAction.StartNow
        )
        assertTrue(
            resolveLivePlaybackStartAction(
                hostPaused = true,
                allowBackgroundPlayback = false,
                playerAutoPause = false
            ) is LivePlaybackStartAction.DeferUntilResume
        )
        assertTrue(
            resolveLivePlaybackStartAction(
                hostPaused = true,
                allowBackgroundPlayback = true,
                playerAutoPause = false
            ) is LivePlaybackStartAction.StartWithForegroundService
        )
        assertTrue(
            resolveLivePlaybackStartAction(
                hostPaused = true,
                allowBackgroundPlayback = true,
                playerAutoPause = true
            ) is LivePlaybackStartAction.DeferUntilResume
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
        assertTrue(screen.contains("Lifecycle.State.RESUMED"))
        assertTrue(screen.contains("LivePlaybackSession.setAutoPipActive(autoPipOwner,"))
        val startupBlock = screen.substringAfter("LaunchedEffect(Unit)")
            .substringBefore("DisposableEffect(playbackSession)")
        assertTrue(startupBlock.indexOf("session.onHostPause(") in 0 until startupBlock.indexOf("session.attach(viewModel)"))
        assertTrue(startupBlock.contains("allowBackgroundPlayback = startupPreferences.allowBackgroundPlayback"))
        assertTrue(startupBlock.contains("playerAutoPause = startupPreferences.playerAutoPause"))
        val lifecycleBlock = screen.substringAfter("lifecycleOwner.lifecycle.addObserver(observer)")
            .substringBefore("onDispose {")
        assertTrue(lifecycleBlock.contains("playbackSession?.onHostResume()"))
        assertFalse(
            "Screen must not construct PlayerController directly after session extraction",
            screen.contains("PlayerController(\n") || screen.contains("PlayerController(context")
        )
    }

    private fun readMainSource(relativePath: String): String {
        return File("src/main/java/$relativePath").readText()
    }
}
