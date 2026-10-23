package com.mylive.app.ui.screen.room.player

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.mylive.app.service.PlaybackForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** An outgoing room can be disposed after the incoming room has handed off its player. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaybackServiceOwnershipTest {
    @Test
    fun releasingAnOutgoingSessionDoesNotStopTheIncomingRoomsForegroundService() = withTwoSessions { old, current, context ->
        val previousStops = context.playbackStops

        old.release()

        assertEquals("Old session release must not stop the service now owned by the incoming room",
            previousStops, context.playbackStops)
        assertSame(current.playerController.player, PlaybackForegroundService.activePlayer)
    }

    @Test
    fun disposingAnOutgoingHostObserverDoesNotStopTheIncomingRoomsForegroundService() = withTwoSessions { old, current, context ->
        val previousStops = context.playbackStops

        // This is the public method used by LivePlaybackHost's lifecycle observer onDispose.
        old.stopForegroundService()

        assertEquals("Old host observer cleanup must not stop the current room's foreground service",
            previousStops, context.playbackStops)
        assertSame(current.playerController.player, PlaybackForegroundService.activePlayer)
    }

    @Test
    fun resumingAnOutgoingSessionDoesNotStopTheIncomingRoomsForegroundService() = withTwoSessions { old, current, context ->
        val previousStops = context.playbackStops

        old.onHostResume()

        assertEquals("An outgoing host resume must not stop the current room's foreground service",
            previousStops, context.playbackStops)
        assertSame(current.playerController.player, PlaybackForegroundService.activePlayer)
    }

    @Test
    fun releasingTheOwningSessionStillStopsItsForegroundService() = withTwoSessions { _, current, context ->
        val previousStops = context.playbackStops

        current.release()

        assertEquals("The owning session must still tear down its own service", previousStops + 1, context.playbackStops)
    }

    private fun withTwoSessions(test: (LivePlaybackSession, LivePlaybackSession, RecordingServiceContext) -> Unit) {
        val context = RecordingServiceContext(ApplicationProvider.getApplicationContext())
        val old = LivePlaybackSession.create(context, true, false, null)
        val current = LivePlaybackSession.create(context, true, false, null)
        old.playerController.resume()
        old.onHostPause(true, false, "outgoing-room", "anchor", "test")
        val service = Robolectric.buildService(PlaybackForegroundService::class.java).create()
        try {
            service.get().onStartCommand(Intent(context, PlaybackForegroundService::class.java), 0, 1)
            assertSame(old.playerController.player, PlaybackForegroundService.activePlayer)
            current.playerController.resume()
            current.onHostPause(true, false, "incoming-room", "anchor", "test")
            service.get().onStartCommand(Intent(context, PlaybackForegroundService::class.java), 0, 2)
            assertSame(current.playerController.player, PlaybackForegroundService.activePlayer)
            assertTrue(current.playerController.player!!.playWhenReady)

            test(old, current, context)
        } finally {
            old.release()
            current.release()
            service.destroy()
            PlaybackForegroundService.activePlayer = null
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    /** Records the public stopService side effect, while forwarding real service operations. */
    private class RecordingServiceContext(base: Context) : ContextWrapper(base) {
        var playbackStops = 0
            private set
        override fun getApplicationContext(): Context = this
        override fun stopService(service: Intent): Boolean {
            if (service.component?.className == PlaybackForegroundService::class.java.name) playbackStops++
            return super.stopService(service)
        }
    }
}
