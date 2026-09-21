package com.mylive.app.ui.screen.room.player

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlayerAudioFocusTest {
    @Test fun playbackRequestsFocusAndRespondsToLossAndGain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        val controller = PlayerController(context)
        try {
            // No network or decoder is needed to exercise ExoPlayer's audio-focus policy.
            controller.player!!.prepare()
            controller.resume()
            val focus = audio.lastAudioFocusRequest
            assertNotNull(focus)
            assertEquals(AudioManager.AUDIOFOCUS_GAIN, focus.durationHint)
            focus.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(controller.player!!.playWhenReady)
            assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
                controller.player!!.playbackSuppressionReason)
            focus.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_NONE, controller.player!!.playbackSuppressionReason)
            focus.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(controller.player!!.playWhenReady)
        } finally {
            controller.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
