package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveMessageBufferTest {

    @Test
    fun addAssignsStableIdsWhenOldMessagesAreEvicted() {
        val buffer = LiveMessageBuffer(capacity = 2)
        val message = LiveMessage(
            type = LiveMessageType.CHAT,
            userName = "alice",
            message = "hello"
        )

        assertEquals(listOf(0L), buffer.add(message).map { it.id })
        assertEquals(listOf(0L, 1L), buffer.add(message).map { it.id })
        assertEquals(listOf(1L, 2L), buffer.add(message).map { it.id })
    }

    @Test
    fun clearRemovesMessagesAndResetsIds() {
        val buffer = LiveMessageBuffer(capacity = 2)
        val message = LiveMessage(
            type = LiveMessageType.CHAT,
            userName = "alice",
            message = "hello"
        )

        buffer.add(message)
        assertEquals(emptyList<DisplayLiveMessage>(), buffer.clear())
        assertEquals(listOf(0L), buffer.add(message).map { it.id })
    }
}
