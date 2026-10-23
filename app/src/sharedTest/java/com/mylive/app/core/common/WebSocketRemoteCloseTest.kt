package com.mylive.app.core.common

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class WebSocketRemoteCloseTest {
    @Test
    fun remoteNormalCloseReceivesTheClientCloseAcknowledgement() {
        SocketFixture().use { fixture ->
            val peer = fixture.enqueuePeer()
            val ready = CountDownLatch(1)
            fixture.socket.connect(url = fixture.url, onReady = { ready.countDown() })
            assertTrue("Client must finish opening before the peer closes", ready.awaitEvent())
            assertTrue("Server must finish opening before it closes", peer.opened.awaitEvent())

            assertTrue(peer.webSocket.get()!!.close(1000, "server restart"))

            assertTrue("Peer must receive the client's Close acknowledgement", peer.closing.awaitEvent())
            assertTrue("Peer must release the completed close handshake", peer.closed.awaitEvent())
        }
    }

    @Test
    fun remoteNormalCloseStopsReportingAnActiveConnection() {
        SocketFixture().use { fixture ->
            val peer = fixture.enqueuePeer()
            val ready = CountDownLatch(1)
            fixture.socket.connect(url = fixture.url, onReady = { ready.countDown() })
            assertTrue(ready.awaitEvent())
            assertTrue(peer.opened.awaitEvent())
            assertEquals(SocketStatus.CONNECTED, fixture.socket.status)

            assertTrue(peer.webSocket.get()!!.close(1000, "server restart"))

            assertTrue(
                "A connection closed by the peer must leave CONNECTED before a new handshake",
                awaitCondition { fixture.socket.status != SocketStatus.CONNECTED }
            )
        }
    }

    @Test
    fun remoteNormalCloseReconnectsAndDeliversMessagesFromTheNewConnection() {
        SocketFixture().use { fixture ->
            val firstPeer = fixture.enqueuePeer()
            val nextPeer = fixture.enqueuePeer()
            val firstReady = CountDownLatch(1)
            val nextReady = CountDownLatch(1)
            val delivered = CountDownLatch(1)
            val readyCount = AtomicInteger(0)
            fixture.socket.connect(
                url = fixture.url,
                onReady = {
                    when (readyCount.incrementAndGet()) {
                        1 -> firstReady.countDown()
                        2 -> nextReady.countDown()
                    }
                },
                onMessage = { if (it == "message after reconnect") delivered.countDown() }
            )
            assertTrue(firstReady.awaitEvent())
            assertTrue(firstPeer.opened.awaitEvent())

            assertTrue(firstPeer.webSocket.get()!!.close(1000, "server restart"))

            // This is the production five-second retry, with no injected timer or private call.
            assertTrue("Client must reconnect after the peer closes normally", nextReady.awaitEvent())
            assertTrue(nextPeer.opened.awaitEvent())
            assertTrue(nextPeer.webSocket.get()!!.send("message after reconnect"))
            assertTrue("The replacement connection must deliver messages", delivered.awaitEvent())
            assertEquals(2, readyCount.get())
            assertEquals(SocketStatus.CONNECTED, fixture.socket.status)
        }
    }

    @Test
    fun anIntentionalClientCloseCompletesWithoutReconnecting() {
        SocketFixture().use { fixture ->
            val peer = fixture.enqueuePeer()
            val unexpectedPeer = fixture.enqueuePeer()
            val ready = CountDownLatch(1)
            val reconnectCount = AtomicInteger(0)
            fixture.socket.connect(
                url = fixture.url,
                onReady = { ready.countDown() },
                onReconnect = { reconnectCount.incrementAndGet() }
            )
            assertTrue(ready.awaitEvent())
            assertTrue(peer.opened.awaitEvent())

            fixture.socket.close()

            assertTrue(peer.closed.awaitEvent())
            assertEquals(SocketStatus.CLOSED, fixture.socket.status)
            assertFalse(
                "An intentional close must stay closed across the retry interval",
                unexpectedPeer.opened.await(NO_RECONNECT_SECONDS, TimeUnit.SECONDS)
            )
            assertEquals(0, reconnectCount.get())
            assertEquals(SocketStatus.CLOSED, fixture.socket.status)
        }
    }

    @Test
    fun aDelayedCloseCallbackFromThePreviousConnectionCannotCloseItsReplacement() {
        SocketFixture().use { fixture ->
            // Hold the server's acknowledgement so the first client's onClosed arrives only
            // after the second connection is already active on the same WebSocketUtils instance.
            val previousPeer = fixture.enqueuePeer(acknowledgeClientClose = false)
            val currentPeer = fixture.enqueuePeer()
            val unexpectedPeer = fixture.enqueuePeer()
            val previousReady = CountDownLatch(1)
            fixture.socket.connect(url = fixture.url, onReady = { previousReady.countDown() })
            assertTrue(previousReady.awaitEvent())
            assertTrue(previousPeer.opened.awaitEvent())
            fixture.socket.close()
            assertTrue(previousPeer.closing.awaitEvent())

            val currentReady = CountDownLatch(1)
            val delivered = CountDownLatch(1)
            val reconnectCount = AtomicInteger(0)
            val failureCount = AtomicInteger(0)
            fixture.socket.connect(
                url = fixture.url,
                onReady = { currentReady.countDown() },
                onMessage = { if (it == "current connection still works") delivered.countDown() },
                onReconnect = { reconnectCount.incrementAndGet() },
                onClose = { failureCount.incrementAndGet() }
            )
            assertTrue(currentReady.awaitEvent())
            assertTrue(currentPeer.opened.awaitEvent())

            assertTrue(previousPeer.webSocket.get()!!.close(1000, "delayed acknowledgement"))
            assertTrue(previousPeer.closed.awaitEvent())
            assertTrue(currentPeer.webSocket.get()!!.send("current connection still works"))
            assertTrue(delivered.awaitEvent())
            assertEquals(SocketStatus.CONNECTED, fixture.socket.status)
            assertFalse(
                "A stale close callback must not schedule another connection",
                unexpectedPeer.opened.await(NO_RECONNECT_SECONDS, TimeUnit.SECONDS)
            )
            assertEquals(0, reconnectCount.get())
            assertEquals(0, failureCount.get())
            assertEquals(SocketStatus.CONNECTED, fixture.socket.status)
        }
    }

    private class SocketFixture : AutoCloseable {
        private val server = MockWebServer().apply { start() }
        private val client = OkHttpClient()
        private val peers = CopyOnWriteArrayList<Peer>()
        val socket = WebSocketUtils(client)
        val url: String = server.url("/chat").toString().replace("http://", "ws://")

        fun enqueuePeer(acknowledgeClientClose: Boolean = true): Peer {
            val peer = Peer(acknowledgeClientClose)
            peers.add(peer)
            server.enqueue(MockResponse().withWebSocketUpgrade(peer.listener))
            return peer
        }

        override fun close() {
            socket.close()
            // MockWebServer's server-side WebSocket has no originating client Call, so its
            // RealWebSocket.cancel() is not a valid cleanup API. Complete its Close handshake;
            // server.shutdown() releases any transport left by a failed assertion.
            peers.forEach { it.webSocket.get()?.close(1000, "test finished") }
            client.dispatcher.cancelAll()
            try {
                server.shutdown()
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
                client.dispatcher.executorService.awaitTermination(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        }
    }

    private class Peer(private val acknowledgeClientClose: Boolean) {
        val webSocket = AtomicReference<WebSocket?>()
        val opened = CountDownLatch(1)
        val closing = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@Peer.webSocket.set(webSocket)
                opened.countDown()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                closing.countDown()
                if (acknowledgeClientClose) webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.countDown()
            }
        }
    }

    private fun CountDownLatch.awaitEvent(): Boolean = await(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private fun awaitCondition(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(EVENT_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10L)
        }
        return condition()
    }

    private companion object {
        const val EVENT_TIMEOUT_SECONDS = 15L
        // Longer than the real five-second retry interval, also used for stale-callback checks.
        const val NO_RECONNECT_SECONDS = 6L
    }
}
