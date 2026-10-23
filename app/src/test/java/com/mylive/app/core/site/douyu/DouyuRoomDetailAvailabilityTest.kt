package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.site.LiveSiteAccountStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DouyuRoomDetailAvailabilityTest {
    @Test
    fun optionalStartTimeHttpFailureDoesNotBlockLiveRoomDetails() = runBlocking {
        Fixture(startTimeStatus = 503).use { fixture ->
            val detail = fixture.site.getRoomDetail(ROOM_ID)

            assertEquals(ROOM_ID, detail.roomId)
            assertEquals("Test live room", detail.title)
            assertEquals("Test anchor", detail.userName)
            assertTrue(detail.status)
            assertNull(detail.showTime)
            assertEquals(listOf(ROOM_PATH, TIME_PATH), fixture.paths.toList())
        }
    }

    @Test
    fun successfulOptionalStartTimeIsIncludedInRoomDetails() = runBlocking {
        Fixture(startTimeStatus = 200).use { fixture ->
            val detail = fixture.site.getRoomDetail(ROOM_ID)

            assertTrue(detail.status)
            assertEquals("1720000000", detail.showTime)
            assertEquals(listOf(ROOM_PATH, TIME_PATH), fixture.paths.toList())
        }
    }

    @Test
    fun cancellationWhileReadingOptionalStartTimeStillCancelsRoomEntry() {
        val http = mockk<HttpClient>()
        val cancellation = CancellationException("Room entry cancelled")
        coEvery { http.getJson("https://www.douyu.com$ROOM_PATH", any(), any()) } returns
            JSONObject(ROOM_RESPONSE)
        coEvery { http.getJson("https://www.douyu.com$TIME_PATH", any(), any()) } throws cancellation
        val client = OkHttpClient()
        try {
            val site = createSite(http, client)

            val error = assertThrows(CancellationException::class.java) {
                runBlocking { site.getRoomDetail(ROOM_ID) }
            }

            assertSame(cancellation, error)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private class Fixture(startTimeStatus: Int) : AutoCloseable {
        val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            paths += request.url.encodedPath
            val (status, body) = when (request.url.encodedPath) {
                ROOM_PATH -> 200 to ROOM_RESPONSE
                TIME_PATH -> startTimeStatus to if (startTimeStatus == 200) {
                    """{"data":{"show_time":"1720000000"}}"""
                } else {
                    "Optional start time unavailable"
                }
                else -> 500 to "Unexpected request in isolated test"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("").body(body.toResponseBody()).build()
        }.build()
        val site = createSite(HttpClient(client), client)

        override fun close() {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private companion object {
        const val ROOM_ID = "123456"
        const val ROOM_PATH = "/betard/$ROOM_ID"
        const val TIME_PATH = "/swf_api/h5room/$ROOM_ID"
        const val ROOM_RESPONSE = """{"room":{"room_id":"123456","room_name":"Test live room","owner_name":"Test anchor","show_status":1,"videoLoop":0}}"""

        fun createSite(http: HttpClient, client: OkHttpClient) = DouyuSite(
            http,
            client,
            DouyuAccountSession(LiveSiteAccountStore { "" }, http),
            DouyuPlaybackSigner(http)
        )
    }
}
