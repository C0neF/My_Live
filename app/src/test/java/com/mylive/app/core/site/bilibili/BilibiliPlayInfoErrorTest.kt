package com.mylive.app.core.site.bilibili

import com.mylive.app.core.common.CoreError
import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.model.LiveRoomDetail
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BilibiliPlayInfoErrorTest {

    @Test
    fun nonRateLimitFailureIsNotReportedAsAnEmptyQualityList() {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } throws
            CoreError("service unavailable", statusCode = 503)
        val site = BiliBiliSite(httpClient, OkHttpClient()) { "buvid3=test-b3;buvid4=test-b4;" }.apply {
            updateAccountCookie("buvid3=test-b3;buvid4=test-b4;")
        }

        val error = assertThrows(CoreError::class.java) {
            runBlocking {
                site.getPlayQualites(
                    LiveRoomDetail(roomId = "1", title = "", cover = "", userName = "")
                )
            }
        }

        assertEquals(503, error.statusCode)
        coVerify(exactly = 1) { httpClient.getJson(any(), any(), any()) }
    }

    @Test
    fun malformedPlayInfoIsNotReportedAsNoAvailableQuality() {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returns
            org.json.JSONObject("""{"data":{}}""")
        val site = BiliBiliSite(httpClient, OkHttpClient()) { "buvid3=test-b3;buvid4=test-b4;" }.apply {
            updateAccountCookie("buvid3=test-b3;buvid4=test-b4;")
        }

        val error = assertThrows(CoreError::class.java) {
            runBlocking {
                site.getPlayQualites(
                    LiveRoomDetail(roomId = "1", title = "", cover = "", userName = "")
                )
            }
        }

        assertTrue(error.message.orEmpty().contains("播放信息响应异常"))
    }

    @Test
    fun emptyAcceptedQualityListIsReportedAsAPlaybackInfoFailure() {
        val httpClient = mockk<HttpClient>()
        coEvery { httpClient.getJson(any(), any(), any()) } returns org.json.JSONObject(
            """
            {
              "data": {
                "playurl_info": {
                  "playurl": {
                    "g_qn_desc": [],
                    "stream": [{"format": [{"codec": [{"accept_qn": []}]}]}]
                  }
                }
              }
            }
            """.trimIndent()
        )
        val site = BiliBiliSite(httpClient, OkHttpClient()) { "buvid3=test-b3;buvid4=test-b4;" }.apply {
            updateAccountCookie("buvid3=test-b3;buvid4=test-b4;")
        }

        val error = assertThrows(CoreError::class.java) {
            runBlocking {
                site.getPlayQualites(
                    LiveRoomDetail(roomId = "1", title = "", cover = "", userName = "")
                )
            }
        }

        assertTrue(error.message.orEmpty().contains("无法获取播放清晰度"))
    }
}
