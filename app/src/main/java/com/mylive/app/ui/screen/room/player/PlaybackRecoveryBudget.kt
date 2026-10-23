package com.mylive.app.ui.screen.room.player

internal const val MAX_PLAYBACK_RECOVERY_ATTEMPTS = 3

internal enum class PlaybackSourceRecoveryAction { SwitchToNextUrl, RequestFreshSource }

internal fun playbackSourceRecoveryAction(currentUrlIndex: Int, urlCount: Int): PlaybackSourceRecoveryAction =
    if (currentUrlIndex in 0 until (urlCount - 1)) PlaybackSourceRecoveryAction.SwitchToNextUrl
    else PlaybackSourceRecoveryAction.RequestFreshSource

/** One budget for all automatic retries in a logical viewing session, across media/decoder swaps. */
internal class PlaybackRecoveryBudget(
    private val maxAttempts: Int = MAX_PLAYBACK_RECOVERY_ATTEMPTS,
    private val stableResetMillis: Long = 30_000L
) {
    var attempts: Int = 0
        private set
    private var sourceToken: Long? = null
    private var lastPosition: Long? = null
    private var lastVideoFrames: Long? = null
    private var stableSince: Long? = null

    fun resetForUserSelection() {
        attempts = 0
        sourceToken = null
        lastPosition = null
        lastVideoFrames = null
        stableSince = null
    }

    fun tryAcquire(): Boolean {
        if (attempts >= maxAttempts) return false
        attempts++
        stableSince = null
        return true
    }

    fun observe(nowMillis: Long, mediaToken: Long, positionMillis: Long,
        playing: Boolean, videoFrames: Long? = null) {
        val sameSource = sourceToken == mediaToken
        val progressed = sameSource && playing && lastPosition?.let { positionMillis != it } == true &&
            (videoFrames == null || lastVideoFrames?.let { videoFrames > it } == true)
        if (progressed) {
            val since = stableSince
            if (since == null) stableSince = nowMillis
            else if (nowMillis - since >= stableResetMillis) attempts = 0
        } else {
            stableSince = null
        }
        sourceToken = mediaToken
        lastPosition = positionMillis
        lastVideoFrames = videoFrames
    }
}
