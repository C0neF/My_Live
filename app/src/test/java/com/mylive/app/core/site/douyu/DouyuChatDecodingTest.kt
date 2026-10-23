package com.mylive.app.core.site.douyu

import android.app.Application
import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.model.LiveMessage
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
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

    private fun escape(value: String) = value.replace("@", "@A").replace("/", "@S")

    private fun decode(danmaku: DouyuDanmaku, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        val frame = ByteBuffer.allocate(body.size + 13).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(body.size + 9).putInt(body.size + 9).putShort(690)
            .put(0).put(0).put(body).put(0).array()
        DouyuDanmaku::class.java.getDeclaredMethod("decodeMessage", ByteArray::class.java)
            .apply { isAccessible = true }.invoke(danmaku, frame)
    }
}
