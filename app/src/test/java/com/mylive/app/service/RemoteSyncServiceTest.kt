package com.mylive.app.service

import kotlinx.coroutines.runBlocking
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
        val source = File("src/main/java/com/mylive/app/service/RemoteSyncService.kt").readText()

        assertFalse(source.contains("workers.dev"))
    }
}
