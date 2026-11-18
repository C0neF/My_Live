package com.mylive.app.core.model

// Site-specific danmaku args
sealed interface DanmakuArgs {
    data class BiliBili(
        val roomId: Int,
        val uid: Long,
        val token: String,
        val buvid: String,
        val serverHost: String,
        val cookie: String,
        val backupHosts: List<String> = emptyList()
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
