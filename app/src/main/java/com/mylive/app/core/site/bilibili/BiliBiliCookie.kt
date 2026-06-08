package com.mylive.app.core.site.bilibili

internal fun parseBilibiliUserIdFromCookie(cookie: String): Int {
    return cookie.split(';')
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("DedeUserID=") }
        ?.substringAfter('=')
        ?.toIntOrNull()
        ?: 0
}
