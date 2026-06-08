package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessage

data class DisplayLiveMessage(
    val id: Long,
    val message: LiveMessage
)

internal class LiveMessageBuffer(private val capacity: Int) {
    private val messages = ArrayDeque<DisplayLiveMessage>(capacity.coerceAtLeast(0))
    private var nextId = 0L

    fun add(message: LiveMessage): List<DisplayLiveMessage> {
        if (capacity <= 0) return emptyList()
        messages.addLast(DisplayLiveMessage(nextId++, message))
        while (messages.size > capacity) {
            messages.removeFirst()
        }
        return messages.toList()
    }

    fun clear(): List<DisplayLiveMessage> {
        messages.clear()
        nextId = 0L
        return emptyList()
    }
}
