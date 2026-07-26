package com.mylive.app.core.site

import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LiveMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The handshake behind a danmaku session is resolved when the connection starts, not when the
 * session is built.
 *
 * Bilibili's `getDanmuInfo` used to be fetched inside `getRoomDetail`, which meant a round trip
 * that only danmaku needs sat between the room detail and the play URLs — in front of the first
 * video frame. Creating a session must therefore stay free of I/O.
 */
class LiveDanmakuSessionLazyArgsTest {

    @Test
    fun buildingASessionResolvesNothing() {
        val resolved = AtomicInteger(0)
        val danmaku = RecordingDanmaku()

        LiveDanmakuSession(danmaku) {
            resolved.incrementAndGet()
            DanmakuArgs.Douyu(roomId = "1")
        }

        assertEquals(0, resolved.get())
        assertNull(danmaku.startedWith)
    }

    @Test
    fun startingTheConnectionResolvesTheHandshakeAndHandsItOver() = runBlocking {
        val args = DanmakuArgs.Douyu(roomId = "42")
        val danmaku = RecordingDanmaku()
        val session = LiveDanmakuSession(danmaku) { args }

        session.start()

        assertSame(args, danmaku.startedWith)
    }

    @Test
    fun aSessionThatNeverStartsNeverPaysForItsHandshake() = runBlocking {
        // Route changed before the danmaku connection was needed: the room must not have spent a
        // request on a handshake nobody used.
        val resolved = AtomicInteger(0)
        val session = LiveDanmakuSession(RecordingDanmaku()) {
            resolved.incrementAndGet()
            DanmakuArgs.Douyu(roomId = "1")
        }

        session.stop()

        assertEquals(0, resolved.get())
    }

    @Test
    fun eagerArgumentsStillWorkForPlatformsThatAlreadyHaveThem() = runBlocking {
        // Huya, Douyin and Douyu get their handshake with the room detail, so they pass a value
        // rather than a fetcher.
        val args = DanmakuArgs.Huya(ayyuid = 7L, topSid = 1, subSid = 2)
        val danmaku = RecordingDanmaku()

        LiveDanmakuSession(danmaku, args).start()

        assertSame(args, danmaku.startedWith)
    }

    @Test
    fun aFailingHandshakeSurfacesToTheCallerRatherThanStartingBlind() = runBlocking {
        val danmaku = RecordingDanmaku()
        val session = LiveDanmakuSession(danmaku) { error("getDanmuInfo failed") }

        val thrown = runCatching { session.start() }.exceptionOrNull()

        assertEquals("getDanmuInfo failed", thrown?.message)
        assertNull("connection must not start without a handshake", danmaku.startedWith)
    }

    private class RecordingDanmaku : LiveDanmaku {
        override var onMessage: ((LiveMessage) -> Unit)? = null
        override var onClose: ((String) -> Unit)? = null
        override var onReady: (() -> Unit)? = null
        override var heartbeatTime: Int = 1
        var startedWith: DanmakuArgs? = null

        override fun heartbeat() = Unit
        override suspend fun start(args: DanmakuArgs) {
            startedWith = args
        }

        override suspend fun stop() = Unit
    }
}
