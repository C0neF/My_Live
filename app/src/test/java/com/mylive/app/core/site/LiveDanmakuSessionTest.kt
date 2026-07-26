package com.mylive.app.core.site

import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDanmakuSessionTest {

    @Test
    fun sessionHidesPlatformArgumentsAndOwnsConnectionLifetime() = runBlocking {
        val danmaku = RecordingDanmaku()
        val args = DanmakuArgs.Douyu(roomId = "room-42")
        val session = LiveDanmakuSession(danmaku, args)

        var received: LiveMessage? = null
        var closeReason: String? = null
        var ready = false
        session.onMessage = { received = it }
        session.onClose = { closeReason = it }
        session.onReady = { ready = true }

        val message = LiveMessage(
            type = LiveMessageType.CHAT,
            userName = "viewer",
            message = "hello"
        )
        danmaku.onMessage?.invoke(message)
        danmaku.onClose?.invoke("closed")
        danmaku.onReady?.invoke()
        session.start()
        session.stop()

        assertSame(message, received)
        assertEquals("closed", closeReason)
        assertTrue(ready)
        assertSame(args, danmaku.startedWith)
        assertTrue(danmaku.stopped)
    }

    private class RecordingDanmaku : LiveDanmaku {
        override var onMessage: ((LiveMessage) -> Unit)? = null
        override var onClose: ((String) -> Unit)? = null
        override var onReady: (() -> Unit)? = null
        override var heartbeatTime: Int = 1

        var startedWith: DanmakuArgs? = null
        var stopped = false

        override fun heartbeat() = Unit

        override suspend fun start(args: DanmakuArgs) {
            startedWith = args
        }

        override suspend fun stop() {
            stopped = true
        }
    }
}
