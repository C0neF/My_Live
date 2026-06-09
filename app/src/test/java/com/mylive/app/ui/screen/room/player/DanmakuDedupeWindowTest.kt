package com.mylive.app.ui.screen.room.player

import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageColor
import com.mylive.app.core.model.LiveMessageType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuDedupeWindowTest {

    @Test
    fun duplicateIsDroppedWithinWindowAndAllowedAfterPruneStep() {
        val window = DanmakuDedupeWindow()
        window.configure(enabled = true, windowSize = 2, stepSize = 2, strictMode = false)

        assertFalse(window.shouldDrop(chat("Alice", "1")))
        assertFalse(window.shouldDrop(chat("Alice", "2")))
        assertTrue(window.shouldDrop(chat("Alice", "1")))

        assertFalse(window.shouldDrop(chat("Alice", "3")))
        assertFalse(window.shouldDrop(chat("Alice", "4")))
        assertFalse(window.shouldDrop(chat("Alice", "5")))
        assertFalse(window.shouldDrop(chat("Alice", "1")))
    }

    @Test
    fun strictModeIgnoresSenderName() {
        val window = DanmakuDedupeWindow()
        window.configure(enabled = true, windowSize = 10, stepSize = 2, strictMode = true)

        assertFalse(window.shouldDrop(chat("Alice", "同一句")))
        assertTrue(window.shouldDrop(chat("Bob", "同一句")))
    }

    @Test
    fun disabledWindowNeverDropsMessages() {
        val window = DanmakuDedupeWindow()
        window.configure(enabled = false, windowSize = 1, stepSize = 1, strictMode = true)

        assertFalse(window.shouldDrop(chat("Alice", "同一句")))
        assertFalse(window.shouldDrop(chat("Bob", "同一句")))
    }

    private fun chat(userName: String, message: String): LiveMessage {
        return LiveMessage(
            type = LiveMessageType.CHAT,
            userName = userName,
            message = message,
            color = LiveMessageColor.WHITE
        )
    }
}
