package com.mylive.app.core.site.douyin

import com.mylive.app.core.script.JsEngine
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * QuickJS failures reach Kotlin as [Error], not [Exception].
 *
 * A missing or incompatible native library surfaces as `UnsatisfiedLinkError`, and a wedged script
 * can surface as a bare `java.lang.Error` — neither is caught by `catch (e: Exception)`. Signing
 * runs on the room-entry path, so an escaping `Error` takes the app down rather than degrading to
 * "this platform did not load".
 *
 * Previously asserted by grepping `DouyinSign.kt` for the literal text `"catch (e: Throwable)"`,
 * which says nothing about what actually escapes. These throw the real thing through the real
 * boundary instead.
 */
class DouyinCrashPolicyTest {

    @Test
    fun aNativeLoaderErrorDuringAbogusSigningDegradesToTheUnsignedUrl() = runBlocking {
        val engine = mockk<JsEngine>()
        coEvery { engine.execute(any(), any(), *anyVararg()) } throws
            UnsatisfiedLinkError("libquickjs.so not found")

        val url = "https://live.douyin.com/webcast/room/web/enter/?aid=6383"
        val signed = DouyinSign(engine).getAbogusUrl(url, "UA")

        // Unsigned rather than crashed: the caller sees a risk-control response, not a dead app.
        assertEquals(url, signed)
    }

    @Test
    fun aBareErrorDuringSignatureGenerationDegradesToAnEmptySignature() = runBlocking {
        val engine = mockk<JsEngine>()
        coEvery {
            engine.executeWithRetry(any(), any(), any(), any(), any())
        } throws Error("quickjs runtime wedged")

        val signature = DouyinSign(engine).getSignature(roomId = "123", uniqueId = "456")

        assertEquals("", signature)
    }

    @Test
    fun anOrdinaryExceptionIsContainedTheSameWay() = runBlocking {
        val engine = mockk<JsEngine>()
        coEvery { engine.execute(any(), any(), *anyVararg()) } throws
            IllegalStateException("JS function 'getABogus' was not found")

        val url = "https://live.douyin.com/webcast/room/web/enter/?aid=6383"

        assertEquals(url, DouyinSign(engine).getAbogusUrl(url, "UA"))
    }

    @Test
    fun successfulSigningStillAppendsBothParameters() = runBlocking {
        val engine = mockk<JsEngine>()
        coEvery { engine.execute(any(), any(), *anyVararg()) } returns "abogus-value"

        val url = "https://live.douyin.com/webcast/room/web/enter/?aid=6383"
        val signed = DouyinSign(engine).getAbogusUrl(url, "UA")

        // Guards the negative tests above: they would also pass if signing never worked at all.
        assert(signed.startsWith("$url&msToken=")) { signed }
        assert(signed.contains("&a_bogus=abogus-value")) { signed }
    }
}
