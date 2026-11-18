package com.mylive.app.core.model

// LivePlayQuality uses a sealed interface for site-specific data.
// NOT @Serializable because sealed interface with complex types.
// These are transient objects used in-memory during play URL resolution.

sealed interface PlayQualityData {
    data class BiliBili(val qualityId: Int) : PlayQualityData
    data class Douyu(val rate: Int, val cdns: List<String>) : PlayQualityData
    data class Huya(val lineIndex: Int, val bitRateIndex: Int) : PlayQualityData
    data class Douyin(val urls: List<String>) : PlayQualityData
}

data class LivePlayQuality(
    val quality: String,
    val data: PlayQualityData,
    val sort: Int = 0
)
