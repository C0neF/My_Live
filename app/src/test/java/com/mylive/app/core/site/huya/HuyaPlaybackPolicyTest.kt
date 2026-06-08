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
}
