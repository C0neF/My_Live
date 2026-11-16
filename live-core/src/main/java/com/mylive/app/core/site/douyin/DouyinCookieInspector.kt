package com.mylive.app.core.site.douyin

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class DouyinCookieKind {
    NotConfigured,
    TtwidOnly,
    LoginStateShape,
    Unrecognized
}

data class DouyinCookieInspection(
    val kind: DouyinCookieKind,
    val estimatedExpiryEpochMillis: Long? = null
)

object DouyinCookieInspector {
    private val loginStateKeys = setOf(
        "sessionid",
        "sid_guard",
        "sid_tt",
        "uid_tt",
        "passport_csrf_token",
        "passport_csrf_token_default",
        "mstoken",
        "odin_tt",
        "passport_auth_status",
        "login_status"
    )

    fun normalizeInput(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return ""

        val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
        var cookie = lines.firstNotNullOfOrNull { line ->
            if (line.startsWith("cookie:", ignoreCase = true)) {
                line.substringAfter(':').trim()
            } else {
                null
            }
        }
        if (cookie == null) {
            val cookieLabelIndex = lines.indexOfFirst { it.equals("cookie", ignoreCase = true) }
            cookie = lines.getOrNull(cookieLabelIndex + 1).takeIf { cookieLabelIndex >= 0 }
        }
        cookie = cookie ?: text
        if (cookie.startsWith("cookie:", ignoreCase = true)) {
            cookie = cookie.substringAfter(':').trim()
        }
        return if ('=' in cookie) cookie.trim() else "ttwid=${cookie.trim()}"
    }

    fun inspect(input: String): DouyinCookieInspection {
        val cookie = normalizeInput(input)
        if (cookie.isEmpty()) return DouyinCookieInspection(DouyinCookieKind.NotConfigured)

        val pairs = parsePairs(cookie)
        val normalizedKeys = pairs.keys.map(String::lowercase).toSet()
        val kind = when {
            normalizedKeys == setOf("ttwid") -> DouyinCookieKind.TtwidOnly
            normalizedKeys.any(loginStateKeys::contains) -> DouyinCookieKind.LoginStateShape
            else -> DouyinCookieKind.Unrecognized
        }
        return DouyinCookieInspection(
            kind = kind,
            estimatedExpiryEpochMillis = pairs.entries
                .firstOrNull { it.key.equals("sid_guard", ignoreCase = true) }
                ?.value
                ?.let(::parseSidGuardExpiry)
        )
    }

    private fun parsePairs(cookie: String): Map<String, String> = buildMap {
        cookie.split(';').forEach { rawPart ->
            val part = rawPart.trim()
            val separator = part.indexOf('=')
            if (separator <= 0) return@forEach
            val key = part.substring(0, separator).trim()
            val value = part.substring(separator + 1).trim()
            if (key.isNotEmpty()) put(key, value)
        }
    }

    private fun parseSidGuardExpiry(value: String): Long? {
        val decoded = try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            value
        }
        val parts = decoded.split('|')
        if (parts.size < 3) return null
        val loginTime = parts[1].toLongOrNull() ?: return null
        val maxAgeSeconds = parts[2].toLongOrNull() ?: return null
        val loginTimeMillis = if (loginTime > 1_000_000_000_000L) loginTime else loginTime * 1_000L
        return loginTimeMillis + maxAgeSeconds * 1_000L
    }
}
