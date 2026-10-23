package com.mylive.app.core.site

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.site.douyu.DouyuAccountSession
import com.mylive.app.core.site.douyu.DouyuPlaybackSigner
import com.mylive.app.core.site.douyu.DouyuSite
import com.mylive.app.core.site.huya.HuyaSite
import com.mylive.app.data.local.AppDatabase
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.local.secure.AndroidKeystoreCredentialCipher
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.service.FollowStatusRefreshCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Real platform adapter -> refresh coordinator -> in-memory Room, with no outgoing HTTP. */
@RunWith(AndroidJUnit4::class)
class PlatformFollowPresenceAndroidTest {
    private lateinit var database: AppDatabase
    private lateinit var follows: FollowRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        follows = FollowRepository(database.followUserDao(), database.followUserTagDao(), database)
        val store = SettingsDataStore(context)
        settings = SettingsRepository(store, SensitiveCredentialStore(store, AndroidKeystoreCredentialCipher()))
        // Only the concurrency preference is read. No settings, credentials or persistent DB are written.
        // Device runs use -PisolatedDeviceTests=true so even this read belongs to the audit package.
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test fun douyuMissingStatusKeepsKnownLiveFollow() = runBlocking {
        assertUnknownStatus("douyu", "{}")
    }

    @Test fun douyuMalformedStatusKeepsKnownLiveFollow() = runBlocking {
        assertUnknownStatus("douyu", """{"show_status":"unknown","videoLoop":0}""")
    }

    @Test fun huyaMissingStatusKeepsKnownLiveFollow() = runBlocking {
        assertUnknownStatus("huya", "{}")
    }

    @Test fun huyaMalformedStatusKeepsKnownLiveFollow() = runBlocking {
        assertUnknownStatus("huya", """{"eLiveStatus":"unknown"}""")
    }

    @Test fun douyuNullBooleanObjectAndFractionAreNotLiveStatusFacts() = runBlocking {
        for (value in listOf("null", "true", "{}", "[]", "1.5", "\"\"")) {
            assertUnknownStatus("douyu", """{"show_status":$value,"videoLoop":0}""")
        }
    }

    @Test fun huyaNullBooleanObjectAndFractionAreNotLiveStatusFacts() = runBlocking {
        for (value in listOf("null", "true", "{}", "[]", "2.5", "\"\"")) {
            assertUnknownStatus("huya", """{"eLiveStatus":$value}""")
        }
    }

    @Test fun douyuFallbackMissingStatusKeepsKnownLiveFollow() = runBlocking {
        assertUnknownStatus("douyu", "{}", useDouyuFallback = true)
    }

    @Test fun douyuFallbackMalformedStatusKeepsKnownLiveFollow() = runBlocking {
        assertUnknownStatus("douyu", """{"room_status":"unknown"}""", useDouyuFallback = true)
    }

    @Test fun douyuExplicitLiveRemainsLive() = runBlocking {
        assertExplicitStatus("douyu", """{"show_status":1,"videoLoop":0}""", 1)
    }

    @Test fun douyuExplicitOfflineClearsLiveTimes() = runBlocking {
        assertExplicitStatus("douyu", """{"show_status":2,"videoLoop":0}""", 2)
    }

    @Test fun douyuNumericStringStatusStillWorks() = runBlocking {
        assertExplicitStatus("douyu", """{"show_status":"1","videoLoop":"0"}""", 1)
        assertExplicitStatus("douyu", """{"show_status":"2","videoLoop":"0"}""", 2)
    }

    @Test fun douyuRecordedLoopIsOffline() = runBlocking {
        assertExplicitStatus("douyu", """{"show_status":1,"videoLoop":1}""", 2)
    }

    @Test fun douyuFallbackExplicitLiveRemainsLive() = runBlocking {
        assertExplicitStatus("douyu", """{"room_status":1}""", 1, useDouyuFallback = true)
    }

    @Test fun douyuFallbackExplicitOfflineClearsLiveTimes() = runBlocking {
        assertExplicitStatus("douyu", """{"room_status":2}""", 2, useDouyuFallback = true)
    }

    @Test fun huyaExplicitLiveRemainsLive() = runBlocking {
        assertExplicitStatus("huya", """{"eLiveStatus":2}""", 1)
    }

    @Test fun huyaExplicitOfflineClearsLiveTimes() = runBlocking {
        assertExplicitStatus("huya", """{"eLiveStatus":0}""", 2)
    }

