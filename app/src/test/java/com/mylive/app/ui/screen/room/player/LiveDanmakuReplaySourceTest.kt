package com.mylive.app.ui.screen.room.player

import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveDanmakuReplaySourceTest {

    @Test
    fun replayEmitsMessagesInOrder() = runBlocking {
        val messages = listOf(
            LiveMessage(type = LiveMessageType.CHAT, userName = "a", message = "1"),
            LiveMessage(type = LiveMessageType.CHAT, userName = "b", message = "2")
        )
        val source = LiveDanmakuReplaySource(messages)
        assertEquals(2, source.size())
        assertEquals(listOf("1", "2"), source.asFlow().toList().map { it.message })
    }

    @Test
    fun replayHelperFansOutToSurfaceAndChatSinks() = runBlocking {
        val messages = listOf(
            LiveMessage(type = LiveMessageType.CHAT, userName = "a", message = "x")
        )
        val surface = mutableListOf<String>()
        val chat = mutableListOf<String>()
        replayLiveDanmakuMessages(
            messages = messages,
            onSurface = { surface.add(it.message) },
            onChat = { chat.add(it.message) }
        )
        assertEquals(listOf("x"), surface)
        assertEquals(listOf("x"), chat)
    }
}
