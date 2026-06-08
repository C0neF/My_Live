package com.mylive.app.core.common

import kotlinx.coroutines.runBlocking
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

class HttpClientTest {

    @Test
    fun getTextClosesErrorResponseBody() {
        val body = CloseTrackingBody()
        val client = OkHttpClient.Builder()
            .addInterceptor(errorResponseInterceptor(body))
            .build()

        assertThrows(CoreError::class.java) {
            runBlocking {
                HttpClient(client).getText("https://example.test/error")
            }
        }

        assertTrue(body.isClosed)
    }

    private fun errorResponseInterceptor(body: ResponseBody): Interceptor {
        return Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("server error")
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
