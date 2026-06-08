package com.mylive.app.core.site

import org.json.JSONArray
import org.json.JSONObject

private val URL_PATTERN = Regex("""(?i)(https?:)?//[^\s"'<>]+""")

internal fun normalizeSiteImageUrl(value: Any?): String? {
    if (value == null || value == JSONObject.NULL) return null
    val normalized = value.toString()
        .trim()
        .trim('"', '\'')
        .replace("\\u002F", "/")
        .replace("\\/", "/")
    if (normalized.isEmpty()) return null

    val candidate = URL_PATTERN.find(normalized)?.value ?: normalized
    return when {
        candidate.startsWith("//") -> "https:$candidate"
        candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true) -> candidate
        else -> null
    }
}

internal fun firstSiteImageUrl(
    data: Any?,
    preferredKeys: List<String> = emptyList(),
    depth: Int = 0
): String? {
    if (depth > 8 || data == null || data == JSONObject.NULL) return null
    if (data is String || data is Number || data is Boolean) {
        return normalizeSiteImageUrl(data)
    }
    if (data is JSONArray) {
        for (i in 0 until data.length()) {
            val resolved = firstSiteImageUrl(data.opt(i), preferredKeys, depth + 1)
            if (!resolved.isNullOrEmpty()) return resolved
        }
        return null
    }
    if (data !is JSONObject) return null

    for (key in preferredKeys) {
        val resolved = firstSiteImageUrl(data.opt(key), preferredKeys, depth + 1)
        if (!resolved.isNullOrEmpty()) return resolved
    }

    val keys = data.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (preferredKeys.contains(key)) continue
        val resolved = firstSiteImageUrl(data.opt(key), preferredKeys, depth + 1)
        if (!resolved.isNullOrEmpty()) return resolved
    }
    return null
}
