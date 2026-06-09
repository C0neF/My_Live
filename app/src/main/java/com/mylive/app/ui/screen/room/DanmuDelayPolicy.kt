package com.mylive.app.ui.screen.room

internal fun resolveDanmuDelayMs(
    globalDelayMs: Int,
    delayBySiteJson: String,
    siteId: String
): Int {
    val siteDelay = parseDanmuDelayBySite(delayBySiteJson)[siteId.trim()]
    return (siteDelay ?: globalDelayMs).coerceIn(0, 5000)
}

internal fun updateDanmuDelayBySite(
    delayBySiteJson: String,
    siteId: String,
    delayMs: Int
): String {
    val safeSiteId = siteId.trim()
    if (safeSiteId.isEmpty()) return delayBySiteJson
    val next = parseDanmuDelayBySite(delayBySiteJson).toMutableMap()
    next[safeSiteId] = delayMs.coerceIn(0, 5000)
    return next.toSortedMap().entries.joinToString(
        separator = ",",
        prefix = "{",
        postfix = "}"
    ) { (key, value) -> "\"${key.jsonEscaped()}\":$value" }
}

internal fun parseDanmuDelayBySite(delayBySiteJson: String): Map<String, Int> {
    if (delayBySiteJson.isBlank()) return emptyMap()
    val entryRegex = """"([^"\\]+)"\s*:\s*(\d+)""".toRegex()
    return entryRegex.findAll(delayBySiteJson).associate { match ->
        val key = match.groupValues[1].trim()
        val value = match.groupValues[2].toIntOrNull()?.coerceIn(0, 5000) ?: 0
        key to value
    }.filterKeys { it.isNotEmpty() }
}

internal fun danmuDelaySiteLabel(siteId: String): String =
    when (siteId) {
        "bilibili" -> "哔哩哔哩"
        "douyu" -> "斗鱼"
        "huya" -> "虎牙"
        "douyin" -> "抖音"
        else -> "当前平台"
    }

private fun String.jsonEscaped(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
