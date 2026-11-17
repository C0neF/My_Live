package com.mylive.app.ui.screen.room

import com.mylive.app.testing.liveRoomUiSource

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class LiveRoomPlayerSuperChatPolicyTest {

    @Test
    fun liveRoomPlayerDoesNotRenderSuperChatOverlay() {
        val source = liveRoomUiSource()

        assertFalse(source.contains("PlayerSuperChatOverlay("))
        assertFalse(source.contains("playerShowSuperChat"))
        assertFalse(source.contains("播放区域也显示SC悬浮栏"))
    }
}
