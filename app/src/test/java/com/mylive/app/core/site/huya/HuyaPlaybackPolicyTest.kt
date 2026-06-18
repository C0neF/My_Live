package com.mylive.app.core.site.huya

import com.mylive.app.core.common.HttpClient
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HuyaPlaybackPolicyTest {

    @Test
    fun huyaPlaybackSignerKeepsPresenterUidAsLong() {
        val siteSource = File("src/main/java/com/mylive/app/core/site/huya/HuyaSite.kt").readText()
        val modelSource = File("src/main/java/com/mylive/app/core/model/HuyaModels.kt").readText()

        assertTrue(
            "Huya presenter uid can exceed Int.MAX_VALUE and must not collapse to 0",
            modelSource.contains("val presenterUid: Long")
        )
        assertTrue(
            "Huya topSid/subSid parsing must preserve 64-bit ids",
            siteSource.contains("private fun asPositiveLong")
        )
        assertTrue(
            "Huya anti-code generation must sign with a 64-bit presenter uid",
            siteSource.contains("fun buildAntiCode(stream: String, presenterUid: Long")
        )
        assertFalse(
            "Huya playback signing must not use the old Int-only presenter uid",
            modelSource.contains("val presenterUid: Int")
        )
    }

    @Test
    fun antiCodeForLargePresenterUidDoesNotCollapseToZero() {
        val okHttpClient = OkHttpClient()
        val site = HuyaSite(HttpClient(okHttpClient), okHttpClient)
        val antiCode = "fm=YWJjX2RlZg%3D%3D&wsTime=65abc123&fs=gctex&t=0&ctype=huya_pc_exe"

        val result = site.buildAntiCode(
            stream = "1239544359035-1239544359035-11140190768256253952",
            presenterUid = 1_239_544_359_035L,
            antiCode = antiCode
        )

        assertFalse("large Huya presenter uid must not become u=0", result.contains("u=0"))
        assertFalse("large Huya presenter uid must not become uid=0", result.contains("uid=0"))
        assertTrue(result.contains("seqid="))
    }

    /**
     * Regression: the real Huya `fm` (base64 anti-code seed) is percent-encoded
     * TWICE inside `sFlvToken`. The previous port decoded it only once, leaving
     * stray `%XX`/`+` in the string fed to Base64.decode, which threw and dropped
     * the line (empty play URL list -> playback failure). Mirroring Dart, `fm`
     * must be decoded twice (form layer + component layer) before base64.
     */
    @Test
    fun antiCodeDecodesDoublePercentEncodedFm() {
        val okHttpClient = OkHttpClient()
        val site = HuyaSite(HttpClient(okHttpClient), okHttpClient)

        // base64 payload containing '+', '/' and '=' so BOTH percent layers matter;
        // include 0x5F ('_') so secretPrefix extraction (split('_')) has a separator.
        val seedBytes = byteArrayOf(-5, -1, -2, 0x5F, 'x'.code.toByte())
        val base64 = java.util.Base64.getEncoder().encodeToString(seedBytes)
        val singleEnc = java.net.URLEncoder.encode(base64, "UTF-8")   // + -> %2B, / -> %2F, = -> %3D
        val doubleEnc = java.net.URLEncoder.encode(singleEnc, "UTF-8") // % -> %25
        val antiCode = "fm=$doubleEnc&wsTime=65abc123&fs=gctex&t=0&ctype=huya_pc_exe"

        // Old single-decode threw IllegalArgumentException here; the fix must not throw.
        val result = site.buildAntiCode(
            stream = "teststream",
            presenterUid = 1_239_544_359_035L,
            antiCode = antiCode
        )

        assertTrue(
            "double-encoded fm must yield a 32-hex wsSecret",
            Regex("wsSecret=[0-9a-f]{32}").containsMatchIn(result)
        )
        assertTrue(result.contains("seqid="))
    }
}
