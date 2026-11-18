package com.mylive.desktop

import com.mylive.app.core.common.*
import com.mylive.app.core.sync.*
import com.mylive.app.core.sync.LanSyncClient
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class DesktopSyncTest {
    @get:Rule val folder = TemporaryFolder()
    private val http = OkHttpClient()
    private val protector = object : DesktopCredentialProtector {
        override fun protect(bytes: ByteArray) = byteArrayOf(42) + bytes.reversedArray()
        override fun unprotect(bytes: ByteArray) = bytes.drop(1).toByteArray().reversedArray()
    }
    private fun sample() = DesktopLibraryState(loaded = true,
        follows = listOf(SavedRoom("douyu", "1", "Name", "Title", savedAt = 1, tags = listOf("tag"))),
        player = DesktopPlayerPreferences(volume = 25f))

    @Test fun webDavCreatesMissingCollectionAndRestoresSameProfile() = runTest {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            server.enqueue(MockResponse().setResponseCode(409)); server.enqueue(MockResponse().setResponseCode(201)); server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setBody(RoomProfileCodec.export(sample())))
            val dav = DesktopWebDav(http, folder.root.toPath().resolve("dav.bin"), protector)
            val config = DesktopWebDavConfig(server.url("/dav/MyLive/").newBuilder().host("127.0.0.1").build().toString(), "user", "secret")
            dav.backup(config, sample())
            assertEquals("PUT", requireNotNull(server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS)).method)
            val mkcol = requireNotNull(server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS)); assertEquals("MKCOL", mkcol.method); assertEquals("/dav/MyLive/", mkcol.path)
            assertEquals("PUT", requireNotNull(server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS)).method)
            val plan = dav.preview(config)
            assertEquals(25f, plan.preferences!!.player.volume)
            assertEquals(listOf("tag"), plan.follows.single().tags)
            assertEquals("Basic dXNlcjpzZWNyZXQ=", requireNotNull(server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS)).getHeader("Authorization"))
        }
    }
    @Test fun webDavRejectsRedirectsErrorsMalformedAndOversizedResponsesWithoutChangingLibrary() = runTest {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            val dav = DesktopWebDav(http, folder.root.toPath().resolve("dav.bin"), protector)
            val config = DesktopWebDavConfig(server.url("/backup.json").newBuilder().host("127.0.0.1").build().toString())
            for (response in listOf(MockResponse().setResponseCode(302).setHeader("Location", "http://other.example"),
                MockResponse().setResponseCode(401), MockResponse().setBody("{"),
                MockResponse().setHeader("Content-Length", MAX_IMPORTED_JSON_BYTES + 1))) {
                server.enqueue(response)
                try { dav.preview(config); fail("Expected failure") } catch (_: Exception) {}
                requireNotNull(server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS))
            }
            assertThrows(IllegalArgumentException::class.java) { desktopWebDavUrl("http://public.example/dav") }
            assertEquals("https://dav.jianguoyun.com/dav/MyLive/mylive_profile_backup.json", desktopWebDavUrl("https://dav.jianguoyun.com/dav").toString())
        }
    }
    @Test fun savedSyncCredentialsStayOutOfProfileAndAreRecoverableOrRemovable() = runTest {
        val path = folder.root.toPath().resolve("dav.bin")
        val dav = DesktopWebDav(http, path, protector)
        dav.save(DesktopWebDavConfig("https://example.org/dav", "user", "unique-secret"))
        assertFalse(Files.readString(path).contains("unique-secret"))
        assertEquals("unique-secret", dav.load().password)
        assertFalse(RoomProfileCodec.export(sample()).contains("unique-secret"))
        assertFalse(dav.load().toString().contains("unique-secret"))
        dav.forget(); assertFalse(Files.exists(path)); assertEquals("", dav.load().server)
    }
    @Test fun realAndroidClientImportsFullProfileAndCollectionUpdatesIntoWindows() = runTest {
        val library = DesktopLibrary(folder.root.toPath().resolve("library.json"))
        library.load()
        DesktopLanServer(library).use { server ->
            server.start("127.0.0.1", 0, discover = false)
            val peer = LanSyncClient(http).connect("127.0.0.1", server.state.value.port, server.token)
            assertEquals("windows", peer.peerInfo.platform); assertTrue(peer.peerInfo.supportsFollowBundle)
            peer.import(LanSyncImportType.PROFILE, RoomProfileCodec.export(sample()), false)
            assertEquals(sample().follows, library.state.value.follows)
            assertEquals(25f, library.state.value.player.volume)
            peer.import(LanSyncImportType.TAG, """[{"tag":"New","userIds":["douyu_1"]}]""", true)
            assertEquals(listOf("New"), library.state.value.follows.single().tags)
            peer.import(LanSyncImportType.BLOCKED_WORD, """["广告"]""", false)
            assertEquals(listOf("广告"), library.state.value.danmaku.keywords)
            peer.import(LanSyncImportType.HISTORY, """[{"siteId":"huya","roomId":"2","updateTime":10}]""", true)
            assertEquals("huya", library.state.value.history.single().siteId)
        }
    }
    @Test fun windowsSenderUsesSameEncryptedProtocolAndConfirmsDurableImport() = runTest {
        val library = DesktopLibrary(folder.root.toPath().resolve("library.json")); library.load()
        DesktopLanServer(library).use { server ->
            server.start("127.0.0.1", 0, discover = false)
            DesktopLanClient(http).send("127.0.0.1", server.state.value.port, server.token, RoomProfileCodec.export(sample()))
            library.load(); assertEquals(sample().follows, library.state.value.follows)
        }
    }
    @Test fun receiverRefusesWrongProofReplayTamperingAndInvalidProfileWithoutMutation() = runTest {
        val library = DesktopLibrary(folder.root.toPath().resolve("library.json")); library.importRooms(RoomProfileImport(sample().follows, emptyList()))
        DesktopLanServer(library).use { server ->
            server.start("127.0.0.1", 0, discover = false)
            val path = "/sync/profile"; val token = server.token
            val timestamp = System.currentTimeMillis(); val nonce = LanSyncAuth.newNonce()
            val context = LanSyncAuth.requestContext("POST", path, false, timestamp, nonce)
            val body = LanSyncCrypto.encrypt(token, RoomProfileCodec.export(sample()), context)
            val proof = LanSyncAuth.createProof(token, "POST", path, false, body, timestamp, nonce)
            val request = Request.Builder().url("http://127.0.0.1:${server.state.value.port}$path").post(body.toRequestBody()).header(LanSyncAuth.HEADER_NAME, proof.headerValue).build()
            fun status(req: Request) = http.newCall(req).execute().use { it.code }
            assertEquals(401, status(request.newBuilder().header(LanSyncAuth.HEADER_NAME, "bad").build()))
            assertEquals(200, status(request))
            assertEquals(401, status(request))
            assertEquals(401, status(request.newBuilder().post((body + "x").toRequestBody()).build()))
            val invalidNonce = LanSyncAuth.newNonce()
            val invalidContext = LanSyncAuth.requestContext("POST", path, false, timestamp, invalidNonce)
            val invalid = LanSyncCrypto.encrypt(token, """{"followUsers":[{"siteId":"unknown","roomId":"1"}]}""", invalidContext)
            val invalidProof = LanSyncAuth.createProof(token, "POST", path, false, invalid, timestamp, invalidNonce)
            assertEquals(400, status(request.newBuilder().post(invalid.toRequestBody()).header(LanSyncAuth.HEADER_NAME, invalidProof.headerValue).build()))
            assertEquals(sample().follows, library.state.value.follows)
        }
    }
    @Test fun lanTargetSupportsAndroidQrLinksAndPartialShieldImportPreservesPlaybackAndAppearance() = runTest {
        val (target, token) = parseDesktopLanTarget("mylive-sync://192.168.1.1:23234?token=hello%2Bworld", "")
        assertEquals(23234, target.port); assertEquals("hello+world", token)
        val library = DesktopLibrary(folder.root.toPath().resolve("library.json"))
        library.updatePlayer { it.copy(volume = 17f) }; library.updateAppearance { it.copy(theme = 1) }; library.updateDanmaku { it.copy(fontSize = 30f) }
        library.importRooms(RoomProfileCodec.parse("""{"danmuShield":[{"value":"keyword:bad"}]}"""))
        assertEquals(17f, library.state.value.player.volume); assertEquals(1, library.state.value.appearance.theme)
        assertEquals(30f, library.state.value.danmaku.fontSize); assertEquals(listOf("bad"), library.state.value.danmaku.keywords)
    }
}
