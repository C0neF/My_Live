package com.mylive.app.core.site.huya.tars

import com.mylive.app.core.site.huya.tars.model.GetCdnTokenReq
import com.mylive.app.core.site.huya.tars.model.GetCdnTokenResp
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BaseTarsHttpTest {

    @Test
    fun tupRequestClosesErrorResponseBody() {
        val body = CloseTrackingBody()
        val client = OkHttpClient.Builder()
            .addInterceptor(errorResponseInterceptor(body))
            .build()
        val tarsHttp = BaseTarsHttp(
            baseUrl = "https://example.test",
            servantName = "liveui",
            okHttpClient = client
        )

        assertThrows(IOException::class.java) {
            tarsHttp.tupRequestWithRspCode(
                methodName = "getCdnToken",
                tReq = GetCdnTokenReq(),
                tRsp = GetCdnTokenResp()
            )
        }

        assertTrue(body.isClosed)
    }

    private fun errorResponseInterceptor(body: ResponseBody): Interceptor {
        return Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("unavailable")
                .body(body)
                .build()
        }
    }

    private class CloseTrackingBody : ResponseBody() {
        var isClosed = false
            private set

        override fun contentType() = "text/plain".toMediaType()

        override fun contentLength() = 5L

        override fun source(): BufferedSource = Buffer().writeUtf8("error")

        override fun close() {
            isClosed = true
            super.close()
        }
    }
}
