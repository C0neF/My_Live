package com.mylive.app.ui.screen.room.player

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlayerVolumeControlTest {
    @Test
    fun directVolumeGestureChangesPlayerGainAndState() {
        val controller = PlayerController(ApplicationProvider.getApplicationContext())
        try {
            controller.setVolumeDirect(0.25f)

            assertEquals(0.25f, controller.player?.volume ?: -1f, 0.001f)
            assertEquals(0.25f, controller.state.value.volume, 0.001f)
        } finally {
            controller.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun muteRestoresPreviousPlayerGain() {
        val controller = PlayerController(ApplicationProvider.getApplicationContext())
        try {
            controller.setVolumeDirect(0.6f)
            controller.toggleMute()
            assertEquals(0f, controller.player?.volume ?: -1f, 0.001f)

            controller.toggleMute()
            assertEquals(0.6f, controller.player?.volume ?: -1f, 0.001f)
        } finally {
            controller.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
