package com.mylive.app.ui.screen.sync

import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.local.entity.ShieldEntity
import org.json.JSONArray
import org.json.JSONObject

internal fun encodeShieldKeywordsForLanSync(shields: List<ShieldEntity>): String {
    val arr = JSONArray()
    shields.asSequence()
        .map { it.value }
        .filter { it.startsWith("keyword:") }
        .map { it.removePrefix("keyword:").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .forEach { arr.put(it) }
    return arr.toString()
}

internal fun decodeLanSyncShieldKeywords(body: String): List<String> {
    val arr = JSONArray(body)
    val keywords = mutableListOf<String>()
    for (i in 0 until arr.length()) {
        val raw = arr.opt(i)
        val value = when (raw) {
            is JSONObject -> raw.optString("value")
            else -> raw?.toString().orEmpty()
        }
        val keyword = when {
            value.startsWith("keyword:") -> value.removePrefix("keyword:")
            value.startsWith("user:") -> ""
            else -> value
        }.trim()
        if (keyword.isNotEmpty()) {
            keywords.add(keyword)
        }
    }
    return keywords
}

internal fun encodeFollowTagsForLanSync(tags: List<FollowUserTagEntity>): String {
    val arr = JSONArray()
    tags.forEach { tag ->
        arr.put(JSONObject().apply {
            put("id", tag.id)
            put("tag", tag.tag)
            put("userIds", JSONArray(tag.userIds))
        })
    }
    return arr.toString()
}

internal fun decodeFollowTagsForLanSync(body: String): List<FollowUserTagEntity> {
    val arr = JSONArray(body)
    val tags = mutableListOf<FollowUserTagEntity>()
    for (i in 0 until arr.length()) {
        val item = arr.getJSONObject(i)
        val userIds = mutableListOf<String>()
        val userIdsArray = item.optJSONArray("userIds") ?: item.optJSONArray("userId")
        if (userIdsArray != null) {
            for (j in 0 until userIdsArray.length()) {
                userIds.add(userIdsArray.getString(j))
            }
        }
        val tagName = item.optString("tag", item.optString("name")).trim()
        require(tagName.isNotEmpty()) { "tag is required" }
        tags.add(
            FollowUserTagEntity(
                id = item.getString("id"),
                tag = tagName,
                userIds = userIds
            )
        )
    }
    return tags
}

internal fun validateLanSyncResponse(statusCode: Int, isSuccessful: Boolean, body: String?) {
    if (statusCode == 401) {
        throw Exception("未配对或配对码错误，请扫描对方二维码或填写配对码")
    }
    if (!isSuccessful) {
        throw Exception("HTTP $statusCode")
    }

    val trimmedBody = body?.trim().orEmpty()
    if (trimmedBody.startsWith("{")) {
        val payload = JSONObject(trimmedBody)
        if (payload.has("status") && !payload.optBoolean("status", true)) {
            throw Exception(payload.optString("message", "sync failed"))
        }
    }
}
