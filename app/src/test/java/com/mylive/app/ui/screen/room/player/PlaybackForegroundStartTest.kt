package com.mylive.app.ui.screen.room.player

import android.content.Context
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
class PlaybackForegroundStartTest {
    @Test fun pendingSourceStartsOnlyAfterForegroundServicePromotion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = LivePlaybackSession.create(context, true, false, null)
        try {
            session.onHostPause(true, false, "room", "anchor", "fake")
            session.play(listOf("https://example.test/stream.flv"), null, 0, PlaybackRequestOrigin.UserSelection)
            assertNull(session.state.value.currentUrl)
            val service = Robolectric.buildService(PlaybackForegroundService::class.java).create()
            try {
                service.get().onStartCommand(Intent(context, PlaybackForegroundService::class.java), 0, 1)
                assertEquals("https://example.test/stream.flv", session.state.value.currentUrl)
                assertTrue(session.playerController.player!!.playWhenReady)
            } finally { service.destroy() }
        } finally {
            session.release()
            PlaybackForegroundService.activePlayer = null
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test fun stoppedSessionCannotBeStartedByALateServiceCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = LivePlaybackSession.create(context, true, false, null)
        try {
            session.onHostPause(true, false, "room", "anchor", "fake")
            session.play(listOf("https://example.test/stream.flv"), null, 0, PlaybackRequestOrigin.UserSelection)
            session.stop()
            val service = Robolectric.buildService(PlaybackForegroundService::class.java).create()
            try {
                service.get().onStartCommand(Intent(context, PlaybackForegroundService::class.java), 0, 1)
                assertNull(session.state.value.currentUrl)
                assertFalse(session.playerController.player!!.playWhenReady)
            } finally { service.destroy() }
        } finally {
            session.release()
            PlaybackForegroundService.activePlayer = null
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
