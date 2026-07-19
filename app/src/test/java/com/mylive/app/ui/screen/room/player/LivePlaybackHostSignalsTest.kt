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
    fun mainActivityAndSessionUseHostSignalsNotBareStaticMutationInSession() {
        val session = File("src/main/java/com/mylive/app/ui/screen/room/player/LivePlaybackSession.kt")
            .readText()
        val activity = File("src/main/java/com/mylive/app/MainActivity.kt").readText()
        assertTrue(session.contains("LivePlaybackHostSignals.setAutoPipOnLeave"))
        assertTrue(activity.contains("LivePlaybackHostSignals.autoPipOnLeave"))
        assertFalse(session.contains("MainActivity.isPipSupportedAndActive"))
    }
}
