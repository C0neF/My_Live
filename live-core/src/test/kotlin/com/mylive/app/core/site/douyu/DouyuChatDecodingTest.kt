package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.model.LiveMessage
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DouyuChatDecodingTest {
    @Test fun escapedChatTextRemainsTextAfterBinaryDecoding() {
        val client = OkHttpClient()
        try {
            val danmaku = DouyuDanmaku(WebSocketUtils(client))
            val received = mutableListOf<LiveMessage>()
            danmaku.onMessage = { received += it }
            for (text in listOf(
                "https://example.test/a//b?user=a@b",
                "hello",
                "a@b",
                "key@=value / another@=literal",
                "literal @S and @A"
            )) {
                received.clear()
                decode(danmaku, "type@=chatmsg/nn@=${escape("Name / @")}/txt@=${escape(text)}/dms@=1/")

                assertEquals(1, received.size)
                assertEquals("Name / @", received.single().userName)
                assertEquals(text, received.single().message)
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test fun serverBlockedMessagesAreStillIgnored() {
        val client = OkHttpClient()
        try {
            val danmaku = DouyuDanmaku(WebSocketUtils(client))
            val received = mutableListOf<LiveMessage>()
            danmaku.onMessage = { received += it }
            decode(danmaku, "type@=chatmsg/nn@=Name/txt@=blocked/")

            assertEquals(emptyList<LiveMessage>(), received)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test fun concatenatedBinaryFramesEmitAllChatMessages() {
        val client = OkHttpClient()
        try {
            val danmaku = DouyuDanmaku(WebSocketUtils(client))
            val received = mutableListOf<LiveMessage>()
            danmaku.onMessage = { received += it }
            val frame1 = buildBinaryFrame("type@=chatmsg/nn@=UserA/txt@=first/dms@=1/")
            val frame2 = buildBinaryFrame("type@=chatmsg/nn@=UserB/txt@=second/dms@=1/")
            DouyuDanmaku::class.java.getDeclaredMethod("decodeMessage", ByteArray::class.java)
                .apply { isAccessible = true }.invoke(danmaku, frame1 + frame2)

            assertEquals(listOf("UserA", "UserB"), received.map { it.userName })
            assertEquals(listOf("first", "second"), received.map { it.message })
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun escape(value: String) = value.replace("@", "@A").replace("/", "@S")

    private fun buildBinaryFrame(text: String): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(body.size + 13).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(body.size + 9).putInt(body.size + 9).putShort(690)
            .put(0).put(0).put(body).put(0).array()
    }

    private fun decode(danmaku: DouyuDanmaku, text: String) {
        DouyuDanmaku::class.java.getDeclaredMethod("decodeMessage", ByteArray::class.java)
            .apply { isAccessible = true }.invoke(danmaku, buildBinaryFrame(text))
    }
}
