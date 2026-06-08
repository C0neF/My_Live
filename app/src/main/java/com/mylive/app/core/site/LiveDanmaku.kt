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
