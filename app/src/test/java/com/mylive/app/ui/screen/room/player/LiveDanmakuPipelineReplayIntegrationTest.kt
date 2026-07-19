package com.mylive.app.ui.screen.room.player

import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.ui.screen.room.LiveMessageBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fixed-input pipeline integration without WebSocket or Compose:
 * scripted messages fan out to chat buffer + surface add hook.
 */
class LiveDanmakuPipelineReplayIntegrationTest {

    @Test
    fun replayDrivesChatBufferAndSurfaceSinkInOrder() = runBlocking {
        val messages = listOf(
            LiveMessage(type = LiveMessageType.CHAT, userName = "a", message = "one"),
            LiveMessage(type = LiveMessageType.CHAT, userName = "b", message = "two"),
            LiveMessage(type = LiveMessageType.ONLINE, userName = "", message = "", onlineCount = 12),
            LiveMessage(type = LiveMessageType.CHAT, userName = "c", message = "three")
        )
        val buffer = LiveMessageBuffer(capacity = 50)
        val surface = mutableListOf<String>()
        var online = 0

        replayLiveDanmakuMessages(
            messages = messages,
            onSurface = { msg ->
                if (msg.type == LiveMessageType.CHAT) {
                    surface.add(msg.message)
                }
            },
            onChat = { msg ->
                when (msg.type) {
                    LiveMessageType.ONLINE -> online = msg.onlineCount ?: 0
                    LiveMessageType.CHAT, LiveMessageType.GIFT, LiveMessageType.SUPER_CHAT -> {
                        buffer.add(msg)
                    }
                }
            }
        )

        assertEquals(listOf("one", "two", "three"), surface)
        assertEquals(listOf("one", "two", "three"), buffer.snapshot().map { it.message.message })
        assertEquals(12, online)
    }

    @Test
    fun replaySourceSizeMatchesScript() {
        val messages = List(5) {
            LiveMessage(type = LiveMessageType.CHAT, userName = "u", message = "m$it")
        }
        assertEquals(5, LiveDanmakuReplaySource(messages).size())
    }
}
