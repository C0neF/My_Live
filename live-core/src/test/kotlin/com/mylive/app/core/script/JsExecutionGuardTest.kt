package com.mylive.app.core.script

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Real behaviour tests for the QuickJS execution guard: scripts must not be able to pin the engine
 * thread forever, and a wedged script must not block later calls.
 */
class JsExecutionGuardTest {

    private val guard = JsExecutionGuard(threadNamePrefix = "quickjs-test")
    private val release = CountDownLatch(1)

    @After
    fun tearDown() {
        release.countDown()
        guard.shutdown()
    }

    @Test
    fun runsWorkOnADedicatedEngineThreadAndReturnsItsResult() {
        val threadName = guard.call(2_000L, "name") { Thread.currentThread().name }

        assertTrue(threadName, threadName.startsWith("quickjs-test-"))
        assertEquals("ok", guard.call(2_000L, "result") { "ok" })
    }

    @Test
    fun allCallsShareTheSameEngineThreadWhileNothingTimesOut() {
        val first = guard.call(2_000L, "first") { Thread.currentThread().name }
        val second = guard.call(2_000L, "second") { Thread.currentThread().name }

        assertEquals(first, second)
    }

    @Test
    fun scriptFailuresPropagateTheOriginalCause() {
        try {
            guard.call(2_000L, "boom") { throw IllegalStateException("JS function 'x' was not found") }
            fail("expected the original exception")
        } catch (expected: IllegalStateException) {
            assertEquals("JS function 'x' was not found", expected.message)
        }
    }

    @Test
    fun aScriptThatNeverFinishesTimesOutInsteadOfHangingTheCaller() {
        val started = CountDownLatch(1)

        try {
            guard.call(150L, "stuck") {
                started.countDown()
                blockUninterruptibly()
            }
            fail("expected a timeout")
        } catch (expected: JsExecutionTimeoutException) {
            assertTrue(expected.message!!.contains("stuck"))
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertEquals(1, guard.quarantinedThreadCount)
    }

    @Test
    fun laterCallsAreNotQueuedBehindAWedgedScript() {
        try {
            guard.call(150L, "stuck") { blockUninterruptibly() }
            fail("expected a timeout")
        } catch (expected: JsExecutionTimeoutException) {
            // The engine thread is now wedged inside "native" work.
        }

        val startedAt = System.nanoTime()
        val result = guard.call(2_000L, "next") { "still-serving" }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals("still-serving", result)
        assertTrue("later call took ${elapsedMs}ms", elapsedMs < 1_000)
    }

    /** Stands in for native QuickJS evaluation, which cannot be interrupted from the JVM. */
    private fun blockUninterruptibly(): String {
        while (release.count > 0L) {
            try {
                release.await(50, TimeUnit.MILLISECONDS)
            } catch (ignored: InterruptedException) {
                // Deliberately ignored: native code would not observe the interrupt either.
            }
        }
        return "finished-late"
    }
}
