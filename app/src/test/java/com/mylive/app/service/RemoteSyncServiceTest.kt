package com.mylive.app.service

import kotlinx.coroutines.runBlocking
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteSyncServiceTest {

    private fun enqueueCreateRoomWs(server: MockWebServer, roomId: String) {
        server.enqueue(
            MockResponse()
                .withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val request = JSONObject(text)
                        if (request.optString("type") == "createRoom") {
                            webSocket.send(
                                JSONObject().apply {
                                    put("type", "roomCreated")
                                    put("requestId", request.optString("requestId"))
                                    put("roomId", roomId)
                                }.toString()
                            )
                            webSocket.close(1000, "test complete")
                        }
                    }
                })
        )
    }

    @Test
    fun probeChecksTheDefaultBusinessRouteWithPingOnly() = runBlocking {
        val server = MockWebServer()
        val messageTypes = CopyOnWriteArrayList<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/sync/create") return MockResponse().setResponseCode(404)
                return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val request = JSONObject(text)
                        messageTypes += request.optString("type")
                        if (request.optString("type") == "ping") {
                            webSocket.send(
                                JSONObject().apply {
                                    put("type", "pong")
                                    put("requestId", request.optString("requestId"))
                                }.toString()
                            )
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                })
            }
        }
        server.start()
        val service = RemoteSyncService()

        try {
            val baseUrl = server.url("/").toString().trimEnd('/').replace("http://", "ws://")
            service.defaultSyncServerUrl = baseUrl
            val result = service.probeServer(
                configuredUrl = "",
                configuredProxyUrl = RemoteSyncService.K_DIRECT_PROXY_VALUE,
                timeoutMillis = 2_000L
            )

            assertTrue(result.isReachable)
            assertTrue((result.latencyMillis ?: -1L) >= 0L)
            assertEquals(RemoteSyncConnectionState.DISCONNECTED, service.connectionState.value)
            assertEquals("/sync/create", server.takeRequest(2, TimeUnit.SECONDS)?.path)
            assertEquals(listOf("ping"), messageTypes.toList())
            assertEquals("", service.currentRoomId)
        } finally {
            service.disconnect()
            server.shutdown()
        }
    }

    @Test
    fun probeAndRoomCreationUseTheSameCustomBasePath() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val request = JSONObject(text)
                webSocket.send(JSONObject().put("type", "pong").put("requestId", request.optString("requestId")).toString())
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
        }))
        enqueueCreateRoomWs(server, "BASE01")
        server.start()
        val service = RemoteSyncService()
        try {
            val baseUrl = "  " + server.url("/relay/").toString().replace("http://", "ws://") + "  "
            assertTrue(service.probeServer(baseUrl, RemoteSyncService.K_DIRECT_PROXY_VALUE, 2_000L).isReachable)
            assertTrue(service.createRoom(baseUrl, RemoteSyncService.K_DIRECT_PROXY_VALUE, JSONObject()).isSuccess)
            assertEquals("/relay/sync/create", server.takeRequest(2, TimeUnit.SECONDS)?.path)
            assertEquals("/relay/sync/create", server.takeRequest(2, TimeUnit.SECONDS)?.path)
        } finally {
            service.disconnect()
            server.shutdown()
        }
    }

    @Test
    fun healthProbeKeepsAnExistingRoomConnectedAndUsable() = runBlocking {
        val server = MockWebServer()
        val messages = CopyOnWriteArrayList<String>()
        fun listener() = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val request = JSONObject(text)
                val type = request.optString("type")
                messages += type
                val reply = JSONObject().put("requestId", request.optString("requestId"))
                when (type) {
                    "createRoom" -> reply.put("type", "roomCreated").put("roomId", "KEEP01")
                    "ping" -> reply.put("type", "pong")
                    "sendFavorite" -> reply.put("type", "sendFavoriteResp").put("status", true)
                }
                webSocket.send(reply.toString())
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(listener()))
        server.enqueue(MockResponse().withWebSocketUpgrade(listener()))
        server.start()
        val service = RemoteSyncService()
        try {
            val url = server.url("/").toString().replace("http://", "ws://")
            assertTrue(service.createRoom(url, RemoteSyncService.K_DIRECT_PROXY_VALUE, JSONObject()).isSuccess)
            assertTrue(service.probeServer(url, RemoteSyncService.K_DIRECT_PROXY_VALUE, 2_000L).isReachable)
            assertEquals(RemoteSyncConnectionState.CONNECTED, service.connectionState.value)
            assertEquals("KEEP01", service.currentRoomId)
            assertTrue(service.sendContent("SendFavorite", false, "{}").isSuccess)
            assertEquals(listOf("createRoom", "ping", "sendFavorite"), messages.toList())
        } finally {
            service.disconnect()
            server.shutdown()
        }
    }

    @Test
    fun probeTreatsAWebSocketWithoutProtocolPongAsUnavailable() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }
            })
        )
        server.start()
        val service = RemoteSyncService()

        try {
            val baseUrl = server.url("/").toString().trimEnd('/').replace("http://", "ws://")
            val result = service.probeServer(
                configuredUrl = baseUrl,
                configuredProxyUrl = RemoteSyncService.K_DIRECT_PROXY_VALUE,
                timeoutMillis = 200L
            )

            assertFalse(result.isReachable)
            assertEquals(null, result.latencyMillis)
            assertTrue(result.message.contains("超时"))
            assertEquals(RemoteSyncConnectionState.DISCONNECTED, service.connectionState.value)
        } finally {
            service.disconnect()
            server.shutdown()
        }
    }

    @Test
    fun createRoomConnectsAndReturnsRoomId() = runBlocking {
        val server = MockWebServer()
        enqueueCreateRoomWs(server, "ABC123")
        server.start()

        val service = RemoteSyncService()
        try {
            val baseUrl = server.url("/").toString().trimEnd('/').replace("http://", "ws://")
            val resp = service.createRoom(baseUrl, RemoteSyncService.K_DIRECT_PROXY_VALUE, JSONObject())

            assertTrue(resp.message, resp.isSuccess)
            assertEquals("ABC123", service.currentRoomId)
        } finally {
            service.disconnect()
            server.shutdown()
        }
    }

    @Test
    fun createRoomWithDelayWaitsForOpen() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeadersDelay(300, TimeUnit.MILLISECONDS)
                .withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val request = JSONObject(text)
                        if (request.optString("type") == "createRoom") {
                            webSocket.send(
                                JSONObject().apply {
                                    put("type", "roomCreated")
                                    put("requestId", request.optString("requestId"))
                                    put("roomId", "DELAY1")
                                }.toString()
                            )
                            webSocket.close(1000, "test complete")
                        }
                    }
                })
        )
        server.start()

        val service = RemoteSyncService()
        try {
            val baseUrl = server.url("/").toString().trimEnd('/').replace("http://", "ws://")
            val resp = service.createRoom(baseUrl, RemoteSyncService.K_DIRECT_PROXY_VALUE, JSONObject())

            assertTrue(resp.message, resp.isSuccess)
            assertEquals("DELAY1", service.currentRoomId)
        } finally {
            service.disconnect()
            server.shutdown()
        }
    }

    @Test
    fun createRoomWithDebugFallback() = runBlocking {
        val server = MockWebServer()
        enqueueCreateRoomWs(server, "DBG001")
        server.start()

        val service = RemoteSyncService()
        service.debugBuild = true
        service.debugSyncServerUrls = listOf(server.url("/").toString().trimEnd('/').replace("http://", "ws://"))
        service.defaultSyncServerUrl = "ws://127.0.0.1:1/sync"
        try {
            val resp = service.createRoom("", RemoteSyncService.K_DIRECT_PROXY_VALUE, JSONObject())

            assertTrue(resp.message, resp.isSuccess)
            assertEquals("DBG001", service.currentRoomId)
        } finally {
            service.disconnect()
            server.shutdown()
        }
    }

    @Test
    fun sendContentTreatsStatusFalseResponseAsFailure() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val request = JSONObject(text)
                        when (request.optString("type")) {
                            "createRoom" -> {
                                webSocket.send(
                                    JSONObject().apply {
                                        put("type", "roomCreated")
                                        put("requestId", request.optString("requestId"))
                                        put("roomId", "FAIL01")
                                    }.toString()
                                )
                            }
                            "sendFavorite" -> {
                                webSocket.send(
                                    JSONObject().apply {
                                        put("type", "sendFavoriteResp")
                                        put("requestId", request.optString("requestId"))
                                        put("status", false)
                                        put("message", "remote rejected")
                                    }.toString()
                                )
                                webSocket.close(1000, "test complete")
                            }
                        }
                    }
                })
        )
        server.start()

        val service = RemoteSyncService()
        try {
            val baseUrl = server.url("/").toString().trimEnd('/').replace("http://", "ws://")
            val createResp = service.createRoom(baseUrl, RemoteSyncService.K_DIRECT_PROXY_VALUE, JSONObject())
            assertTrue(createResp.message, createResp.isSuccess)

            val resp = service.sendContent("SendFavorite", overlay = false, content = "{}")

            assertFalse(resp.isSuccess)
            assertEquals("remote rejected", resp.message)
        } finally {
            service.disconnect()
            server.shutdown()
        }
    }

    @Test
    fun sendContentRejectsOversizedPayloadBeforeConnecting() = runBlocking {
        val service = RemoteSyncService()
        val content = "x".repeat(RemoteSyncService.K_MAX_SYNC_CONTENT_BYTES + 1)

        val response = service.sendContent("SendFavorite", overlay = false, content = content)

        assertFalse(response.isSuccess)
        assertEquals("同步内容不能超过 5 MB", response.message)
    }

    @Test
    fun connectionErrorsDoNotReferenceRetiredWorkersDomain() {
        val source = File("../live-core/src/main/java/com/mylive/app/service/RemoteSyncService.kt").readText()

        assertFalse(source.contains("workers.dev"))
    }

    @Test
    fun encryptedRoomSealsCookiesOnWireAndRejectsPlaintextOrWrongKeyPayloads() = runBlocking {
        val server = MockWebServer()
        val wireFrames = CopyOnWriteArrayList<String>()
        val sockets = CopyOnWriteArrayList<WebSocket>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        sockets += webSocket
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        wireFrames += text
                        val msg = JSONObject(text)
                        val reqId = msg.optString("requestId")
                        when (val type = msg.optString("type")) {
                            "createRoom" -> {
                                webSocket.send(
                                    JSONObject()
                                        .put("type", "roomCreated")
                                        .put("requestId", reqId)
                                        .put("roomId", "ENC123")
                                        .toString()
                                )
                            }
                            "joinRoom" -> {
                                webSocket.send(
                                    JSONObject()
                                        .put("type", "roomJoined")
                                        .put("requestId", reqId)
                                        .toString()
                                )
                            }
                            "sendBiliAccount" -> {
                                val forwarded = JSONObject()
                                    .put("type", "biliAccountReceived")
                                    .put("payload", msg.optJSONObject("payload") ?: JSONObject())
                                    .toString()
                                sockets.filter { it !== webSocket }.forEach { it.send(forwarded) }
                                webSocket.send(JSONObject().put("type", "ack").put("requestId", reqId).toString())
                            }
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                })
            }
        }
        server.start()

        val creator = RemoteSyncService()
        val joiner = RemoteSyncService()
        val receivedCookies = CopyOnWriteArrayList<String>()
        val syncErrors = CopyOnWriteArrayList<String>()
        joiner.onBiliAccountReceived = { _, content -> receivedCookies += content }
        joiner.onSyncError = { err -> syncErrors += err }

        try {
            val baseUrl = server.url("/").toString().trimEnd('/').replace("http://", "ws://")

            // Joining with only a bare 6-char room ID must fail before connecting.
            val bareJoin = joiner.joinRoom(baseUrl, RemoteSyncService.K_DIRECT_PROXY_VALUE, "ENC123", JSONObject())
            assertFalse(bareJoin.isSuccess)

            val createResp = creator.createRoom(baseUrl, RemoteSyncService.K_DIRECT_PROXY_VALUE, JSONObject())
            assertTrue(createResp.message, createResp.isSuccess)
            assertTrue(creator.currentInviteCode.startsWith("ENC123-"))

            val joinResp = joiner.joinRoom(
                baseUrl,
                RemoteSyncService.K_DIRECT_PROXY_VALUE,
                creator.currentQrPayload,
                JSONObject()
            )
            assertTrue(joinResp.message, joinResp.isSuccess)

            val secretJson = """{"cookie":"SESSDATA=ultra_secret_cookie_token"}"""
            val sendResp = creator.sendContent("SendBiliAccount", overlay = false, content = secretJson)
            assertTrue(sendResp.message, sendResp.isSuccess)

            // Wait briefly for async frame delivery
            val deadline = System.currentTimeMillis() + 3_000L
            while (receivedCookies.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertEquals(listOf(secretJson), receivedCookies.toList())
            assertTrue(wireFrames.none { it.contains("ultra_secret_cookie_token") || it.contains("SESSDATA") })

            // Injecting an unencrypted payload from relay must be rejected and reported via onSyncError
            sockets.last().send(
                JSONObject()
                    .put("type", "biliAccountReceived")
                    .put("payload", JSONObject().put("overlay", false).put("content", """{"cookie":"injected"}"""))
                    .toString()
            )
            val errDeadline = System.currentTimeMillis() + 3_000L
            while (syncErrors.isEmpty() && System.currentTimeMillis() < errDeadline) {
                Thread.sleep(20)
            }
            assertEquals(1, receivedCookies.size)
            assertTrue(syncErrors.isNotEmpty())
        } finally {
            creator.disconnect()
            joiner.disconnect()
            server.shutdown()
        }
    }
}
