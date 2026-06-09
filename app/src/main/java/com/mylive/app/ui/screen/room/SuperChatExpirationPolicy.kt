package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveSuperChatMessage

internal fun activeSuperChats(
    messages: List<LiveSuperChatMessage>,
    nowMillis: Long
): List<LiveSuperChatMessage> {
    return messages.filter { it.endTime > nowMillis }
}

internal fun countActiveSuperChats(
    messages: List<LiveSuperChatMessage>,
    nowMillis: Long
): Int {
    return activeSuperChats(messages, nowMillis).size
}
