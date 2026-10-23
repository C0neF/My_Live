package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        buffer.append(message)
        assertEquals(listOf(0L), buffer.snapshot().map { it.id })
        buffer.append(message)
        assertEquals(listOf(0L, 1L), buffer.snapshot().map { it.id })
        buffer.append(message)
        assertEquals(listOf(1L, 2L), buffer.snapshot().map { it.id })
    }

    @Test
    fun clearRemovesMessagesAndResetsIds() {
        val buffer = LiveMessageBuffer(capacity = 2)
        val message = LiveMessage(
            type = LiveMessageType.CHAT,
            userName = "alice",
            message = "hello"
        )

        buffer.append(message)
        assertEquals(emptyList<DisplayLiveMessage>(), buffer.clear())
        buffer.append(message)
        assertEquals(listOf(0L), buffer.snapshot().map { it.id })
    }

    @Test
    fun snapshotReusesListReferenceUntilNextMutation() {
        val buffer = LiveMessageBuffer(capacity = 2)
        val message = LiveMessage(
            type = LiveMessageType.CHAT,
            userName = "alice",
            message = "hello"
        )
        buffer.append(message)
        val first = buffer.snapshot()
        assertTrue(first === buffer.snapshot())
        buffer.append(message)
        val second = buffer.snapshot()
        assertTrue(second === buffer.snapshot())
        assertFalse(first === second)
        val cleared = buffer.clear()
        assertTrue(cleared === buffer.snapshot())
        assertTrue(cleared.isEmpty())
    }
}
