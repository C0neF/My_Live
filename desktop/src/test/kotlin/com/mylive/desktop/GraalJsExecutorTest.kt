package com.mylive.desktop

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GraalJsExecutorTest {
    @Test fun passesArgumentsAsValuesWithoutEvaluatingThem() = runBlocking {
        GraalJsExecutor().use { js ->
            val hostile = "'); throw new Error('injected'); //"
            assertEquals(hostile, js.executeRaw("function echo(value) { return value; }", "echo", hostile))
        }
    }

    @Test fun bundledSigningScriptExecutesOnDesktop() = runBlocking {
        GraalJsExecutor().use { js ->
            val result = js.execute("douyin_abogus.js", "getABogus", "room_id=123", "Mozilla/5.0")
            assertTrue(result.length > 20)
        }
    }

    @Test fun bundledWebSocketSigningScriptExecutesOnDesktop() = runBlocking {
        GraalJsExecutor().use { js ->
            val result = js.execute("douyin_webmssdk.js", "getMSSDKSignature", "0123456789abcdef0123456789abcdef", "Mozilla/5.0")
            assertTrue(result.length > 10)
        }
    }

    @Test fun retriesShareOneEvaluatedScriptContext() = runBlocking {
        GraalJsExecutor().use { js ->
            assertEquals("3", js.executeWithRetry("retry_test.js", "next", emptyArray(), 4) { it == "3" })
        }
    }

    @Test fun guestCannotReadHostFilesOrEnvironment() {
        GraalJsExecutor().use { js ->
            assertThrows(Exception::class.java) {
                runBlocking { js.executeRaw("function run() { return Java.type('java.lang.System').getenv('PATH'); }", "run") }
            }
        }
    }

    @Test fun infiniteLoopTimesOutAndLaterSigningStillWorks() = runBlocking {
        GraalJsExecutor(timeoutMillis = 2_000).use { js ->
            assertThrows(IllegalStateException::class.java) {
                runBlocking { js.executeRaw("function run() { while(true) {} }", "run") }
            }
            assertEquals("ok", js.executeRaw("function run() { return 'ok'; }", "run"))
        }
    }

    @Test fun coroutineCancellationStopsGuestLoopAndReleasesQueue() = runBlocking {
        GraalJsExecutor(timeoutMillis = 8_000).use { js ->
            val spinning = launch { js.executeRaw("function run() { while(true) {} }", "run") }
            delay(200)
            spinning.cancelAndJoin()
            assertEquals("ok", withTimeout(4_000) { js.executeRaw("function run() { return 'ok'; }", "run") })
        }
    }
}
