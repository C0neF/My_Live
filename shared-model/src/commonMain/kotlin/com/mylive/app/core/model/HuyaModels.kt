package com.mylive.app.core.model

enum class HuyaLineType { FLV, HLS }

data class HuyaUrlDataModel(
    val url: String,
    val uid: String,
    val lines: List<HuyaLineModel> = emptyList(),
    val bitRates: List<HuyaBitRateModel> = emptyList()
)

data class HuyaLineModel(
    val line: String,
    val cdnType: String = "",
    val flvAntiCode: String = "",
    val hlsAntiCode: String = "",
    val streamName: String = "",
    val lineType: HuyaLineType = HuyaLineType.FLV,
    var bitRate: Int = 0,
    val presenterUid: Long = 0L
)

data class HuyaBitRateModel(
    val name: String,
    val bitRate: Int
)
