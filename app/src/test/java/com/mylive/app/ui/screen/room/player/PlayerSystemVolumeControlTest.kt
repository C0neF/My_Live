package com.mylive.app.ui.screen.room.player

import android.content.Context
import android.media.AudioManager
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
class PlayerSystemVolumeControlTest {
    @Test
    fun systemGestureChangesMediaStreamWithoutChangingPlayerGain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = PlayerController(context)
        try {
            controller.setVolumeDirect(0.6f)
            controller.setSystemVolumeDirect(0.5f)

            assertEquals(0.6f, controller.player?.volume ?: -1f, 0.001f)
            assertEquals(0.6f, controller.state.value.volume, 0.001f)

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            assertEquals(
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume,
                controller.currentSystemMediaVolume(),
                0.001f
            )
        } finally {
            controller.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
