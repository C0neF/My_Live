package com.mylive.app.core.model

// Site-specific play data (stored in LiveRoomDetail equivalent)
sealed interface SitePlayData {
    data class BiliBili(val dummy: Unit = Unit) : SitePlayData
    data class Douyu(val rate: Int, val cdns: List<String>, val args: String) : SitePlayData
    data class Huya(
        val urlData: HuyaUrlDataModel
    ) : SitePlayData
    data class Douyin(val streamUrl: Map<String, Any?>) : SitePlayData
}

// Site-specific danmaku args
sealed interface DanmakuArgs {
    data class BiliBili(
        val roomId: Int,
        val uid: Int,
        val token: String,
        val buvid: String,
        val serverHost: String,
        val cookie: String
    ) : DanmakuArgs

    data class Douyu(
        val roomId: String
    ) : DanmakuArgs

    data class Huya(
        val ayyuid: Long,
        val topSid: Long,
        val subSid: Long
    ) : DanmakuArgs

    data class Douyin(
        val webRid: String,
        val roomId: String,
        val userId: String,
        val cookie: String
    ) : DanmakuArgs
}
