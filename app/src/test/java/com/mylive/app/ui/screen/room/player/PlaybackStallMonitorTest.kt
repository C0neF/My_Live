package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStallMonitorTest {

    private fun sample(
        monitor: PlaybackStallMonitor,
        nowMillis: Long,
        positionMillis: Long = 10_000L,
        mediaToken: Long = 1L,
        playWhenReady: Boolean = true,
        isPlaying: Boolean = true,
        state: PlaybackProgressState = PlaybackProgressState.Ready
    ): PlaybackStallAction = monitor.sample(
        nowMillis = nowMillis,
        mediaToken = mediaToken,
        positionMillis = positionMillis,
        playWhenReady = playWhenReady,
        isPlaying = isPlaying,
        state = state
    )

    @Test
    fun readyPlaybackRequestsRecoveryOnlyAfterFifteenSecondsWithoutProgress() {
        val monitor = PlaybackStallMonitor()

        assertEquals(
            PlaybackStallAction.None,
            monitor.sample(
                nowMillis = 0L,
                mediaToken = 1L,
                positionMillis = 10_000L,
                playWhenReady = true,
                isPlaying = true,
                state = PlaybackProgressState.Ready
            )
        )
        assertEquals(
            PlaybackStallAction.None,
            monitor.sample(
                nowMillis = 14_999L,
                mediaToken = 1L,
                positionMillis = 10_000L,
                playWhenReady = true,
                isPlaying = true,
                state = PlaybackProgressState.Ready
            )
        )
        assertEquals(
            PlaybackStallAction.Recover,
            monitor.sample(
                nowMillis = 15_000L,
                mediaToken = 1L,
                positionMillis = 10_000L,
                playWhenReady = true,
                isPlaying = true,
                state = PlaybackProgressState.Ready
            )
        )
    }

    @Test
    fun bufferingUsesAThirtySecondGracePeriod() {
        val monitor = PlaybackStallMonitor()

        assertEquals(
            PlaybackStallAction.None,
            sample(monitor, nowMillis = 0L, isPlaying = false, state = PlaybackProgressState.Buffering)
        )
        assertEquals(
            PlaybackStallAction.None,
            sample(monitor, nowMillis = 29_999L, isPlaying = false, state = PlaybackProgressState.Buffering)
        )
        assertEquals(
            PlaybackStallAction.Recover,
            sample(monitor, nowMillis = 30_000L, isPlaying = false, state = PlaybackProgressState.Buffering)
        )
    }

    @Test
    fun userPauseNeverConsumesStallTime() {
        val monitor = PlaybackStallMonitor()

        assertEquals(PlaybackStallAction.None, sample(monitor, nowMillis = 0L))
        assertEquals(
            PlaybackStallAction.None,
            sample(monitor, nowMillis = 20_000L, playWhenReady = false, isPlaying = false)
        )
        assertEquals(PlaybackStallAction.None, sample(monitor, nowMillis = 34_999L))
        assertEquals(PlaybackStallAction.Recover, sample(monitor, nowMillis = 35_000L))
    }

    @Test
    fun readyButPlaybackSuppressedDoesNotTriggerRecovery() {
        val monitor = PlaybackStallMonitor()

        assertEquals(PlaybackStallAction.None, sample(monitor, nowMillis = 0L))
        assertEquals(
            PlaybackStallAction.None,
            sample(monitor, nowMillis = 20_000L, isPlaying = false)
        )
    }

    @Test
    fun switchingMediaStartsANewObservationWindow() {
        val monitor = PlaybackStallMonitor()

        assertEquals(PlaybackStallAction.None, sample(monitor, nowMillis = 0L, mediaToken = 1L))
        assertEquals(PlaybackStallAction.None, sample(monitor, nowMillis = 14_000L, mediaToken = 1L))
        assertEquals(PlaybackStallAction.None, sample(monitor, nowMillis = 15_000L, mediaToken = 2L))
        assertEquals(PlaybackStallAction.None, sample(monitor, nowMillis = 29_999L, mediaToken = 2L))
        assertEquals(PlaybackStallAction.Recover, sample(monitor, nowMillis = 30_000L, mediaToken = 2L))
    }

    @Test
    fun videoStallIsDetectedWhileTheAudioPositionKeepsAdvancing() {
        val monitor = PlaybackStallMonitor()
        assertEquals(PlaybackStallAction.None,
            monitor.sample(0, 1, 0, true, true, PlaybackProgressState.Ready, videoFrames = 30))
        assertEquals(PlaybackStallAction.Recover,
            monitor.sample(15_000, 1, 15_000, true, true, PlaybackProgressState.Ready, videoFrames = 30))
    }

    @Test
    fun audioOnlyOrBackgroundPlaybackDoesNotRequireVideoFrames() {
        val monitor = PlaybackStallMonitor()
        assertEquals(PlaybackStallAction.None,
            monitor.sample(0, 1, 0, true, true, PlaybackProgressState.Ready, videoFrames = 30))
        assertEquals(PlaybackStallAction.None,
            monitor.sample(15_000, 1, 15_000, true, true, PlaybackProgressState.Ready, videoFrames = null))
    }
    @Test
    fun stallRecoveryUsesTheNextCandidateBeforeRequestingFreshUrls() {
        assertEquals(
            PlaybackSourceRecoveryAction.SwitchToNextUrl,
            playbackSourceRecoveryAction(currentUrlIndex = 0, urlCount = 2)
        )
        assertEquals(
            PlaybackSourceRecoveryAction.RequestFreshSource,
            playbackSourceRecoveryAction(currentUrlIndex = 1, urlCount = 2)
        )
    }
}
