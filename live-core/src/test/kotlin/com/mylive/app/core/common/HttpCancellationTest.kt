package com.mylive.app.core.common

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class HttpCancellationTest {
    @Test fun cancellationClosesCallWhileWaitingForHeaders() = verifyCancellation(false)
    @Test fun cancellationClosesCallWhileReadingResponseBody() = verifyCancellation(true)

    private fun verifyCancellation(duringBody: Boolean) = runBlocking {
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        val activeCall = AtomicReference<Call>()
        val server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!duringBody) {
                    reached.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
                return MockResponse().setBody("response body").apply {
                    if (duringBody) throttleBody(1, 1, TimeUnit.SECONDS)
                }
            }
        }
        server.start()
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun callStart(call: Call) { activeCall.set(call) }
            override fun responseBodyStart(call: Call) { if (duringBody) reached.countDown() }
        }).build()
        val job = launch(Dispatchers.Default) { HttpClient(client).getText(server.url("/").toString()) }
        try {
            assertTrue(reached.await(5, TimeUnit.SECONDS))
            withTimeout(1_000L) { job.cancelAndJoin() }
            assertTrue(activeCall.get().isCanceled())
        } finally {
            release.countDown()
            job.cancelAndJoin()
            server.shutdown()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
