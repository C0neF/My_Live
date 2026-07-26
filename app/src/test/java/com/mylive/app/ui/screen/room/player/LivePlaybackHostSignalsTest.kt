package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LivePlaybackHostSignalsTest {

    @Test
    fun autoPipSignalIsToggledExplicitly() {
        LivePlaybackHostSignals.setAutoPipOnLeave(false)
        assertFalse(LivePlaybackHostSignals.autoPipOnLeave)
        LivePlaybackHostSignals.setAutoPipOnLeave(true)
        assertTrue(LivePlaybackHostSignals.autoPipOnLeave)
        LivePlaybackHostSignals.setAutoPipOnLeave(false)
        assertFalse(LivePlaybackHostSignals.autoPipOnLeave)
    }

    @Test
    fun disposingPreviousOwnerDoesNotDisableCurrentRoomAutoPip() {
        LivePlaybackHostSignals.setAutoPipOnLeave(false)
        val previousRoom = Any()
        val currentRoom = Any()

        LivePlaybackHostSignals.setAutoPipOnLeave(previousRoom, true)
        LivePlaybackHostSignals.setAutoPipOnLeave(currentRoom, true)
        LivePlaybackHostSignals.setAutoPipOnLeave(previousRoom, false)

        assertTrue(LivePlaybackHostSignals.autoPipOnLeave)

        LivePlaybackHostSignals.setAutoPipOnLeave(currentRoom, false)
        assertFalse(LivePlaybackHostSignals.autoPipOnLeave)
    }

    @Test
    fun mainActivityAndHostUseSharedPipSignal() {
        val host = File("src/main/java/com/mylive/app/ui/screen/room/player/LivePlaybackHost.kt")
            .readText()
        val activity = File("src/main/java/com/mylive/app/MainActivity.kt").readText()
        assertTrue(host.contains("LivePlaybackHostSignals.setAutoPipOnLeave"))
        assertTrue(activity.contains("LivePlaybackHostSignals.autoPipOnLeave"))
        assertFalse(host.contains("MainActivity.isPipSupportedAndActive"))
    }
}
