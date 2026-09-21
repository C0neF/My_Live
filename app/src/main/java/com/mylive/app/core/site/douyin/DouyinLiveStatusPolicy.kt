package com.mylive.app.core.site.douyin

import org.json.JSONObject

internal enum class DouyinStatusLookupRoute {
    WebRid,
    RoomId
}

internal fun douyinStatusLookupOrder(roomId: String): List<DouyinStatusLookupRoute> =
    if (roomId.trim().length <= 16) {
        listOf(DouyinStatusLookupRoute.WebRid, DouyinStatusLookupRoute.RoomId)
    } else {
        listOf(DouyinStatusLookupRoute.RoomId, DouyinStatusLookupRoute.WebRid)
    }

internal fun parseDouyinLiveStatus(data: JSONObject): Boolean? {
    for (key in listOf("status", "live_status", "room_status", "status_str")) {
        val status = parseDouyinStatusValue(data.opt(key))
        if (status != null) return status == 2
    }
    return null
}

private fun parseDouyinStatusValue(value: Any?): Int? = when (value) {
    null,
    JSONObject.NULL -> null
    is Number -> value.toInt()
    is String -> value.trim().toIntOrNull()
    is JSONObject -> listOf("status", "live_status", "room_status")
        .firstNotNullOfOrNull { key -> parseDouyinStatusValue(value.opt(key)) }
    else -> null
}
