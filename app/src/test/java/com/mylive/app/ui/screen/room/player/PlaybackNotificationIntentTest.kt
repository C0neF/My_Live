package com.mylive.app.ui.screen.room.player

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.service.PlaybackForegroundService
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Uses real Session, Controller, ExoPlayer and Service; only Android delivery order is controlled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaybackNotificationIntentTest {
    @Test
    fun notificationStopInvalidatesAReplacementWaitingForServicePromotion() = withPlayback { session, command, first, replacement ->
        session.play(listOf(replacement), origin = PlaybackRequestOrigin.SourceRecovery)
        assertEquals("The replacement must still be waiting for foreground promotion", first, session.state.value.currentUrl)

        command(PlaybackForegroundService.ACTION_STOP)
        assertFalse("The real service must pause the real player immediately", session.playerController.player!!.playWhenReady)
        session.onHostResume()

        assertFalse("Returning to the room must preserve the notification's newer Stop request",
            session.playerController.player!!.playWhenReady)
    }

    @Test
    fun notificationPauseInvalidatesAReplacementWaitingForServicePromotion() = withPlayback { session, command, first, replacement ->
        session.play(listOf(replacement), origin = PlaybackRequestOrigin.SourceRecovery)
        assertEquals(first, session.state.value.currentUrl)

        command(PlaybackForegroundService.ACTION_PAUSE)
        assertFalse(session.playerController.player!!.playWhenReady)
        // A subsequently delivered normal start must not restore the cancelled pending callback.
        command(null)
        assertEquals(first, session.state.value.currentUrl)
        session.onHostResume()

        assertFalse("Returning to the room must preserve the notification's newer Pause request",
            session.playerController.player!!.playWhenReady)
    }

    @Test
    fun notificationStopWithoutADeferredSourceRemainsPausedOnHostResume() = withPlayback { session, command, _, _ ->
        command(PlaybackForegroundService.ACTION_STOP)

        session.onHostResume()

        assertFalse("A completed handoff must also preserve Stop", session.playerController.player!!.playWhenReady)
    }

    @Test
    fun notificationPauseWithoutADeferredSourceRemainsPausedOnHostResume() = withPlayback { session, command, _, _ ->
        command(PlaybackForegroundService.ACTION_PAUSE)

        session.onHostResume()

        assertFalse("A completed handoff must also preserve Pause", session.playerController.player!!.playWhenReady)
    }

    @Test
    fun aReplacementWithoutANewerPauseStillStartsOnHostResume() = withPlayback { session, _, _, replacement ->
        session.play(listOf(replacement), origin = PlaybackRequestOrigin.SourceRecovery)

        session.onHostResume()

        assertEquals(replacement, session.state.value.currentUrl)
        assertTrue("The original playback intent must remain valid when the user did not pause",
            session.playerController.player!!.playWhenReady)
    }

    @Test
    fun anExplicitSourceSelectionAfterNotificationStopStillStarts() = withPlayback { session, command, _, replacement ->
        command(PlaybackForegroundService.ACTION_STOP)
        session.onHostResume()

        session.play(listOf(replacement), origin = PlaybackRequestOrigin.UserSelection)

        assertEquals(replacement, session.state.value.currentUrl)
        assertTrue("A later explicit source selection supplies new playback intent",
            session.playerController.player!!.playWhenReady)
    }

    private fun withPlayback(test: (LivePlaybackSession, (String?) -> Unit, String, String) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                    .setHeader("Content-Type", "audio/wav")
                    .setBody(Buffer().write(silentWav()))
            }
            start(InetAddress.getByName("127.0.0.1"), 0)
        }
        val first = server.url("/first.wav").toString()
        val replacement = server.url("/replacement.wav").toString()
        val session = LivePlaybackSession.create(context, true, false, null)
        val service = Robolectric.buildService(PlaybackForegroundService::class.java)
        var serviceCreated = false
        var startId = 0
        try {
            session.playerController.player!!.volume = 0f
            session.play(listOf(first), origin = PlaybackRequestOrigin.UserSelection)
            session.onHostPause(true, false, "synthetic-room", "synthetic-anchor", "test")
            service.create()
            serviceCreated = true
            val command: (String?) -> Unit = { action ->
                service.get().onStartCommand(Intent(context, PlaybackForegroundService::class.java).apply {
                    this.action = action
                }, 0, ++startId)
            }
            command(null)
            assertTrue("The first handoff must have an actual foreground notification",
                shadowOf(service.get()).lastForegroundNotification != null)
            assertTrue(session.playerController.player!!.playWhenReady)

            test(session, command, first, replacement)
        } finally {
            session.release()
            if (serviceCreated) service.destroy()
            PlaybackForegroundService.activePlayer = null
            shadowOf(Looper.getMainLooper()).idle()
            server.shutdown()
        }
    }

    private fun silentWav(): ByteArray {
        val bytes = 8_000 * 2 * 20
        return ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + bytes)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1); putShort(1); putInt(8_000); putInt(16_000)
            putShort(2); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)); putInt(bytes)
        }.array()
    }
}
