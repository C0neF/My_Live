package com.mylive.app.core.site.bilibili

import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.PlayQualityData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BiliBiliPlaybackQualityTest {

    @Test
    fun reportsActualQualityWhenOriginalRequestIsDowngraded() {
        val playurlData = JSONObject(
            """
            {
              "g_qn_desc": [
                {"qn": 10000, "desc": "原画"},
                {"qn": 250, "desc": "超清"}
              ],
              "stream": [
                {
                  "protocol_name": "http_stream",
                  "format": [
                    {
                      "format_name": "flv",
                      "codec": [
                        {
                          "codec_name": "avc",
                          "current_qn": 250,
                          "base_url": "/live/test_2500.flv?",
                          "url_info": [
                            {"host": "https://cdn.example.com", "extra": "token=1"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val result = parseBiliBiliPlayback(
            playurlData = playurlData,
            requestedQuality = LivePlayQuality(
                quality = "原画",
                data = PlayQualityData.BiliBili(qualityId = 10000)
            )
        )

        assertEquals(listOf("https://cdn.example.com/live/test_2500.flv?token=1"), result.urls)
        assertEquals(
            LivePlayQuality(
                quality = "超清",
                data = PlayQualityData.BiliBili(qualityId = 250)
            ),
            result.actualQuality
        )
    }
}
