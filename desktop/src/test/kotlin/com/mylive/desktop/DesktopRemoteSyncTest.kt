package com.mylive.desktop

import com.mylive.app.core.common.RemoteSyncCrypto
import com.mylive.app.service.RemoteSyncService
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class DesktopRemoteSyncTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun desktopCreatesRoomSendsEncryptedPayloadsRejectsPlaintextAndReceivesSealedData() = runBlocking {
        MockWebServer().use { server ->
            val socket = AtomicReference<WebSocket>()
            val requests = CopyOnWriteArrayList<JSONObject>()
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) { socket.set(ws) }
                override fun onMessage(ws: WebSocket, text: String) {
                    val request = JSONObject(text)
                    requests += request
                    ws.send(
                        JSONObject()
                            .put("requestId", request.optString("requestId"))
                            .put("roomId", "ABC123")
                            .put("status", true)
                            .toString()
                    )
                }
                override fun onClosing(ws: WebSocket, code: Int, reason: String) { ws.close(code, reason) }
            }))
            val library = DesktopLibrary(temporary.root.toPath().resolve("profile.json"))
            library.load()
            val service = RemoteSyncService()
            DesktopRemoteSync(library, service).use { controller ->
                val endpoint = server.url("/").newBuilder().host("127.0.0.1").build().toString().replace("http://", "ws://")
                controller.connect(endpoint, null)
                withTimeout(10_000) { while (controller.state.value.roomId.isEmpty()) delay(20) }
                assertTrue(controller.state.value.inviteCode.startsWith("ABC123-"))
                assertTrue(controller.state.value.qrPayload.startsWith("mylive-room://ABC123?key="))

                controller.send(library.state.value)
                withTimeout(10_000) { while (controller.state.value.busy || requests.size < 4) delay(20) }
                assertEquals(listOf("createRoom", "sendFavorite", "sendHistory", "sendShieldWord"), requests.take(4).map { it.getString("type") })

                // Outgoing payloads must be sealed envelopes, never plaintext JSON.
                val favPayload = requests[1].getJSONObject("payload")
                assertTrue(RemoteSyncCrypto.isEncryptedEnvelope(favPayload.optString("envelope")))
                assertFalse(favPayload.has("content"))

                // Unencrypted payload from relay must be rejected.
                socket.get().send(
                    JSONObject()
                        .put("type", "favoriteReceived")
                        .put("payload", JSONObject().put("overlay", false).put("content", """{"follows":[{"siteId":"douyu","roomId":"88"}],"tags":[]}"""))
                        .toString()
                )
                delay(150)
                assertTrue(library.state.value.follows.isEmpty())

                // Properly sealed payload with matching room key imports cleanly.
                val sealedIncoming = RemoteSyncCrypto.encryptPayload(
                    roomId = "ABC123",
                    roomKey = service.currentRoomKey,
                    channel = "favoriteReceived",
                    overlay = false,
                    content = """{"follows":[{"siteId":"douyu","roomId":"9"}],"tags":[]}"""
                )
                socket.get().send(
                    JSONObject()
                        .put("type", "favoriteReceived")
                        .put("payload", sealedIncoming)
                        .toString()
                )
                withTimeout(10_000) { while (library.state.value.follows.isEmpty()) delay(20) }
                assertEquals("9", library.state.value.follows.single().roomId)

                controller.disconnect()
                assertEquals("", controller.state.value.roomId)
                assertEquals("", controller.state.value.inviteCode)
            }
        }
    }

    @Test fun desktopRejectsBareRoomIdWithoutPairingKey() = runBlocking {
        val library = DesktopLibrary(temporary.root.toPath().resolve("profile-bare.json"))
        library.load()
        DesktopRemoteSync(library, RemoteSyncService()).use { controller ->
            controller.connect("ws://127.0.0.1:51999", "ABC123")
            withTimeout(5_000) { while (controller.state.value.busy) delay(20) }
            assertEquals("", controller.state.value.roomId)
            assertNotNull(controller.state.value.message)
        }
    }

    @Test fun remoteEndpointRejectsInsecureInternetAndEmbeddedCredentials() {
        assertThrows(IllegalArgumentException::class.java) { validateDesktopRemoteUrl("ws://example.com") }
        assertThrows(IllegalArgumentException::class.java) { validateDesktopRemoteUrl("wss://user:secret@example.com") }
        validateDesktopRemoteUrl("wss://example.com")
    }

    @Test fun desktopPromptsBeforeSavingReceivedAccountCookie() = runBlocking {
        MockWebServer().use { server ->
            val socket = AtomicReference<WebSocket>()
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) { socket.set(ws) }
                override fun onMessage(ws: WebSocket, text: String) {
                    val request = JSONObject(text)
                    ws.send(
                        JSONObject()
                            .put("requestId", request.optString("requestId"))
                            .put("roomId", "ACC123")
                            .put("status", true)
                            .toString()
                    )
                }
                override fun onClosing(ws: WebSocket, code: Int, reason: String) { ws.close(code, reason) }
            }))
            val dir = temporary.root.toPath().resolve("acc-sync")
            val library = DesktopLibrary(dir.resolve("profile.json"))
            library.load()
            val accounts = DesktopAccounts(dir.resolve("credentials"), object : DesktopCredentialProtector {
                override fun protect(bytes: ByteArray) = bytes.copyOf()
                override fun unprotect(bytes: ByteArray) = bytes.copyOf()
            })
            val service = RemoteSyncService()
            DesktopRemoteSync(library, service, accounts).use { controller ->
                val endpoint = server.url("/").newBuilder().host("127.0.0.1").build().toString().replace("http://", "ws://")
                controller.connect(endpoint, null)
                withTimeout(10_000) { while (controller.state.value.roomId.isEmpty()) delay(20) }

                val sealedAccount = RemoteSyncCrypto.encryptPayload(
                    roomId = "ACC123",
                    roomKey = service.currentRoomKey,
                    channel = "biliAccountReceived",
                    overlay = true,
                    content = """{"cookie":"SESSDATA=synced-token"}"""
                )
                socket.get().send(
                    JSONObject()
                        .put("type", "biliAccountReceived")
                        .put("payload", sealedAccount)
                        .toString()
                )
                withTimeout(10_000) { while (controller.state.value.pendingPrompt == null) delay(20) }
                // Must NOT overwrite until user explicitly confirms.
                assertEquals("", accounts.cookieFor(com.mylive.app.core.site.LiveSiteAccount.BILIBILI))

                controller.confirmPending()
                withTimeout(10_000) {
                    while (accounts.cookieFor(com.mylive.app.core.site.LiveSiteAccount.BILIBILI).isEmpty()) delay(20)
                }
                assertEquals("SESSDATA=synced-token", accounts.cookieFor(com.mylive.app.core.site.LiveSiteAccount.BILIBILI))
                assertNull(controller.state.value.pendingPrompt)
            }
        }
    }
}
