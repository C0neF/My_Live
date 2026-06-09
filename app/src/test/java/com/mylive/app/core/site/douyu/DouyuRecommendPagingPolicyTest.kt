package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.script.JsEngine
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Provider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DouyuRecommendPagingPolicyTest {

    @Test
    fun recommendKeepsPagingWhenDouyuOmitsPageCountButReturnsRooms() = runBlocking {
        val site = douyuSiteWithJsonResponse(
            """
                {
                  "data": {
                    "pgcnt": 0,
                    "rl": [
                      {
                        "type": 1,
                        "rid": "1001",
                        "rn": "Live room",
                        "nn": "Anchor",
                        "ol": 1234,
                        "rs16": "//rpic.douyucdn.cn/live-cover.jpg"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )

        val result = site.getRecommendRooms(page = 1)

        assertTrue(result.items.isNotEmpty())
        assertTrue(result.hasMore)
    }

    private fun douyuSiteWithJsonResponse(json: String): DouyuSite {
        val interceptor = Interceptor { chain ->
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
        return DouyuSite(
            httpClient = HttpClient(okHttpClient),
            jsEngineProvider = Provider<JsEngine> { error("not used") },
            okHttpClient = okHttpClient
        )
    }
}
