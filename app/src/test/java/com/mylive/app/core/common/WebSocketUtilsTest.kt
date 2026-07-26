package com.mylive.app.core.common

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebSocketUtilsTest {

    private companion object {
        /**
         * How long to wait for an event that the test requires to happen.
         *
         * Generous on purpose. These latches only bound how long a *healthy* run waits — the
         * assertion still fails if the event never arrives — so a tight budget buys nothing and
         * costs a flake whenever the machine is busy (this suite failed under a concurrent
         * lint + assemble run at two seconds).
         */
        const val EVENT_TIMEOUT_SECONDS = 15L

        /**
         * How long to watch for an event that must NOT happen. This one is a real trade-off
         * (longer = slower suite, shorter = weaker assertion), so it stays short and is only
         * used where the negative is checked after the positive events already landed.
         */
        const val ABSENCE_WINDOW_MILLIS = 300L
    }

    @Test
    fun canDisableCompressionExtensionForIncompatibleServers() {
        val server = MockWebServer()
        val openLatch = CountDownLatch(1)
        val closeLatch = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    openLatch.countDown()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closeLatch.countDown()
                }
            })
        )
        server.start()

        try {
            val socket = WebSocketUtils(OkHttpClient())
            val wsUrl = server.url("/socket").toString().replace("http://", "ws://")

            socket.connect(
                url = wsUrl,
                disableCompression = true
            )

            assertTrue(openLatch.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertNull(server.takeRequest(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)?.getHeader("Sec-WebSocket-Extensions"))
            socket.close()
            assertTrue(closeLatch.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun heartbeatUsesFixedDelayAndCrossThreadLifecycleStateIsVisible() {
        val source = File("src/main/java/com/mylive/app/core/common/WebSocketUtils.kt")
            .readText()
            .replace("\r\n", "\n")

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

            assertTrue(closeLatch.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
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

            assertTrue(firstOpen.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue(readyLatch.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue(reconnectLatch.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue(secondOpen.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
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

            assertTrue(firstOpen.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue(reconnectLatch.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue(secondOpen.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue(secondReady.await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertFalse(closeLatch.await(ABSENCE_WINDOW_MILLIS, TimeUnit.MILLISECONDS))
        } finally {
            server.shutdown()
        }
    }
}
