package com.mylive.app.core.script

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/** Raised when a JS call exceeds its deadline. */
class JsExecutionTimeoutException(message: String) : RuntimeException(message)

/**
 * Runs blocking QuickJS work on a dedicated single thread with a hard deadline.
 *
 * QuickJS evaluation happens in native code and cannot be interrupted from the JVM, so a script
 * with an accidental (or malicious) infinite loop would otherwise pin the engine thread forever and
 * every later call would silently queue behind it. On timeout this guard therefore:
 *
 * 1. fails the caller fast with [JsExecutionTimeoutException], and
 * 2. quarantines the stuck thread and installs a fresh one, so later calls are unaffected.
 *
 * The quarantined thread is a daemon and dies with the process.
 */
class JsExecutionGuard(private val threadNamePrefix: String = "quickjs") {

    private val lock = Any()
    private val threadSerial = AtomicInteger(0)
    private val quarantineCount = AtomicInteger(0)

    private var runner: Runner? = null

    /** Number of threads abandoned because a script blew through its deadline. */
    val quarantinedThreadCount: Int
        get() = quarantineCount.get()

    /**
     * Runs [block] on the engine thread and blocks the caller until it finishes.
     *
     * @param timeoutMs hard deadline in milliseconds.
     * @param label short description used in the timeout message.
     * @throws JsExecutionTimeoutException when [block] does not finish in time.
     */
    fun <T> call(timeoutMs: Long, label: String, block: () -> T): T {
        var active = activeRunner()
        val future: Future<T> = try {
            active.executor.submit(Callable { block() })
        } catch (e: RejectedExecutionException) {
            // The runner was quarantined between lookup and submit; replace it and retry once.
            active = activeRunner(replacing = active)
            active.executor.submit(Callable { block() })
        }

        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            quarantine(active)
            throw JsExecutionTimeoutException("JS execution timed out after ${timeoutMs}ms: $label")
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    /** Stops the current engine thread. A later [call] transparently starts a new one. */
    fun shutdown() {
        val current = synchronized(lock) {
            val existing = runner
            runner = null
            existing
        }
        current?.executor?.shutdownNow()
    }

    private fun activeRunner(replacing: Runner? = null): Runner = synchronized(lock) {
        val current = runner
        if (current != null && current !== replacing) {
            return current
        }
        val next = Runner(threadNamePrefix, threadSerial.incrementAndGet())
        runner = next
        return next
    }

    private fun quarantine(stuck: Runner) {
        val replaced = synchronized(lock) {
            if (runner !== stuck) return
            runner = null
            true
        }
        if (replaced) {
            quarantineCount.incrementAndGet()
            // Interrupts do not stop native evaluation, but they release anything interruptible and
            // stop new work from being scheduled on the wedged thread.
            stuck.executor.shutdownNow()
        }
    }

    private class Runner(prefix: String, index: Int) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "$prefix-$index").apply { isDaemon = true }
        }
    }
}
