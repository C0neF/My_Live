package com.mylive.app.ui.screen.room

import androidx.compose.ui.graphics.Color
import com.mylive.app.core.model.LiveSuperChatMessage

internal fun superChatFingerprint(message: LiveSuperChatMessage): String {
    val id = message.id?.trim()
    if (!id.isNullOrEmpty()) return "id:$id"
    return "${message.userName}|${message.message}|${message.price}|${message.startTime}"
}

internal fun mergeActiveSuperChats(
    current: List<LiveSuperChatMessage>,
    incoming: Iterable<LiveSuperChatMessage>,
    nowMillis: Long
): List<LiveSuperChatMessage> {
    val byFingerprint = LinkedHashMap<String, LiveSuperChatMessage>()
    for (item in current) {
        if (item.endTime > nowMillis) {
            byFingerprint[superChatFingerprint(item)] = item
        }
    }
    for (item in incoming) {
        if (item.endTime > nowMillis) {
            byFingerprint[superChatFingerprint(item)] = item
        }
    }
    return byFingerprint.values.sortedBy { it.endTime }
}

internal fun remainingSuperChatSeconds(
    message: LiveSuperChatMessage,
    nowMillis: Long
): Int {
    return ((message.endTime - nowMillis) / 1000L).toInt().coerceAtLeast(0)
}

internal fun parseSuperChatColor(
    value: String,
    fallback: Color
): Color {
    val hex = value.trim().removePrefix("#")
    return runCatching {
        when (hex.length) {
            6 -> Color(0xFF000000 or hex.toLong(radix = 16))
            8 -> Color(hex.toLong(radix = 16))
            else -> fallback
        }
    }.getOrDefault(fallback)
}
