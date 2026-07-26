package com.mylive.app.core.site.douyu

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Douyu resolves one play URL per CDN, and each one is its own POST.
 *
 * They used to run in a sequential loop, which put N round trips in front of the first video
 * frame. These tests pin the two properties that made the sequential version safe — result order
 * and per-CDN failure isolation — so the concurrent version cannot quietly lose either.
 */
class DouyuPlayUrlConcurrencyTest {

    @Test
    fun requestsForEveryCdnAreInFlightAtTheSameTime() = runBlocking {
        val cdns = listOf("ws-h5", "tct-h5", "ali-h5", "scdn-h5")
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val allStarted = CompletableDeferred<Unit>()

        val urls = resolveDouyuPlayUrls(cdns) { cdn ->
            val now = inFlight.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            if (now == cdns.size) allStarted.complete(Unit)
            // Every call parks until the last one has started. Under the old sequential loop
            // this would deadlock, because the second call could never begin.
            withTimeout(5_000) { allStarted.await() }
            inFlight.decrementAndGet()
            "https://$cdn/live.flv"
        }

        assertEquals(cdns.size, peak.get())
        assertEquals(cdns.size, urls.size)
    }

    @Test
    fun resultsKeepTheCdnPreferenceOrderRegardlessOfWhoAnswersFirst() = runBlocking {
        // The list doubles as the preference order (scdn sorted last) and the player treats
        // earlier entries as better lines, so completion order must not leak into it.
        val cdns = listOf("fast", "slow", "slowest")
        val urls = resolveDouyuPlayUrls(cdns) { cdn ->
            when (cdn) {
                "slowest" -> delay(60)
                "slow" -> delay(30)
            }
            "https://$cdn/live.flv"
        }

        assertEquals(
            listOf("https://fast/live.flv", "https://slow/live.flv", "https://slowest/live.flv"),
            urls
        )
    }

    @Test
    fun oneFailingCdnDoesNotCostTheOthers() = runBlocking {
        val urls = resolveDouyuPlayUrls(listOf("good-1", "broken", "good-2")) { cdn ->
            if (cdn == "broken") throw IllegalStateException("cdn is down")
            "https://$cdn/live.flv"
        }

        assertEquals(listOf("https://good-1/live.flv", "https://good-2/live.flv"), urls)
    }

    @Test
    fun aCdnThatAnswersWithNothingIsDropped() = runBlocking {
        val urls = resolveDouyuPlayUrls(listOf("empty", "good")) { cdn ->
            if (cdn == "empty") "" else "https://$cdn/live.flv"
        }

        assertEquals(listOf("https://good/live.flv"), urls)
    }

    @Test
    fun everyCdnFailingYieldsNoUrlsRatherThanAnError() = runBlocking {
        val urls = resolveDouyuPlayUrls(listOf("a", "b")) { throw IllegalStateException("down") }

        assertTrue(urls.isEmpty())
    }

    @Test
    fun noCdnsMeansNoRequests() = runBlocking {
        val calls = AtomicInteger(0)
        val urls = resolveDouyuPlayUrls(emptyList()) { calls.incrementAndGet(); "x" }

        assertTrue(urls.isEmpty())
        assertEquals(0, calls.get())
    }

    @Test
    fun everyCdnIsRequestedExactlyOnce() = runBlocking {
        val seen = mutableListOf<String>()
        val cdns = listOf("a", "b", "c")

        resolveDouyuPlayUrls(cdns) { cdn ->
            synchronized(seen) { seen.add(cdn) }
            "https://$cdn/live.flv"
        }

        assertEquals(cdns.sorted(), seen.sorted())
    }
}
