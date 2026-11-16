package com.mylive.app.core.site.bilibili

internal fun parseBilibiliUserIdFromCookie(cookie: String): Long {
    return cookie.split(';')
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("DedeUserID=") }
        ?.substringAfter('=')
        ?.toLongOrNull()
        ?.takeIf { it >= 0L }
        ?: 0L
}
