package com.mylive.app.core.common

import io.mockk.*
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.IOException

class WebSocketCancellationRaceTest {
    @Test fun cancellingExpiredSocketCannotReportItsOwnFailureAsTerminal() {
        val listeners = mutableListOf<WebSocketListener>()
        val oldSocket = mockk<WebSocket>(relaxed = true)
        val nextSocket = mockk<WebSocket>(relaxed = true)
        val client = mockk<OkHttpClient>()
        every { client.newWebSocket(any(), any()) } answers {
            listeners.add(secondArg())
            if (listeners.size == 1) oldSocket else nextSocket
        }
        every { oldSocket.cancel() } answers {
            listeners.first().onFailure(oldSocket, IOException("cancelled"), null)
        }
        var terminalCloses = 0
        val socket = WebSocketUtils(client)
        try {
            socket.connect("wss://example.test/socket", heartBeatTime = 300_000L,
                idleTimeoutMillis = 1L, onClose = { terminalCloses++ })
            listeners.first().onOpen(oldSocket, mockk<Response>())
            WebSocketUtils::class.java.getDeclaredField("lastMessageAtMillis")
                .apply { isAccessible = true }.setLong(socket, 0L)
            WebSocketUtils::class.java.getDeclaredMethod("onIdleTimeout")
                .apply { isAccessible = true }.invoke(socket)
            assertEquals(0, terminalCloses)
            assertEquals(2, listeners.size)
            verify(exactly = 1) { oldSocket.cancel() }
        } finally { socket.close() }
    }

    @Test fun overlappingIdleTimeoutsOpenOnlyOneReplacement() {
        val listeners = mutableListOf<WebSocketListener>()
        val sockets = mutableListOf<WebSocket>()
        val client = mockk<OkHttpClient>()
        every { client.newWebSocket(any(), any()) } answers {
            listeners.add(secondArg())
            mockk<WebSocket>(relaxed = true).also { sockets.add(it) }
        }
        val socket = WebSocketUtils(client)
        try {
            socket.connect("wss://example.test/socket", heartBeatTime = 300_000L, idleTimeoutMillis = 1L)
            listeners.first().onOpen(sockets.first(), mockk<Response>())
            WebSocketUtils::class.java.getDeclaredField("lastMessageAtMillis")
                .apply { isAccessible = true }.setLong(socket, 0L)
            val idle = WebSocketUtils::class.java.getDeclaredMethod("onIdleTimeout")
                .apply { isAccessible = true }
            idle.invoke(socket)
            idle.invoke(socket)
            assertEquals(2, listeners.size)
        } finally { socket.close() }
    }

    @Test fun synchronousOnOpenBeforeTheSocketIsPublishedStillBecomesReady() {
        val ready = CountDownLatch(1)
        val client = mockk<OkHttpClient>()
        every { client.newWebSocket(any(), any()) } answers {
            val listener = secondArg<WebSocketListener>()
            val opened = mockk<WebSocket>(relaxed = true)
            listener.onOpen(opened, mockk(relaxed = true))
            opened
        }
        val socket = WebSocketUtils(client)
        try {
            socket.connect(
                "wss://example.test/socket",
                heartBeatTime = 60_000L,
                onReady = { ready.countDown() }
            )
            assertTrue(ready.await(1, TimeUnit.SECONDS))
        } finally { socket.close() }
    }
}
