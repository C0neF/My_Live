package com.mylive.app.core.common

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
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

    @Test
    fun getJsonReportsBlankSuccessfulBodyClearly() {
        val client = OkHttpClient.Builder()
            .addInterceptor(successResponseInterceptor(""))
            .build()

        val error = assertThrows(CoreError::class.java) {
            runBlocking {
                HttpClient(client).getJson("https://example.test/empty")
            }
        }

        assertEquals("接口返回为空，请稍后再试", error.message)
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

    private fun successResponseInterceptor(body: String): Interceptor {
        return Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("ok")
                .body(body.toResponseBody("application/json".toMediaType()))
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
