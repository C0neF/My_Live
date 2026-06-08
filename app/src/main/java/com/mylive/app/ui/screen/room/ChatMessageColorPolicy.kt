package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessageColor

internal data class ChatMessageColorPolicy(
    val applyMessageColorToUserName: Boolean,
    val applyMessageColorToText: Boolean
)

internal fun resolveChatMessageColorPolicy(
    color: LiveMessageColor
): ChatMessageColorPolicy {
    val hasCustomMessageColor = color != LiveMessageColor.WHITE &&
        color != LiveMessageColor(r = 0, g = 0, b = 0)
    return ChatMessageColorPolicy(
        applyMessageColorToUserName = false,
        applyMessageColorToText = hasCustomMessageColor
    )
}
