package com.mylive.app.core.site.bilibili

import com.mylive.app.core.common.HttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BiliBiliRecommendPagingPolicyTest {

    @Test
    fun recommendUsesUnrestrictedRoomListEndpointForDeepAnonymousPaging() = runBlocking {
        val requestedUrls = mutableListOf<HttpUrl>()
        val site = bilibiliSiteWithResponses(requestedUrls)

        val result = site.getRecommendRooms(page = 100)

        assertEquals(30, result.items.size)
        assertTrue(result.hasMore)
        assertFalse(requestedUrls.any { it.encodedPath.contains("/second/getListByArea") })

        val roomListRequest = requestedUrls.firstOrNull {
            it.encodedPath == "/room/v1/Area/getRoomList"
        } ?: error("Expected Bilibili recommend rooms to use the unrestricted room list endpoint")
        assertEquals("100", roomListRequest.queryParameter("page"))
        assertEquals("30", roomListRequest.queryParameter("page_size"))
        assertEquals(null, roomListRequest.queryParameter("parent_area_id"))
        assertEquals(null, roomListRequest.queryParameter("area_id"))
    }

    private fun bilibiliSiteWithResponses(requestedUrls: MutableList<HttpUrl>): BiliBiliSite {
        val interceptor = Interceptor { chain ->
            val url = chain.request().url
            requestedUrls += url
            val json = when (url.encodedPath) {
                "/x/frontend/finger/spi" -> """
                    {
                      "code": 0,
                      "data": {
                        "b_3": "test-buvid3",
                        "b_4": "test-buvid4"
                      }
                    }
                """.trimIndent()
                "/x/web-interface/nav" -> """
                    {
                      "code": 0,
                      "data": {
                        "wbi_img": {
                          "img_url": "https://i0.hdslb.com/bfs/wbi/abcdefghijklmnopqrstuvwxyz123456.png",
                          "sub_url": "https://i0.hdslb.com/bfs/wbi/123456abcdefghijklmnopqrstuvwxyz.png"
                        }
                      }
                    }
                """.trimIndent()
                "/xlive/web-interface/v1/second/getListByArea" -> secondListByAreaJson()
                "/room/v1/Area/getRoomList" -> roomListJson()
                else -> error("Unexpected Bilibili request: $url")
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
        return BiliBiliSite(
            httpClient = HttpClient(okHttpClient),
            okHttpClient = okHttpClient
        )
    }

    private fun secondListByAreaJson(): String {
        return """
            {
              "code": 0,
              "data": {
                "list": ${roomItemsJson()}
              }
            }
        """.trimIndent()
    }

    private fun roomListJson(): String {
        return """
            {
              "code": 0,
              "data": ${roomItemsJson()}
            }
        """.trimIndent()
    }

    private fun roomItemsJson(): String {
        return (1..30).joinToString(prefix = "[", postfix = "]") { index ->
            """
                {
                  "roomid": "$index",
                  "title": "Live room $index",
                  "cover": "https://i0.hdslb.com/live-cover-$index.jpg",
                  "uname": "Anchor $index",
                  "face": "https://i0.hdslb.com/face-$index.jpg",
                  "online": "100$index"
                }
            """.trimIndent()
        }
    }
}
