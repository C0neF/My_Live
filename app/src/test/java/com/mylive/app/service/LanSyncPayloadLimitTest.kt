package com.mylive.app.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.core.common.LanSyncCrypto
import com.mylive.app.core.common.MAX_IMPORTED_JSON_BYTES
import com.mylive.app.core.common.readUtf8TextWithinLimit
import com.mylive.app.core.sync.LanSyncImportType
import com.mylive.app.core.sync.LanSyncProtocolException
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.HistoryEntity
import com.mylive.app.data.repository.ProfileBackupManager
import com.mylive.app.data.sync.LanSyncClient
import com.mylive.app.testing.ReviewRoomFixture
import fi.iki.elonen.NanoHTTPD
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real Room/profile restore, LAN sender, encryption, and NanoHTTPD request handling. The transport
 * only adapts OkHttp requests to NanoHTTPD sessions; the production handler decides every response
 * and runs its actual background import jobs. No listening socket or Android service is started.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanSyncPayloadLimitTest : ReviewRoomFixture() {
    private lateinit var store: SettingsDataStore
    private lateinit var manager: ProfileBackupManager
    private lateinit var service: LanSyncService
    private lateinit var receiver: NanoHTTPD

    @Before
    fun setUpReceiver() = runBlocking {
        store = SettingsDataStore(ApplicationProvider.getApplicationContext<Context>())
        store.clearAll()
        store.setValue(SettingsDataStore.DanmuEnable, true)
        manager = ProfileBackupManager(store, follows, history, shields, database)
        service = LanSyncService().apply {
            profileBackupManager = manager
            followRepository = follows
            historyRepository = history
            shieldRepository = shields
        }
        LanSyncService.syncToken = PAIRING_TOKEN
        LanSyncService.syncDeviceId = "payload-limit-test"
        LanSyncService.ipAddress = "127.0.0.1"
        val serverClass = LanSyncService::class.java.declaredClasses.single { it.simpleName == "HttpServer" }
        val constructor = serverClass.getDeclaredConstructor(
            LanSyncService::class.java, String::class.java, Integer.TYPE
        ).apply { isAccessible = true }
        receiver = constructor.newInstance(service, "127.0.0.1", 23234) as NanoHTTPD
    }

    @After
    fun tearDownReceiver() = runBlocking {
        service.onDestroy()
        store.clearAll()
    }

    @Test
    fun aRestorableFourMiBUtf8ProfileAlsoImportsThroughTheEncryptedLanTransport() = runBlocking {
        // Each real database row stays below 4 KiB; the legal aggregate crosses the old wire cap
        // only after sealing. The row count remains well below the LAN history item limit.
        val originals = List(1_800) { index ->
            HistoryEntity(
                id = "huya_$index", roomId = index.toString(), siteId = "huya",
                userName = "主播$index-${"界".repeat(760)}",
                face = "https://example.test/avatar/$index", updateTime = 100L + index
            )
        }
        history.addHistories(originals)
        assertEquals(originals.size, history.getAllHistory().first().size)
        val exported = manager.exportProfileJson()
        val plaintextBytes = exported.toByteArray(Charsets.UTF_8).size
        assertTrue("Fixture must exceed 4 MiB", plaintextBytes > 4 * 1024 * 1024)
        assertTrue("Fixture must be a legal backup", plaintextBytes <= MAX_IMPORTED_JSON_BYTES)

        history.clearAllHistory()
        val readableBackup = exported.byteInputStream(Charsets.UTF_8).use { it.readUtf8TextWithinLimit() }
        manager.importProfileJson(readableBackup)
        assertEquals(originals.sortedBy { it.id }, history.getAllHistory().first().sortedBy { it.id })

        history.clearAllHistory()
        store.setValue(SettingsDataStore.DanmuEnable, false)
        val delivery = sendToReceiver(exported)

        assertTrue("The real encrypted envelope crosses 5 MiB", delivery.wireBytes > MAX_IMPORTED_JSON_BYTES)
        assertTrue(
            "The bounded file reader restored $plaintextBytes bytes, but the same LAN profile failed: " +
                "${delivery.outcome.exceptionOrNull()}; POST responses=${delivery.postResponses}",
            delivery.outcome.isSuccess
        )
        assertEquals(listOf(202), delivery.postResponses)
        assertEquals(originals.sortedBy { it.id }, history.getAllHistory().first().sortedBy { it.id })
        assertTrue(store.getFlow(SettingsDataStore.DanmuEnable, false).first())
    }

    @Test
    fun aPlaintextProfileBeyondFiveMiBIsStillRefusedWithoutChangingStoredData() = runBlocking {
        val original = HistoryEntity("huya_local", "local", "huya", "keep-local", "", 123L)
        history.addHistory(original)
        store.setValue(SettingsDataStore.UserRemarks, "keep-local-remarks")
        val tooLarge = JSONObject().put("settings", JSONObject()
            .put(SettingsDataStore.DanmuEnable.name, false)
            .put(SettingsDataStore.UserRemarks.name, "x".repeat(MAX_IMPORTED_JSON_BYTES)))
            .toString()
        assertTrue(tooLarge.toByteArray(Charsets.UTF_8).size > MAX_IMPORTED_JSON_BYTES)
        assertTrue(runCatching {
            tooLarge.byteInputStream(Charsets.UTF_8).use { it.readUtf8TextWithinLimit() }
        }.exceptionOrNull() is IllegalArgumentException)

        val delivery = sendToReceiver(tooLarge)

        assertTrue(delivery.outcome.exceptionOrNull() is LanSyncProtocolException)
        assertEquals(listOf(413), delivery.postResponses)
        assertEquals(listOf(original), history.getAllHistory().first())
        assertTrue(store.getFlow(SettingsDataStore.DanmuEnable, false).first())
        assertEquals("keep-local-remarks", store.getFlow(SettingsDataStore.UserRemarks, "").first())
    }

    @Test
    fun anExactlyFiveMiBUtf8ProfileImportsAtTheEnvelopeBoundary() = runBlocking {
        val remarks = JSONObject().put(
            "huya::anchor", "界".repeat((MAX_IMPORTED_JSON_BYTES - 512) / 3)
        ).toString()
        val json = JSONObject().put("settings", JSONObject()
            .put(SettingsDataStore.DanmuEnable.name, false)
            .put(SettingsDataStore.UserRemarks.name, remarks)).toString()
        // Trailing JSON whitespace fills the final sub-KiB gap without changing the profile.
        val paddingBytes = MAX_IMPORTED_JSON_BYTES - json.toByteArray(Charsets.UTF_8).size
        assertTrue(paddingBytes >= 0)
        val payload = json + " ".repeat(paddingBytes)
        assertEquals(MAX_IMPORTED_JSON_BYTES, payload.toByteArray(Charsets.UTF_8).size)
        assertTrue(payload.length < MAX_IMPORTED_JSON_BYTES)

        val delivery = sendToReceiver(payload)

        assertTrue("The maximum legal UTF-8 profile must import: ${delivery.outcome.exceptionOrNull()}",
            delivery.outcome.isSuccess)
        assertEquals(LanSyncCrypto.maxEnvelopeBytes(MAX_IMPORTED_JSON_BYTES), delivery.wireBytes)
        assertEquals(listOf(202), delivery.postResponses)
        assertFalse(store.getFlow(SettingsDataStore.DanmuEnable, true).first())
        assertEquals(remarks, store.getFlow(SettingsDataStore.UserRemarks, "").first())
    }

    @Test
    fun anOversizedWireUploadIsRefusedBeforeNanoHttpdBuffersItsBody() = runBlocking {
        // Eight MiB is larger than any sealed legal five-MiB plaintext. An unauthenticated peer
        // advertising this upload must be stopped before parseBody can allocate or read it.
        val request = Request.Builder().url("http://127.0.0.1:23234/sync/profile").build()
        val session = sessionFor(request, body = "", declaredLength = 8L * 1024 * 1024)
        every { session.method } returns NanoHTTPD.Method.POST

        val response = receiver.serve(session)
        try {
            assertEquals(413, response.status.requestStatus)
            verify(exactly = 0) { session.parseBody(any()) }
            assertTrue(history.getAllHistory().first().isEmpty())
            assertTrue(store.getFlow(SettingsDataStore.DanmuEnable, false).first())
        } finally {
            response.close()
        }
    }

    private suspend fun sendToReceiver(payload: String): Delivery {
        val wireBytes = AtomicLong(-1)
        val postResponses = CopyOnWriteArrayList<Int>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = request.body?.let { Buffer().also(it::writeTo).readUtf8() }.orEmpty()
            if (request.method == "POST") wireBytes.set(request.body!!.contentLength())
            val response = receiver.serve(sessionFor(request, body, request.body?.contentLength()))
            try {
                val status = response.status.requestStatus
                if (request.method == "POST") postResponses += status
                val responseBody = response.data?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(status).message(response.status.description)
                    .body(responseBody.toResponseBody("application/json".toMediaType())).build()
            } finally {
                response.close()
            }
        }.build()
        return try {
            val outcome = runCatching {
                val peer = LanSyncClient(client).connect("127.0.0.1", 23234, PAIRING_TOKEN)
                peer.import(LanSyncImportType.PROFILE, payload, overlay = false)
            }
            Delivery(outcome, wireBytes.get(), postResponses.toList())
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun sessionFor(request: Request, body: String, declaredLength: Long?): NanoHTTPD.IHTTPSession {
        val headers = request.headers.names().associate { name ->
            name.lowercase() to request.header(name).orEmpty()
        }.toMutableMap()
        if (declaredLength != null) headers["content-length"] = declaredLength.toString()
        val parameters = request.url.queryParameterNames.associateWith { name ->
            request.url.queryParameterValues(name).filterNotNull()
        }
        return mockk<NanoHTTPD.IHTTPSession> {
            every { method } returns NanoHTTPD.Method.valueOf(request.method)
            every { uri } returns request.url.encodedPath
            every { this@mockk.headers } returns headers
            every { this@mockk.parameters } returns parameters
            every { parseBody(any()) } answers {
                firstArg<MutableMap<String, String>>()["postData"] = body
            }
        }
    }

    private data class Delivery(
        val outcome: Result<Unit>,
        val wireBytes: Long,
        val postResponses: List<Int>
    )

    private companion object {
        const val PAIRING_TOKEN = "4f1c35034a8e46c399e10e520eb3d9cf"
    }
}