    @Test fun huyaNumericStringStatusStillWorks() = runBlocking {
        assertExplicitStatus("huya", """{"eLiveStatus":"2"}""", 1)
        assertExplicitStatus("huya", """{"eLiveStatus":"0"}""", 2)
    }

    @Test fun douyuHttp503KeepsKnownLiveFollow() = runBlocking {
        assertTransportFailure("douyu")
    }

    @Test fun huyaHttp503KeepsKnownLiveFollow() = runBlocking {
        assertTransportFailure("huya")
    }

    private suspend fun assertUnknownStatus(siteId: String, roomData: String, useDouyuFallback: Boolean = false) {
        val result = refresh(siteId, roomData, useDouyuFallback = useDouyuFallback)
        assertEquals("Unconfirmed $siteId status $roomData must preserve the complete previous follow",
            result.original, result.persisted)
        assertTrue("Unconfirmed status must be a failed refresh", result.failed)
        assertEquals(0, result.transitions)
        assertEquals(1, result.completed)
        assertEquals(1, result.failedCount)
    }

    private suspend fun assertExplicitStatus(siteId: String, roomData: String, expected: Int,
        useDouyuFallback: Boolean = false) {
        val result = refresh(siteId, roomData, useDouyuFallback = useDouyuFallback)
        assertEquals(expected, result.persisted.liveStatus)
        assertFalse(result.failed)
        assertEquals(1, result.completed)
        assertEquals(0, result.failedCount)
        if (expected == 2) {
            assertNull(result.persisted.liveStartTime)
            assertNull(result.persisted.showTime)
            assertEquals(1, result.transitions)
        }
    }

    private suspend fun assertTransportFailure(siteId: String) {
        val result = refresh(siteId, "{}", httpStatus = 503)
        assertEquals(result.original, result.persisted)
        assertTrue(result.failed)
        assertEquals(0, result.transitions)
    }

    private suspend fun refresh(siteId: String, roomData: String, httpStatus: Int = 200,
        useDouyuFallback: Boolean = false): Observation {
        val original = FollowUserEntity(id = "${siteId}_123456", roomId = "123456", siteId = siteId,
            userName = "Test anchor", face = "", addTime = 1L, tag = "Keep group", isSpecialFollow = true,
            liveStatus = 1, liveStartTime = 100L, showTime = "1720000000")
        follows.addFollow(original)
        ResponseFixture(siteId, roomData, httpStatus, useDouyuFallback).use { fixture ->
            val coordinator = FollowStatusRefreshCoordinator(follows, settings, setOf(fixture.site))
            val transitions = coordinator.refreshAll()
            if (useDouyuFallback) assertEquals("The fallback response must actually be exercised", 1,
                fixture.fallbackRequests.get())
            return Observation(original, follows.getAllFollows().first().single(),
                coordinator.entries.value.getValue(original.id).failed, transitions.size,
                coordinator.progress.value.completed, coordinator.progress.value.failed)
        }
    }

    private data class Observation(val original: FollowUserEntity, val persisted: FollowUserEntity,
        val failed: Boolean, val transitions: Int, val completed: Int, val failedCount: Int)

    private class ResponseFixture(siteId: String, roomData: String, httpStatus: Int,
        useDouyuFallback: Boolean) : AutoCloseable {
        val fallbackRequests = AtomicInteger()
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val isFallback = request.url.host == "open.douyucdn.cn"
            if (isFallback) fallbackRequests.incrementAndGet()
            val code = if (useDouyuFallback && !isFallback && request.url.encodedPath.startsWith("/betard/"))
                503 else httpStatus
            val body = when {
                siteId == "huya" -> {
                    check(request.url.host == "m.huya.com")
                    "<script>window.HNF_GLOBAL_INIT = {\"roomInfo\":$roomData}</script>"
                }
                request.url.encodedPath.startsWith("/swf_api/h5room/") ->
                    """{"data":{"show_time":"1720000000"}}"""
                isFallback -> """{"error":0,"data":$roomData}"""
                else -> {
                    check(request.url.encodedPath.startsWith("/betard/"))
                    """{"room":$roomData}"""
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("")
                .body(body.toResponseBody()).build()
        }.build()
        private val http = HttpClient(client)
        val site: LiveSite = if (siteId == "huya") HuyaSite(http, client) else DouyuSite(http, client,
            DouyuAccountSession(LiveSiteAccountStore { "" }, http), DouyuPlaybackSigner(http))

        override fun close() {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
