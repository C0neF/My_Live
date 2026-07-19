package com.mylive.app.ui.screen.room.player

import com.mylive.app.core.model.LiveMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fixed-input seam for deterministic danmaku / chat pipeline benchmarks and tests.
 * Production platforms emit through LiveDanmaku; tests and benches can drive the same sinks
 * with a scripted message sequence without WebSocket or Compose.
 */
class LiveDanmakuReplaySource(
    private val messages: List<LiveMessage>
) {
    fun asFlow(): Flow<LiveMessage> = flow {
        for (message in messages) {
            emit(message)
        }
    }

    fun size(): Int = messages.size
}

/**
 * Apply a scripted sequence to the surface controller (and optionally a chat sink).
 * Pure orchestration helper — no Android threading assumptions beyond caller context.
 */
suspend fun replayLiveDanmakuMessages(
    messages: Iterable<LiveMessage>,
    onSurface: (LiveMessage) -> Unit,
    onChat: ((LiveMessage) -> Unit)? = null
) {
    for (message in messages) {
        onChat?.invoke(message)
        onSurface(message)
    }
}
