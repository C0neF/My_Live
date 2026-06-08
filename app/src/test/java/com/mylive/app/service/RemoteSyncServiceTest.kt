package com.mylive.app.service

import kotlinx.coroutines.runBlocking
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
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
}
