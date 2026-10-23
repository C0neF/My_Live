package com.mylive.app.ui.screen.room.player

/** All timestamps use elapsedRealtime; a source generation rejects late frames from old media. */
internal class PlaybackStartupTiming {
    private var roomStartedAtMillis: Long? = null
    private var sourceStartedAtMillis: Long? = null
    private var sourceToken: Long? = null
    var roomFirstFrameMillis: Long? = null
        private set
    var sourceFirstFrameMillis: Long? = null
        private set

    fun beginRoomLoad(nowMillis: Long) {
        roomStartedAtMillis = nowMillis
        roomFirstFrameMillis = null
        invalidateSource()
    }

    fun beginSource(nowMillis: Long, token: Long) {
        sourceStartedAtMillis = nowMillis
        sourceToken = token
        sourceFirstFrameMillis = null
    }

    fun onRenderedFirstFrame(renderedAtMillis: Long, token: Long): Boolean {
        val startedAt = sourceStartedAtMillis ?: return false
        if (sourceToken != token || sourceFirstFrameMillis != null || renderedAtMillis < startedAt) return false
        sourceFirstFrameMillis = renderedAtMillis - startedAt
        if (roomFirstFrameMillis == null) roomStartedAtMillis?.let {
            roomFirstFrameMillis = (renderedAtMillis - it).coerceAtLeast(0)
        }
        return true
    }

    fun invalidateSource() {
        sourceStartedAtMillis = null
        sourceToken = null
        sourceFirstFrameMillis = null
    }
}
