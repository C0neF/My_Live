package com.mylive.app.core.common

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class RuntimeNetworkLogInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        CoreLog.clear()
        CoreLog.configure(enabled = true, debugEnabled = true)
        CoreLog.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
        CoreLog.clear()
        CoreLog.configure(enabled = false, debugEnabled = false)
    }

    @Test
    fun requestLogExcludesQueryHeadersAndBodies() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val client = OkHttpClient.Builder()
            .addInterceptor(RuntimeNetworkLogInterceptor())
            .build()
        val request = Request.Builder()
            .url(server.url("/live/play?token=secret"))
            .header("Cookie", "session=secret")
            .post("password=secret".toRequestBody())
            .build()

        client.newCall(request).execute().close()

        val message = CoreLog.entries.value.single().message
        assertTrue(message.contains("POST"))
        assertTrue(message.contains("/live/play"))
        assertTrue(message.contains("200"))
        assertFalse(message.contains("token"))
        assertFalse(message.contains("session"))
        assertFalse(message.contains("password"))
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("?"))
    }

    @Test
    fun transportFailureOmitsExceptionMessage() {
        val client = OkHttpClient.Builder()
            .addInterceptor(RuntimeNetworkLogInterceptor())
            .addInterceptor(Interceptor {
                throw IOException("token=secret")
            })
            .build()
        val request = Request.Builder()
            .url("https://example.test/live?token=secret")
            .get()
            .build()

        assertThrows(IOException::class.java) {
            client.newCall(request).execute()
        }

        val message = CoreLog.entries.value.single().message
        assertTrue(message.contains("GET"))
        assertTrue(message.contains("https://example.test/live"))
        assertTrue(message.contains("IOException"))
        assertFalse(message.contains("token"))
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("?"))
    }

    @Test
    fun networkModuleInstallsSafeRuntimeInterceptorInAllBuilds() {
        val source = File("src/main/java/com/mylive/app/di/NetworkModule.kt").readText()

        assertTrue(source.contains("RuntimeNetworkLogInterceptor()"))
        assertFalse(source.contains("HttpLoggingInterceptor"))
        assertFalse(source.contains("if (BuildConfig.DEBUG)"))
    }

    @Test
    fun networkModuleStreamsBrotliResponsesWithoutFullBuffering() {
        val source = File("src/main/java/com/mylive/app/di/NetworkModule.kt").readText()

        assertTrue(source.contains("BrotliResponseBody("))
        assertTrue(source.contains("contentLength() = -1L"))
        assertFalse(source.contains("readBytes()"))
        assertFalse(source.contains("toResponseBody(body.contentType())"))
    }
}
