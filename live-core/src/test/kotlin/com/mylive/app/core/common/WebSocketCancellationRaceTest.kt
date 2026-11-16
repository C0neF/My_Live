package com.mylive.app.core.common

import io.mockk.*
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Test
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
}
