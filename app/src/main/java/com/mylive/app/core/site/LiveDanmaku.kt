package com.mylive.app.core.site

import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LiveMessage

interface LiveDanmaku {
    var onMessage: ((LiveMessage) -> Unit)?
    var onClose: ((String) -> Unit)?
    var onReady: (() -> Unit)?
    var heartbeatTime: Int

    fun heartbeat()

    suspend fun start(args: DanmakuArgs)

    suspend fun stop()
}

/**
 * A configured danmaku connection. Platform-specific arguments stay inside the
 * site adapter; room consumers only manage callbacks and connection lifetime.
 *
 * The arguments are resolved by [resolveArgs] when the connection actually starts, not when the
 * session is created. That matters for platforms whose handshake costs a request of its own:
 * Bilibili's `getDanmuInfo` used to be fetched inside `getRoomDetail`, which put a round trip that
 * only danmaku needs directly in front of the first video frame. Deferring it takes that hop off
 * the playback path and lets the handshake run alongside it instead.
 */
class LiveDanmakuSession internal constructor(
    private val danmaku: LiveDanmaku,
    private val resolveArgs: suspend () -> DanmakuArgs
) {
    /** For platforms whose handshake data already came back with the room detail. */
    internal constructor(danmaku: LiveDanmaku, args: DanmakuArgs) : this(danmaku, { args })

    var onMessage: ((LiveMessage) -> Unit)?
        get() = danmaku.onMessage
        set(value) {
            danmaku.onMessage = value
        }

    var onClose: ((String) -> Unit)?
        get() = danmaku.onClose
        set(value) {
            danmaku.onClose = value
        }

    var onReady: (() -> Unit)?
        get() = danmaku.onReady
        set(value) {
            danmaku.onReady = value
        }

    suspend fun start() {
        danmaku.start(resolveArgs())
    }

    suspend fun stop() {
        danmaku.stop()
    }
}
