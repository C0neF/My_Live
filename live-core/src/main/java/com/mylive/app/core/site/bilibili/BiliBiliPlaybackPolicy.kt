package com.mylive.app.core.site.bilibili

import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.core.model.PlayQualityData
import org.json.JSONObject

internal fun parseBiliBiliPlayback(
    playurlData: JSONObject,
    requestedQuality: LivePlayQuality
): LivePlayUrl {
    val requestedQualityId =
        (requestedQuality.data as PlayQualityData.BiliBili).qualityId
    val qualityNames = mutableMapOf<Int, String>()
    val qualityDescriptions = playurlData.optJSONArray("g_qn_desc")
    if (qualityDescriptions != null) {
        for (i in 0 until qualityDescriptions.length()) {
            val item = qualityDescriptions.optJSONObject(i) ?: continue
            val id = item.optInt("qn")
            if (id > 0) {
                qualityNames[id] = item.optString("desc", "")
            }
        }
    }

    val urlsByQuality = linkedMapOf<Int, MutableList<String>>()
    val streamList = playurlData.optJSONArray("stream")
        ?: return LivePlayUrl(urls = emptyList())
    for (i in 0 until streamList.length()) {
        val streamItem = streamList.optJSONObject(i) ?: continue
        val formatList = streamItem.optJSONArray("format") ?: continue
        for (j in 0 until formatList.length()) {
            val formatItem = formatList.optJSONObject(j) ?: continue
            val codecList = formatItem.optJSONArray("codec") ?: continue
            for (k in 0 until codecList.length()) {
                val codecItem = codecList.optJSONObject(k) ?: continue
                val currentQualityId = codecItem.optInt("current_qn", requestedQualityId)
                    .takeIf { it > 0 }
                    ?: requestedQualityId
                val urlList = codecItem.optJSONArray("url_info") ?: continue
                val baseUrl = codecItem.optString("base_url")
                val qualityUrls = urlsByQuality.getOrPut(currentQualityId) {
                    mutableListOf()
                }
                for (l in 0 until urlList.length()) {
                    val urlItem = urlList.optJSONObject(l) ?: continue
                    qualityUrls.add(
                        "${urlItem.optString("host")}$baseUrl${urlItem.optString("extra")}"
                    )
                }
            }
        }
    }

    val actualQualityId = when {
        urlsByQuality.containsKey(requestedQualityId) -> requestedQualityId
        else -> urlsByQuality.keys.maxOrNull()
    }
    val urls = actualQualityId?.let(urlsByQuality::get)?.toMutableList() ?: mutableListOf()
    urls.sortWith(
        compareBy<String> { it.contains("mcdn") }
    )

    return LivePlayUrl(
        urls = urls,
        actualQuality = actualQualityId?.let { id ->
            LivePlayQuality(
                quality = qualityNames[id].orEmpty().ifBlank {
                    if (id == requestedQualityId) requestedQuality.quality else "画质 $id"
                },
                data = PlayQualityData.BiliBili(qualityId = id)
            )
        }
    )
}
