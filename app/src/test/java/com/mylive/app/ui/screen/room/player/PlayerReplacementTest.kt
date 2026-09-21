package com.mylive.app.ui.screen.room.player

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.service.PlaybackForegroundService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlayerReplacementTest {
    @Test fun decoderRebuildRebindsServiceAndNotificationActions() {
        val session = LivePlaybackSession.create(ApplicationProvider.getApplicationContext(), true, false, null)
        val controller = session.playerController
        val original = controller.player
        val generation = controller.state.value.playerGeneration
        PlaybackForegroundService.activePlayer = original
        val service = Robolectric.buildService(PlaybackForegroundService::class.java).create()
        try {
            PlayerController::class.java.getDeclaredMethod("retryCurrentUrlWithSoftwareDecoder")
                .apply { isAccessible = true }.invoke(controller)
            val replacement = controller.player
            assertNotSame(original, replacement)
            assertSame(replacement, PlaybackForegroundService.activePlayer)
            assertTrue(controller.state.value.playerGeneration > generation)
            service.get().onStartCommand(Intent().setAction(PlaybackForegroundService.ACTION_PAUSE), 0, 1)
            assertFalse(replacement!!.playWhenReady)
            service.destroy()
            assertNull(PlaybackForegroundService.activePlayer)
        } finally {
            if (PlaybackForegroundService.activePlayer != null) service.destroy()
            PlaybackForegroundService.activePlayer = null
            session.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
