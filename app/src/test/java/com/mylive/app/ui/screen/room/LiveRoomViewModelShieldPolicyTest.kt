package com.mylive.app.ui.screen.room

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveRoomViewModelShieldPolicyTest {

    @Test
    fun liveRoomViewModelFiltersShieldedMessagesBeforeEmittingDanmakuEvents() {
        val source = File("src/main/java/com/mylive/app/ui/screen/room/LiveRoomViewModel.kt").readText()

        assertTrue(source.contains("ShieldRepository"))
        assertTrue(source.contains("LiveMessageShieldConfig"))
        assertTrue(source.contains("shouldShieldLiveMessage("))
        assertTrue(
            source.indexOf("shouldDropMessageForShield(message, route)") <
                source.indexOf("_messages.tryEmit(message)")
        )
    }
}
