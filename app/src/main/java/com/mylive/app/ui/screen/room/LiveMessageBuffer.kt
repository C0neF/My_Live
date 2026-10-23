package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessage

data class DisplayLiveMessage(
    val id: Long,
    val message: LiveMessage
)

/**
 * Ring buffer for the chat panel.
 *
 * Appends stay allocation-light. [snapshot] copies only when the UI publication cadence asks for
 * a new immutable list, so a burst does not copy the full history once per message.
 */
internal class LiveMessageBuffer(private val capacity: Int) {
    private val messages = ArrayDeque<DisplayLiveMessage>(capacity.coerceAtLeast(0))
    private var nextId = 0L
    private var snapshot: List<DisplayLiveMessage> = emptyList()

    fun append(message: LiveMessage) {
        if (capacity <= 0) {
            snapshot = emptyList()
            return
        }
        messages.addLast(DisplayLiveMessage(nextId++, message))
        while (messages.size > capacity) {
            messages.removeFirst()
        }
    }

    fun clear(): List<DisplayLiveMessage> {
        messages.clear()
        nextId = 0L
        snapshot = emptyList()
        return snapshot
    }

    /** Current immutable snapshot, copied only when content changed since the last publication. */
    fun snapshot(): List<DisplayLiveMessage> {
        if (snapshot.size == messages.size &&
            snapshot.firstOrNull()?.id == messages.firstOrNull()?.id &&
            snapshot.lastOrNull()?.id == messages.lastOrNull()?.id
        ) {
            return snapshot
        }
        snapshot = messages.toList()
        return snapshot
    }
}
