package com.mylive.app.core.site.douyu

import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.LivePlayUrl
import com.mylive.app.core.model.PlayQualityData

internal data class DouyuVideoRequirement(val width: Int, val height: Int, val frameRate: Double)

private val videoRequirementPattern = Regex(
    """(4K|2K|2160P|1440P|1080P|720P)\s*(\d{2,3})(?:FPS|帧)?(?:$|[\s)）])""",
    RegexOption.IGNORE_CASE
)

internal fun douyuVideoRequirement(qualityName: String): DouyuVideoRequirement? {
    val match = videoRequirementPattern.find(qualityName) ?: return null
    val (width, height) = when (match.groupValues[1].uppercase()) {
        "4K", "2160P" -> 3840 to 2160
        "2K", "1440P" -> 2560 to 1440
        "1080P" -> 1920 to 1080
        "720P" -> 1280 to 720
        else -> return null
    }
    val frameRate = match.groupValues[2].toDouble().takeIf { it > 0 } ?: return null
    return DouyuVideoRequirement(width, height, frameRate)
}

internal data class DouyuPlaybackSource(
    val url: String,
    val rate: Int?,
    val qualityName: String?,
    val qualityIndex: Int
)

internal fun selectDouyuPlaybackSources(
    requestedQuality: LivePlayQuality,
    sources: List<DouyuPlaybackSource>
): LivePlayUrl {
    val requestedData = requestedQuality.data as PlayQualityData.Douyu
    val selected = sources.firstOrNull { it.rate == requestedData.rate }
        ?: sources.filter { it.rate != null }.minByOrNull { it.qualityIndex }
        ?: sources.firstOrNull()
        ?: return LivePlayUrl(emptyList())

    // Keep automatic line recovery at the same quality, including its frame-rate tier.
    val matchingSources = sources.filter { it.rate == selected.rate }
    val actualQuality = selected.rate?.let { rate ->
        requestedQuality.copy(
            quality = selected.qualityName?.takeIf { it.isNotBlank() }
                ?: if (rate == requestedData.rate) requestedQuality.quality else "画质 $rate",
            data = requestedData.copy(rate = rate)
        )
    }
    return LivePlayUrl(
        urls = matchingSources.map { it.url },
        actualQuality = actualQuality,
        videoFrameRateHint = actualQuality?.let { douyuVideoRequirement(it.quality)?.frameRate?.toFloat() }
    )
}
