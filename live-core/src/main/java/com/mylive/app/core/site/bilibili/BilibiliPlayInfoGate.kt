package com.mylive.app.core.site.bilibili

import com.mylive.app.core.common.CoreError
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class BilibiliPlayInfoGate(
    private val minIntervalMillis: Long = 450L,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) {
    private val mutex = Mutex()
    private var lastRequestStartedAtMillis: Long? = null

    suspend fun <T> execute(request: suspend () -> T): T = mutex.withLock {
        val previousStart = lastRequestStartedAtMillis
        if (previousStart != null) {
            val remaining = minIntervalMillis - (nowMillis() - previousStart)
            if (remaining > 0L) sleep(remaining)
        }
        lastRequestStartedAtMillis = nowMillis()
        request()
    }
}

internal suspend fun <T> requestBilibiliPlayInfo(
    gate: BilibiliPlayInfoGate,
    retryDelaysMillis: List<Long> = listOf(800L, 1_600L),
    sleep: suspend (Long) -> Unit = { delay(it) },
    onRetry: (attempt: Int, delayMillis: Long) -> Unit = { _, _ -> },
    request: suspend () -> T
): T {
    var retryIndex = 0
    while (true) {
        try {
            return gate.execute(request)
        } catch (error: CoreError) {
            val retryDelay = retryDelaysMillis.getOrNull(retryIndex)
            if (error.statusCode != 429 || retryDelay == null) throw error
            retryIndex += 1
            onRetry(retryIndex, retryDelay)
            sleep(retryDelay)
        }
    }
}
