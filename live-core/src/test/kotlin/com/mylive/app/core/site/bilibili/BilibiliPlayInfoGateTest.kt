package com.mylive.app.core.site.bilibili

import com.mylive.app.core.common.CoreError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BilibiliPlayInfoGateTest {

    @Test
    fun adjacentRequestsWaitForTheMinimumStartInterval() = runBlocking {
        var nowMillis = 1_000L
        val waits = mutableListOf<Long>()
        val starts = mutableListOf<Long>()
        val gate = BilibiliPlayInfoGate(
            minIntervalMillis = 450L,
            nowMillis = { nowMillis },
            sleep = { durationMillis ->
                waits += durationMillis
                nowMillis += durationMillis
            }
        )

        gate.execute { starts += nowMillis }
        gate.execute { starts += nowMillis }

        assertEquals(listOf(1_000L, 1_450L), starts)
        assertEquals(listOf(450L), waits)
    }

    @Test
    fun http429RetriesWithBoundedBackoff() = runBlocking {
        val backoffs = mutableListOf<Long>()
        var attempts = 0
        val gate = BilibiliPlayInfoGate(
            minIntervalMillis = 0L,
            nowMillis = { 0L },
            sleep = { error("gate sleep is not expected") }
        )

        val result = requestBilibiliPlayInfo(
            gate = gate,
            retryDelaysMillis = listOf(800L, 1_600L),
            sleep = { backoffs += it }
        ) {
            attempts += 1
            if (attempts < 3) throw CoreError("rate limited", statusCode = 429)
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, attempts)
        assertEquals(listOf(800L, 1_600L), backoffs)
    }

    @Test
    fun cancellationReleasesTheGateForTheNextRequest() = runBlocking {
        val gate = BilibiliPlayInfoGate(minIntervalMillis = 0L)

        try {
            gate.execute<Unit> { throw CancellationException("room changed") }
        } catch (_: CancellationException) {
            // Expected: cancellation must leave the mutex through withLock's finally path.
        }

        assertEquals("next", gate.execute { "next" })
    }
}
