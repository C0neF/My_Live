package com.mylive.app.ui.screen.room

import com.mylive.app.core.model.LiveSuperChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class SuperChatExpirationPolicyTest {

    @Test
    fun countsOnlyActiveSuperChats() {
        val now = 1_000L
        val messages = listOf(
            LiveSuperChatMessage(userName = "active", message = "still visible", endTime = 1_001L),
            LiveSuperChatMessage(userName = "expired", message = "gone", endTime = 1_000L)
        )

        assertEquals(1, countActiveSuperChats(messages, now))
    }

    @Test
    fun filtersExpiredSuperChats() {
        val now = 1_000L
        val active = LiveSuperChatMessage(userName = "active", message = "still visible", endTime = 1_001L)
        val expired = LiveSuperChatMessage(userName = "expired", message = "gone", endTime = 999L)

        assertEquals(listOf(active), activeSuperChats(listOf(active, expired), now))
    }
}
