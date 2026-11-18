package com.mylive.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class DesktopPlaybackLazyTest {
    @Test fun configuringAndClosingAnIdlePlayerDoesNotLoadTheNativePlayer() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val playback = MpvDesktopPlayback(Any(), scope)
            playback.applyPreferences(DesktopPlayerPreferences(volume = 0f, muted = true, hardwareDecode = false))
            playback.setVolume(25f)
            playback.beginRoom()
            assertTrue(playback.playbackRequested)
            playback.pause()
            assertFalse(playback.playbackRequested)
            playback.resume()
            assertTrue(playback.playbackRequested)
            playback.stop()
            assertFalse(playback.playbackRequested)
            assertEquals(PlaybackDiagnostics(), playback.diagnostics())
            playback.close()
            assertFalse(playback.playerInitialized)
        } finally {
            scope.cancel()
        }
    }

    @Test fun hidingARoomDuringLoadingPreservesThePauseBeforeNativeInitialization() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val playback = MpvDesktopPlayback(Any(), scope)
            val visibility = DesktopVisibilityPolicy()
            playback.beginRoom()
            assertEquals(VisibilityPlaybackAction.Pause, visibility.update(false, false, playback.playbackRequested, true))
            playback.pause()
            assertFalse(playback.playbackRequested)
            assertEquals(VisibilityPlaybackAction.Resume, visibility.update(true, false, playback.playbackRequested, true))
            playback.resume()
            assertTrue(playback.playbackRequested)
            assertFalse(playback.playerInitialized)
            playback.close()
        } finally {
            scope.cancel()
        }
    }
}
