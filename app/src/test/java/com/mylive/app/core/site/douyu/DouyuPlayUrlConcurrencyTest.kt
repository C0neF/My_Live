package com.mylive.app.core.site.douyu

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
    fun requestsRunTogetherButNeverExceedTheExplicitLimit() = runBlocking {
        val maxConcurrency = 5
        val cdns = (1..8).map { "cdn-$it" }
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val started = AtomicInteger(0)
        val firstWaveStarted = CompletableDeferred<Unit>()
        val releaseFirstWave = CompletableDeferred<Unit>()

        val result = async {
            resolveDouyuCdnResults(cdns) { cdn ->
                val startedCount = started.incrementAndGet()
                val now = inFlight.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                if (now == maxConcurrency) firstWaveStarted.complete(Unit)
                try {
                    if (startedCount <= maxConcurrency) {
                        releaseFirstWave.await()
                    }
                    "https://$cdn/live.flv"
                } finally {
                    inFlight.decrementAndGet()
                }
            }
        }

        withTimeout(5_000) { firstWaveStarted.await() }
        assertEquals(maxConcurrency, started.get())
        assertEquals(maxConcurrency, peak.get())
        releaseFirstWave.complete(Unit)

        val urls = result.await()
        assertEquals(cdns.size, urls.size)
        assertTrue(peak.get() <= maxConcurrency)
    }

    @Test
    fun resultsKeepTheCdnPreferenceOrderRegardlessOfWhoAnswersFirst() = runBlocking {
        // The list doubles as the preference order (scdn sorted last) and the player treats
        // earlier entries as better lines, so completion order must not leak into it.
        val cdns = listOf("fast", "slow", "slowest")
        val urls = resolveDouyuCdnResults(cdns) { cdn ->
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
        val urls = resolveDouyuCdnResults(listOf("good-1", "broken", "good-2")) { cdn ->
            if (cdn == "broken") throw IllegalStateException("cdn is down")
            "https://$cdn/live.flv"
        }

        assertEquals(listOf("https://good-1/live.flv", "https://good-2/live.flv"), urls)
    }

    @Test
    fun aCdnThatAnswersWithNothingIsDropped() = runBlocking {
        val urls = resolveDouyuCdnResults(listOf("empty", "good")) { cdn ->
            if (cdn == "empty") null else "https://$cdn/live.flv"
        }

        assertEquals(listOf("https://good/live.flv"), urls)
    }

    @Test
    fun everyCdnFailingYieldsNoUrlsRatherThanAnError() = runBlocking {
        val urls = resolveDouyuCdnResults<String>(listOf("a", "b")) { throw IllegalStateException("down") }

        assertTrue(urls.isEmpty())
    }

    @Test
    fun noCdnsMeansNoRequests() = runBlocking {
        val calls = AtomicInteger(0)
        val urls = resolveDouyuCdnResults(emptyList()) { calls.incrementAndGet(); "x" }

        assertTrue(urls.isEmpty())
        assertEquals(0, calls.get())
    }

    @Test
    fun everyCdnIsRequestedExactlyOnce() = runBlocking {
        val seen = mutableListOf<String>()
        val cdns = listOf("a", "b", "c")

        resolveDouyuCdnResults(cdns) { cdn ->
            synchronized(seen) { seen.add(cdn) }
            "https://$cdn/live.flv"
        }

        assertEquals(cdns.sorted(), seen.sorted())
    }
}
