package com.mylive.app.core.common

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebSocketUtilsTest {

    @Test
    fun heartbeatUsesFixedDelayAndCrossThreadLifecycleStateIsVisible() {
        val source = File("src/main/java/com/mylive/app/core/common/WebSocketUtils.kt").readText()

        assertFalse(source.contains("scheduleAtFixedRate"))
        assertTrue(source.contains("@Volatile\n    var status"))
        assertTrue(source.contains("@Volatile\n    private var webSocket"))
        assertTrue(source.contains("@Volatile\n    private var intentionalClose"))
    }

    @Test
    fun connectFailureInvokesOnClose() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()

        try {
            val closeLatch = CountDownLatch(1)
            val socket = WebSocketUtils(OkHttpClient())
            val wsUrl = server.url("/socket").toString().replace("http://", "ws://")

            socket.connect(
                url = wsUrl,
                onClose = { closeLatch.countDown() }
            )

            assertTrue(closeLatch.await(2, TimeUnit.SECONDS))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun idleWebSocketReconnectsWhenNoMessagesArrive() {
        val server = MockWebServer()
        val firstOpen = CountDownLatch(1)
        val secondOpen = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    firstOpen.countDown()
                }
            })
        )
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    secondOpen.countDown()
                }
            })
        )
        server.start()

        try {
            val readyLatch = CountDownLatch(1)
            val reconnectLatch = CountDownLatch(1)
            val socket = WebSocketUtils(OkHttpClient())
            val primaryUrl = server.url("/socket-primary").toString().replace("http://", "ws://")
            val backupUrl = server.url("/socket-backup").toString().replace("http://", "ws://")

            socket.connect(
                url = primaryUrl,
                backupUrl = backupUrl,
                heartBeatTime = 50L,
                idleTimeoutMillis = 120L,
                onReady = { readyLatch.countDown() },
                onReconnect = { reconnectLatch.countDown() },
                onHeartBeat = {}
            )

            assertTrue(firstOpen.await(2, TimeUnit.SECONDS))
            assertTrue(readyLatch.await(2, TimeUnit.SECONDS))
            assertTrue(reconnectLatch.await(2, TimeUnit.SECONDS))
            assertTrue(secondOpen.await(2, TimeUnit.SECONDS))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun idleTimeoutReconnectsWithoutReportingTerminalClose() {
        val server = MockWebServer()
        val firstOpen = CountDownLatch(1)
        val secondOpen = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    firstOpen.countDown()
                }
            })
        )
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    secondOpen.countDown()
                }
            })
        )
        server.start()

        try {
            val readyCount = AtomicInteger(0)
            val secondReady = CountDownLatch(1)
            val reconnectLatch = CountDownLatch(1)
            val closeLatch = CountDownLatch(1)
            val socket = WebSocketUtils(OkHttpClient())
            val wsUrl = server.url("/socket").toString().replace("http://", "ws://")

            socket.connect(
                url = wsUrl,
                heartBeatTime = 50L,
                idleTimeoutMillis = 120L,
                onReady = {
                    if (readyCount.incrementAndGet() == 2) {
                        secondReady.countDown()
                    }
                },
                onReconnect = { reconnectLatch.countDown() },
                onClose = { closeLatch.countDown() },
                onHeartBeat = {}
            )

            assertTrue(firstOpen.await(2, TimeUnit.SECONDS))
            assertTrue(reconnectLatch.await(2, TimeUnit.SECONDS))
            assertTrue(secondOpen.await(2, TimeUnit.SECONDS))
            assertTrue(secondReady.await(2, TimeUnit.SECONDS))
            assertFalse(closeLatch.await(200, TimeUnit.MILLISECONDS))
        } finally {
            server.shutdown()
        }
    }
}
