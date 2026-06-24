package com.mylive.app.core.common

import java.net.URI

private const val REDACTED_URL = "<redacted-url>"

internal fun safeUrlForLog(rawUrl: String): String {
    return runCatching {
        val uri = URI(rawUrl)
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("http", "https", "ws", "wss")) return REDACTED_URL

        val host = uri.host ?: return REDACTED_URL
        val displayHost = if (host.contains(":") && !host.startsWith("[")) {
            "[$host]"
        } else {
            host
        }
        val defaultPort = when (scheme) {
            "http", "ws" -> 80
            else -> 443
        }
        val authority = if (uri.port == -1 || uri.port == defaultPort) {
            displayHost
        } else {
            "$displayHost:${uri.port}"
        }
        val path = uri.rawPath?.ifBlank { "/" } ?: "/"
        "$scheme://$authority$path"
    }.getOrDefault(REDACTED_URL)
}

internal fun safePathForLog(rawPath: String): String {
    return rawPath
        .substringBefore("?")
        .substringBefore("#")
        .ifBlank { "/" }
}
