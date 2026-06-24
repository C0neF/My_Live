package com.mylive.app.core.site.douyin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DouyinLegacyQualityPolicyTest {

    @Test
    fun legacyQualityUrlsUseStableKeysInsteadOfJsonIterationOrder() {
        val quality = JSONObject(
            """{"name":"高清","sdk_key":"hd","level":3}"""
        )
        val flvMap = JSONObject(
            """
            {
              "SD1": "https://example.com/ld.flv",
              "FULL_HD1": "https://example.com/origin.flv",
              "SD2": "https://example.com/sd.flv",
              "HD1": "https://example.com/hd.flv"
            }
            """.trimIndent()
        )
        val hlsMap = JSONObject(
            """
            {
              "SD2": "https://example.com/sd.m3u8",
              "HD1": "https://example.com/hd.m3u8",
              "FULL_HD1": "https://example.com/origin.m3u8",
              "SD1": "https://example.com/ld.m3u8"
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "https://example.com/hd.flv",
                "https://example.com/hd.m3u8"
            ),
            resolveLegacyDouyinQualityUrls(quality, flvMap, hlsMap)
        )
    }

    @Test
    fun singleLegacyStreamFallsBackToItsOnlyAvailableKey() {
        val quality = JSONObject(
            """{"name":"标清","sdk_key":"origin","level":1}"""
        )
        val flvMap = JSONObject(
            """{"FULL_HD1":"https://example.com/only.flv"}"""
        )

        assertEquals(
            listOf("https://example.com/only.flv"),
            resolveLegacyDouyinQualityUrls(quality, flvMap, null)
        )
    }
}
