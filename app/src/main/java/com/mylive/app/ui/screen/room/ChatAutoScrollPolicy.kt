package com.mylive.app.ui.screen.room

internal fun shouldAutoScrollChat(
    autoScrollDisabled: Boolean,
    hasMessages: Boolean
): Boolean {
    return hasMessages && !autoScrollDisabled
}

internal fun reduceChatAutoScrollDisabled(
    currentDisabled: Boolean,
    isNearBottom: Boolean,
    userScrolledAwayFromBottom: Boolean
): Boolean {
    if (isNearBottom) return false
    if (userScrolledAwayFromBottom) return true
    return currentDisabled
}

internal fun shouldShowLatestChatButton(
    previousLastMessageId: Long?,
    currentLastMessageId: Long?,
    autoScrollDisabled: Boolean
): Boolean {
    if (currentLastMessageId == null || previousLastMessageId == currentLastMessageId) {
        return false
    }
    return autoScrollDisabled
}

internal fun <T, K> mergeChatDisplayMessages(
    currentDisplay: List<T>,
    sourceMessages: List<T>,
    autoScrollDisabled: Boolean,
    maxEnabledMessages: Int,
    keyOf: (T) -> K
): List<T> {
    if (sourceMessages.isEmpty()) return emptyList()

    if (!autoScrollDisabled) {
        return sourceMessages.takeLast(maxEnabledMessages.coerceAtLeast(0))
    }

    if (currentDisplay.isEmpty()) return sourceMessages

    val displayedKeys = currentDisplay.mapTo(mutableSetOf()) { keyOf(it) }
    val merged = ArrayList<T>(currentDisplay.size + sourceMessages.size)
    merged.addAll(currentDisplay)
    for (message in sourceMessages) {
        if (displayedKeys.add(keyOf(message))) {
            merged.add(message)
        }
    }
    return merged
}
