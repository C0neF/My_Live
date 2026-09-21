package com.mylive.app.ui.screen.room.player

internal enum class PlaybackProgressState { Idle, Ready, Buffering, Ended }
internal enum class PlaybackStallAction { None, Recover }
internal enum class PlaybackStallRecoveryAction { SwitchToNextUrl, RequestFreshSource }

internal fun playbackStallRecoveryAction(currentUrlIndex: Int, urlCount: Int): PlaybackStallRecoveryAction =
    if (currentUrlIndex in 0 until (urlCount - 1)) PlaybackStallRecoveryAction.SwitchToNextUrl
    else PlaybackStallRecoveryAction.RequestFreshSource

/** Detects stalled clocks. Retry ownership deliberately lives in PlaybackRecoveryBudget. */
internal class PlaybackStallMonitor(
    private val readyTimeoutMillis: Long = 15_000L,
    private val bufferingTimeoutMillis: Long = 30_000L
) {
    private var observedMediaToken: Long? = null
    private var lastPositionMillis: Long? = null
    private var lastProgressAtMillis = 0L
    private var lastVideoFrames: Long? = null
    private var lastVideoProgressAtMillis = 0L

    fun sample(nowMillis: Long, mediaToken: Long, positionMillis: Long,
        playWhenReady: Boolean, isPlaying: Boolean, state: PlaybackProgressState,
        videoFrames: Long? = null): PlaybackStallAction {
        if (observedMediaToken != mediaToken) {
            observedMediaToken = mediaToken
            resetProgress(nowMillis, positionMillis, videoFrames)
            return PlaybackStallAction.None
        }
        val observing = playWhenReady && when (state) {
            PlaybackProgressState.Ready -> isPlaying
            PlaybackProgressState.Buffering -> true
            else -> false
        }
        if (!observing) {
            resetProgress(nowMillis, positionMillis, videoFrames)
            return PlaybackStallAction.None
        }
        if (lastPositionMillis != positionMillis) lastProgressAtMillis = nowMillis
        lastPositionMillis = positionMillis
        if (videoFrames == null || lastVideoFrames == null || videoFrames != lastVideoFrames) {
            lastVideoProgressAtMillis = nowMillis
        }
        lastVideoFrames = videoFrames
        val stalledAt = if (state == PlaybackProgressState.Ready && videoFrames != null)
            minOf(lastProgressAtMillis, lastVideoProgressAtMillis) else lastProgressAtMillis
        val timeout = if (state == PlaybackProgressState.Buffering) bufferingTimeoutMillis else readyTimeoutMillis
        if (nowMillis - stalledAt < timeout) return PlaybackStallAction.None
        resetProgress(nowMillis, positionMillis, videoFrames)
        return PlaybackStallAction.Recover
    }

    fun reset() {
        observedMediaToken = null
        lastPositionMillis = null
        lastVideoFrames = null
        lastProgressAtMillis = 0L
        lastVideoProgressAtMillis = 0L
    }

    private fun resetProgress(nowMillis: Long, positionMillis: Long, videoFrames: Long?) {
        lastPositionMillis = positionMillis
        lastVideoFrames = videoFrames
        lastProgressAtMillis = nowMillis
        lastVideoProgressAtMillis = nowMillis
    }
}
