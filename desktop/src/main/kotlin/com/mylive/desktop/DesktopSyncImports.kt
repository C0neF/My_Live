package com.mylive.desktop

import com.mylive.app.core.sync.LanSyncImportType
import org.json.JSONArray
import org.json.JSONObject

internal suspend fun DesktopLibrary.importSyncPayload(type: LanSyncImportType, text: String, overlay: Boolean) {
        require(text.toByteArray(Charsets.UTF_8).size <= com.mylive.app.core.common.MAX_IMPORTED_JSON_BYTES)
        when (type) {
            LanSyncImportType.PROFILE -> check(importRooms(RoomProfileCodec.parse(text)))
            LanSyncImportType.FOLLOW -> {
                val root = if (text.trimStart().startsWith("[")) JSONObject().put("followUsers", JSONArray(text)) else JSONObject(text).let {
                    JSONObject().put("followUsers", it.getJSONArray("follows")).put("followUserTags", it.getJSONArray("tags"))
                }
                check(importRooms(RoomProfileCodec.parse(root.toString()), replaceFollows = overlay))
            }
            LanSyncImportType.HISTORY -> check(importRooms(RoomProfileCodec.parse(JSONObject().put("histories", JSONArray(text)).toString()), replaceHistory = overlay))
            LanSyncImportType.TAG -> {
                val rows = JSONArray(text); val groups = mutableMapOf<String, MutableList<String>>()
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i); val tag = row.getString("tag")
                    require(tag.isNotBlank() && tag.length <= 80)
                    val ids = row.optJSONArray("userIds") ?: row.optJSONArray("userId") ?: JSONArray()
                    for (j in 0 until ids.length()) groups.getOrPut(ids.getString(j)) { mutableListOf() }.add(tag)
                }
                check(importGroups(groups, overlay))
            }
            LanSyncImportType.BLOCKED_WORD -> {
                val rows = JSONArray(text); val words = List(rows.length()) { rows.getString(it).removePrefix("keyword:") }
                require(words.all { it.isNotBlank() && it.length <= 200 })
                check(updateDanmaku { it.copy(keywords = ((if (overlay) emptyList() else it.keywords) + words).distinct()) })
            }
        }
    }

