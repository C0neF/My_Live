package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessage

data class DisplayLiveMessage(
    val id: Long,
    val message: LiveMessage
)

/**
 * Ring buffer for the chat panel.
 *
 * Snapshot lists are reused when unchanged so StateFlow subscribers can use
 * reference equality / structural equality without forcing a copy on every poll.
 * [add] still returns a fresh list when content changes (required for StateFlow).
 */
internal class LiveMessageBuffer(private val capacity: Int) {
    private val messages = ArrayDeque<DisplayLiveMessage>(capacity.coerceAtLeast(0))
    private var nextId = 0L
    private var snapshot: List<DisplayLiveMessage> = emptyList()

    fun add(message: LiveMessage): List<DisplayLiveMessage> {
        if (capacity <= 0) {
            snapshot = emptyList()
            return snapshot
        }
        messages.addLast(DisplayLiveMessage(nextId++, message))
        while (messages.size > capacity) {
            messages.removeFirst()
        }
        snapshot = messages.toList()
        return snapshot
    }

    fun clear(): List<DisplayLiveMessage> {
        messages.clear()
        nextId = 0L
        snapshot = emptyList()
        return snapshot
    }

    /** Current snapshot without allocating when empty or when reusing last add/clear result. */
    fun snapshot(): List<DisplayLiveMessage> = snapshot
}
