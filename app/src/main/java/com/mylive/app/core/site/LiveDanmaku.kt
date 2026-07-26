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
 */
class LiveDanmakuSession internal constructor(
    private val danmaku: LiveDanmaku,
    private val args: DanmakuArgs
) {
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
        danmaku.start(args)
    }

    suspend fun stop() {
        danmaku.stop()
    }
}
