package com.mylive.desktop

import java.net.URI

data class RoomAddress(val siteId: String, val roomId: String)

/** Only platform room links belong here. Media URLs are opened explicitly by the video action. */
fun parseRoomAddress(input: String, selectedSiteId: String): RoomAddress {
    val value = input.trim()
    require(value.isNotEmpty()) { "请输入房间号或直播间链接" }
    if (!value.contains("://") && !value.contains('/')) {
        require(value.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "房间号格式不正确" }
        return RoomAddress(selectedSiteId, value)
    }
    val uri = runCatching { URI(if (value.contains("://")) value else "https://$value") }
        .getOrElse { throw IllegalArgumentException("直播间链接格式不正确") }
    require(uri.scheme in setOf("http", "https")) { "请使用直播间网页链接" }
    val host = uri.host?.lowercase().orEmpty()
    val site = when {
        host == "live.bilibili.com" -> "bilibili"
        host == "douyu.com" || host.endsWith(".douyu.com") -> "douyu"
        host == "huya.com" || host.endsWith(".huya.com") -> "huya"
        host == "live.douyin.com" -> "douyin"
        else -> throw IllegalArgumentException("暂不支持此链接，请选择平台并输入房间号")
    }
    val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
    val id = if (site == "bilibili" && segments.firstOrNull() == "blanc") segments.getOrNull(1)
        else segments.lastOrNull()
    require(id != null && id.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "链接中没有有效房间号" }
    return RoomAddress(site, id)
}
