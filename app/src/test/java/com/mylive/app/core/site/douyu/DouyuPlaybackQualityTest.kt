package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.HttpClient
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LiveRoomDetail
import com.mylive.app.core.model.PlayQualityData
import com.mylive.app.core.site.LiveSiteAccountStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DouyuPlaybackQualityTest {
    private val cdns = listOf("a", "b", "scdn")
    private val original = LivePlayQuality("原画2K120", PlayQualityData.Douyu(0, cdns))
    private val detail = LiveRoomDetail("100", "Test room", "", "Test anchor")

    private fun site(rates: Map<String, Int?>, includeQualityNames: Boolean = true): DouyuSite {
        val accounts = mockk<LiveSiteAccountStore>()
        coEvery { accounts.cookieFor(any()) } returns ""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val data = if (request.url.encodedPath.endsWith("getEncryption")) {
                JSONObject("""{"key":"key","rand_str":"random","enc_time":1,"enc_data":"descriptor","is_special":0}""")
            } else {
                val form = request.body as FormBody
                val cdn = (0 until form.size).first { form.name(it) == "cdn" }.let(form::value)
                JSONObject().apply {
                    put("rtmp_url", "https://stream.example")
                    put("rtmp_live", "$cdn.flv")
                    rates[cdn]?.let { put("rate", it) }
                    if (includeQualityNames) {
                        put("multirates", org.json.JSONArray("""[{"name":"原画2K120","rate":0},{"name":"蓝光8M","rate":8},{"name":"蓝光4M","rate":4}]"""))
                    }
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("")
                .body(JSONObject().put("error", 0).put("data", data).toString().toResponseBody()).build()
        }.build()
        val http = HttpClient(client)
        return DouyuSite(http, client, DouyuAccountSession(accounts, http) { 100_000L }, DouyuPlaybackSigner(http) { 100_000L })
    }

    @Test fun requestedOriginalPrefersMatchingLinesOverEarlierDowngradedLines() = runBlocking {
        val result = site(mapOf("a" to 4, "b" to 0, "scdn" to 0)).getPlayUrls(detail, original)

        assertEquals(listOf("https://stream.example/b.flv", "https://stream.example/scdn.flv"), result.urls)
        assertEquals(original, result.actualQuality)
        assertEquals(120f, result.videoFrameRateHint)
    }

    @Test fun allDowngradedLinesReportTheReturnedQualityInsteadOfOriginal120() = runBlocking {
        val result = site(cdns.associateWith { 4 }).getPlayUrls(detail, original)

        assertEquals(cdns.map { "https://stream.example/$it.flv" }, result.urls)
        assertEquals(LivePlayQuality("蓝光4M", PlayQualityData.Douyu(4, cdns)), result.actualQuality)
        assertNull(result.videoFrameRateHint)
    }

    @Test fun differentDowngradesUseTheBestReturnedQualityAndKeepRecoveryAtThatQuality() = runBlocking {
        val result = site(mapOf("a" to 4, "b" to 8, "scdn" to 4)).getPlayUrls(detail, original)

        assertEquals(listOf("https://stream.example/b.flv"), result.urls)
        assertEquals(LivePlayQuality("蓝光8M", PlayQualityData.Douyu(8, cdns)), result.actualQuality)
    }

    @Test fun matchingLowerQualityDoesNotAccidentallyChooseOriginal() = runBlocking {
        val requested = LivePlayQuality("蓝光4M", PlayQualityData.Douyu(4, cdns))
        val result = site(mapOf("a" to 0, "b" to 4, "scdn" to 4)).getPlayUrls(detail, requested)

        assertEquals(listOf("https://stream.example/b.flv", "https://stream.example/scdn.flv"), result.urls)
        assertEquals(requested, result.actualQuality)
    }

    @Test fun missingRateRetainsPlaybackWithoutInventingAnActualQuality() = runBlocking {
        val result = site(cdns.associateWith { null }).getPlayUrls(detail, original)

        assertEquals(cdns.map { "https://stream.example/$it.flv" }, result.urls)
        assertNull(result.actualQuality)
        assertNull(result.videoFrameRateHint)
    }

    @Test fun missingNameForAKnownDowngradeDoesNotReuseTheOriginalLabel() = runBlocking {
        val result = site(cdns.associateWith { 4 }, includeQualityNames = false).getPlayUrls(detail, original)

        assertEquals("画质 4", result.actualQuality?.quality)
        assertEquals(PlayQualityData.Douyu(4, cdns), result.actualQuality?.data)
        assertNull(result.videoFrameRateHint)
    }

    @Test fun resolved2k60DoesNotInheritTheRequested2k120Hint() {
        val result = selectDouyuPlaybackSources(original, listOf(
            DouyuPlaybackSource("https://stream.example/a.flv", 2, "原画2K60", 1),
            DouyuPlaybackSource("https://stream.example/b.flv", 2, "原画2K60", 1)
        ))

        assertEquals("原画2K60", result.actualQuality?.quality)
        assertEquals(60f, result.videoFrameRateHint)
        assertEquals(2, result.urls.size)
    }

    @Test fun requested2k60KeepsItsFrameRateWhenTheResponseOmitsTheLabel() {
        val requested = LivePlayQuality("原画2K60", PlayQualityData.Douyu(0, cdns))
        val result = selectDouyuPlaybackSources(requested, listOf(
            DouyuPlaybackSource("https://stream.example/a.flv", 0, null, 0)
        ))

        assertEquals(requested, result.actualQuality)
        assertEquals(60f, result.videoFrameRateHint)
    }

    @Test fun resolved4kTiersCarryTheirOwnFrameRateAndKeepTheirOriginalQuality() {
        for ((label, fps) in listOf("原画4K30" to 30f, "原画4K60" to 60f,
            "原画4K120" to 120f, "原画2160P60帧" to 60f)) {
            val requested = original.copy(quality = label)
            val result = selectDouyuPlaybackSources(requested, listOf(
                DouyuPlaybackSource("https://stream.example/4k.flv", 0, label, 0)
            ))

            assertEquals(requested, result.actualQuality)
            assertEquals(fps, result.videoFrameRateHint)
            assertEquals(listOf("https://stream.example/4k.flv"), result.urls)
        }
    }
}
