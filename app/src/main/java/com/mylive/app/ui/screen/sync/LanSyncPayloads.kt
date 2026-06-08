package com.mylive.app.ui.screen.sync

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
