package com.mylive.app.core.common

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebSocketUtilsTest {

    @Test
    fun connectFailureInvokesOnClose() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()

        try {
            val closeLatch = CountDownLatch(1)
            val socket = WebSocketUtils(OkHttpClient())
            val wsUrl = server.url("/socket").toString().replace("http://", "ws://")

            socket.connect(
                url = wsUrl,
                onClose = { closeLatch.countDown() }
            )

            assertTrue(closeLatch.await(2, TimeUnit.SECONDS))
        } finally {
            server.shutdown()
        }
    }
}
