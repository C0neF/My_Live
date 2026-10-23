package com.mylive.app.ui.screen.sync

import java.net.URI

internal fun syncEndpointDisplayName(configuredUrl: String): String {
    val value = configuredUrl.trim()
    if (value.isEmpty()) return "默认同步服务"
    val host = try {
        URI(value).host
    } catch (_: Exception) {
        null
    }
    return host?.takeIf(String::isNotBlank)
        ?.let { "自定义同步服务 · $it" }
        ?: "自定义同步服务"
}

internal fun syncProbeStatusText(
    isReachable: Boolean,
    latencyMillis: Long?,
    error: String
): String = if (isReachable) {
    latencyMillis?.let { "可用 · $it ms" } ?: "可用"
} else {
    error.trim().takeIf(String::isNotEmpty)?.let { "不可用 · $it" } ?: "不可用"
}
