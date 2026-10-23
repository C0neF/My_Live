package com.mylive.app.core.site.huya

import android.app.Application
import com.mylive.app.core.common.HttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HuyaRoomIdentityTest {
    @Test fun aliasRoomKeepsPlaybackQualitiesAfterResolvingCanonicalId() = runBlocking {
        withSite { site ->
            val detail = site.getRoomDetail("kpl")

            assertEquals("123456", detail.roomId)
            assertTrue(detail.status)
            assertEquals(listOf("original"), site.getPlayQualites(detail).map { it.quality })
        }
    }

    @Test fun numericRoomKeepsPlaybackQualities() = runBlocking {
        withSite { site ->
            val detail = site.getRoomDetail("123456")

            assertEquals("123456", detail.roomId)
            assertEquals(listOf("original"), site.getPlayQualites(detail).map { it.quality })
        }
    }

    private suspend fun withSite(block: suspend (HuyaSite) -> Unit) {
        val streamInfo = JSONObject("""{
            "vStreamInfo":{"value":[{
                "sFlvUrl":"https://cdn.example.test",
                "sStreamName":"test-stream",
                "sCdnType":"TEST"
            }]},
            "vBitRateInfo":{"value":[{"sDisplayName":"original","iBitRate":0}]}
        }""")
        val liveInfo = JSONObject()
            .put("tLiveStreamInfo", streamInfo)
            .put("lProfileRoom", "123456")
            .put("sIntroduction", "Test broadcast")
            .put("lYyid", 998877)
            .put("lChannelId", 998877)
            .put("lSubChannelId", 112233)
        val payload = JSONObject().put(
            "roomInfo", JSONObject()
                .put("tLiveInfo", liveInfo)
                .put("tProfileInfo", JSONObject().put("sNick", "Test streamer"))
                .put("eLiveStatus", 2)
        )
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("<script>window.HNF_GLOBAL_INIT = $payload </script>".toResponseBody())
                .build()
        }.build()
        try {
            block(HuyaSite(HttpClient(client), client))
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
