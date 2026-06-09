package com.mylive.app.ui.screen.room

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class LiveRoomPlayerSuperChatPolicyTest {

    @Test
    fun liveRoomPlayerDoesNotRenderSuperChatOverlay() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomScreen.kt").readText()

        assertFalse(source.contains("PlayerSuperChatOverlay("))
        assertFalse(source.contains("playerShowSuperChat"))
        assertFalse(source.contains("播放区域也显示SC悬浮栏"))
    }
}
