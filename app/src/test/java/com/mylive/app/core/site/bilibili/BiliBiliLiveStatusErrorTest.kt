package com.mylive.app.core.site.bilibili

import com.mylive.app.core.common.CoreError
import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.site.LiveSiteAccountStore
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.repository.FollowRepository
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.service.FollowStatusRefreshCoordinator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BiliBiliLiveStatusErrorTest {
    @Test
    fun missingOrMalformedApplicationCodeCannotConfirmAnOfflineStatus() {
        for (fields in listOf("", "\"code\":null,", "\"code\":\"invalid\",",
            "\"code\":true,", "\"code\":0.5,", "\"code\":[],")) {
            Fixture("""{$fields"data":{"live_status":0}}""").use { fixture ->
                val error = assertThrows(CoreError::class.java) {
                    runBlocking { fixture.site.getLiveStatus(ROOM_ID) }
                }

                assertTrue(error.message.contains("直播状态"))
                assertEquals(listOf(STATUS_PATH), fixture.paths.toList())
            }
        }
    }

    @Test
    fun applicationFailureIsReportedEvenWhenItsPayloadLooksOffline() {
        for (body in listOf(
            """{"code":-412,"message":"request blocked","data":null}""",
            """{"code":-412,"message":"request blocked","data":{"live_status":0}}"""
        )) {
            Fixture(body).use { fixture ->
                val error = assertThrows(CoreError::class.java) {
                    runBlocking { fixture.site.getLiveStatus(ROOM_ID) }
                }

                assertTrue(error.message.contains("-412"))
                assertEquals(listOf(STATUS_PATH), fixture.paths.toList())
            }
        }
    }

    @Test
    fun missingOrMalformedRoomDataIsReportedAsUnknownInsteadOfOffline() {
        for (body in listOf(
            """{"code":0}""",
            """{"code":0,"data":null}""",
            """{"code":0,"data":[]}""",
            """{"code":0,"data":"invalid"}"""
        )) {
            Fixture(body).use { fixture ->
                val error = assertThrows(CoreError::class.java) {
                    runBlocking { fixture.site.getLiveStatus(ROOM_ID) }
                }

                assertTrue(error.message.contains("直播状态"))
                assertEquals(listOf(STATUS_PATH), fixture.paths.toList())
            }
        }
    }

    @Test
    fun missingOrInvalidLiveStatusIsReportedAsUnknownInsteadOfOffline() {
        for (data in listOf(
            "{}",
            """{"live_status":null}""",
            """{"live_status":""}""",
            """{"live_status":"unknown"}""",
            """{"live_status":true}""",
            """{"live_status":1.5}""",
            """{"live_status":-1}""",
            """{"live_status":3}"""
        )) {
            Fixture("""{"code":0,"data":$data}""").use { fixture ->
                val error = assertThrows(CoreError::class.java) {
                    runBlocking { fixture.site.getLiveStatus(ROOM_ID) }
                }

                assertTrue(error.message.contains("直播状态"))
                assertEquals(listOf(STATUS_PATH), fixture.paths.toList())
            }
        }
    }

    @Test
    fun explicitLiveOfflineAndLoopingStatusesRemainSupported() = runBlocking {
        for ((status, expectedLive) in listOf("1" to true, "0" to false, "2" to false, "\"1\"" to true)) {
            Fixture("""{"code":0,"data":{"live_status":$status}}""").use { fixture ->
                assertEquals(expectedLive, fixture.site.getLiveStatus(ROOM_ID))
                assertEquals(listOf(STATUS_PATH), fixture.paths.toList())
            }
        }
    }

    @Test
    fun blockedStatusRefreshKeepsTheKnownLiveFollowAndRecordsFailure() = runBlocking {
        Fixture("""{"code":-412,"message":"request blocked","data":null}""").use { fixture ->
            val original = FollowUserEntity(
                id = "bilibili_$ROOM_ID",
                roomId = ROOM_ID,
                siteId = "bilibili",
                userName = "Test anchor",
                face = "",
                addTime = 0L,
                liveStatus = 1,
                liveStartTime = 100L,
                showTime = "2026-10-19 00:00:00"
            )
            var persisted = original
            val repository = mockk<FollowRepository>()
            every { repository.getAllFollows() } returns flowOf(listOf(original))
            coEvery { repository.updateLiveStatuses(any()) } coAnswers {
                firstArg<List<FollowUserEntity>>().forEach { persisted = it }
            }
            val settings = mockk<SettingsRepository>()
            every { settings.updateFollowThreadCount } returns flowOf(1)
            val coordinator = FollowStatusRefreshCoordinator(repository, settings, setOf(fixture.site))

            val transitions = coordinator.refreshAll()

            assertTrue(transitions.isEmpty())
            assertEquals(original, persisted)
            val entry = coordinator.entries.value.getValue(original.id)
            assertTrue(entry.failed)
            assertFalse(entry.refreshing)
            assertNull(entry.lastSuccessAtMillis)
            assertEquals(1, coordinator.progress.value.failed)
            assertEquals(listOf(STATUS_PATH), fixture.paths.toList())
        }
    }

    private class Fixture(statusResponse: String) : AutoCloseable {
        val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            paths += request.url.encodedPath
            val expected = request.url.encodedPath == STATUS_PATH
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (expected) 200 else 500).message("")
                .body((if (expected) statusResponse else "Unexpected request in isolated test").toResponseBody())
                .build()
        }.build()
        val site = BiliBiliSite(HttpClient(client), client,
            LiveSiteAccountStore { "buvid3=test-b3;buvid4=test-b4;" })

        override fun close() {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private companion object {
        const val ROOM_ID = "123456"
        const val STATUS_PATH = "/room/v1/Room/get_info"
    }
}
