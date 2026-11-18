package com.mylive.app.core.site.douyin

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinTtwidTest {

    @Test
    fun registerResponseIsCachedUntilExpiryAndRefreshedWhenForcedOrInvalidated() = runBlocking {
        val hits = mutableListOf<String>()
        val server = server { request ->
            hits += request.path.orEmpty()
            MockResponse().setHeader("Set-Cookie", "ttwid=fresh-token; Path=/; Max-Age=10")
        }
        var now = 1_000L
        val provider = provider(server, clock = { now })
        try {
            assertEquals("ttwid=fresh-token", provider.current())
            assertEquals("ttwid=fresh-token", provider.current())
            assertEquals(listOf("/register"), hits)

            provider.invalidate()
            assertEquals("ttwid=fresh-token", provider.current(forceRefresh = false))
            assertEquals(listOf("/register", "/register"), hits)

            now += 10_000L
            assertEquals("ttwid=fresh-token", provider.current())
            assertEquals(3, hits.size)

            assertEquals("ttwid=fresh-token", provider.current(forceRefresh = true))
            assertEquals(4, hits.size)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"aid\":1768"))
            assertTrue(body.contains("www.ixigua.com"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun homepageCookieIsUsedWhenRegisterDoesNotIssueOne() = runBlocking {
        val server = server { request ->
            if (request.path == "/register") {
                MockResponse().setResponseCode(500)
            } else {
                MockResponse().setHeader("Set-Cookie", "other=1; Path=/")
                    .setHeader("Set-Cookie", "ttwid=from-home; Path=/; Max-Age=30")
            }
        }
        val provider = provider(server)
        try {
            assertEquals("ttwid=from-home", provider.current())
            assertEquals("/register", server.takeRequest().path)
            assertEquals("/", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun failedIssuersFallBackWithoutCachingTheStaticCookieAsSuccess() = runBlocking {
        var now = 5_000L
        var calls = 0
        val server = server {
            calls += 1
            MockResponse().setResponseCode(503).setHeader("Set-Cookie", "ttwid=bad\rvalue; Path=/")
        }
        val provider = provider(server, clock = { now })
        try {
            assertEquals(DouyinTtwidProvider.FALLBACK_COOKIE, provider.current())
            assertEquals(DouyinTtwidProvider.FALLBACK_COOKIE, provider.current())
            val duringBackoff = calls
            assertTrue(duringBackoff >= 2)
            assertEquals(DouyinTtwidProvider.FALLBACK_COOKIE, provider.current())
            assertEquals(duringBackoff, calls)

            now += 60_001L
            assertEquals(DouyinTtwidProvider.FALLBACK_COOKIE, provider.current())
            assertTrue(calls > duringBackoff)
        } finally {
            server.shutdown()
        }
    }

    private fun provider(server: MockWebServer, clock: () -> Long = { 0L }) = DouyinTtwidProvider(
        client = OkHttpClient(),
        userAgent = "test-agent",
        clock = clock,
        registerUrl = server.url("/register").toString(),
        liveHomeUrl = server.url("/").toString()
    )

    private fun server(response: (RecordedRequest) -> MockResponse): MockWebServer {
        val mock = MockWebServer()
        mock.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = response(request)
        }
        mock.start()
        return mock
    }
}
