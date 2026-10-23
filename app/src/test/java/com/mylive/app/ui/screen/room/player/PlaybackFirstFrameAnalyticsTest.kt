package com.mylive.app.ui.screen.room.player

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackFirstFrameAnalyticsTest {
    @Test fun onlyAFirstFrameFromTheCurrentMediaSetsTheRenderedMarker() {
        val controller = PlayerController(ApplicationProvider.getApplicationContext())
        controller.player?.release()
        PlayerController::class.java.getDeclaredField("player").apply { isAccessible = true }
            .set(controller, mockk<ExoPlayer>(relaxed = true))
        try {
            controller.beginRoomLoad(0)
            controller.play(listOf("https://example.invalid/live.flv"))
            val listener = PlayerController::class.java.getDeclaredField("analyticsListener")
                .apply { isAccessible = true }.get(controller) as AnalyticsListener
            listener.onRenderedFirstFrame(event(0), Any(), 1200)
            assertFalse(controller.state.value.firstFrameRendered)
            listener.onRenderedFirstFrame(event(1), Any(), 1500)
            assertTrue(controller.state.value.firstFrameRendered)
            assertEquals("1500 ms", controller.playbackDiagnostics("test").valueFor("进房首帧（含请求）"))
            controller.stop()
            listener.onRenderedFirstFrame(event(1), Any(), 1700)
            assertFalse(controller.state.value.firstFrameRendered)
        } finally { controller.release(); shadowOf(Looper.getMainLooper()).idle() }
    }

    private fun event(token: Long): AnalyticsListener.EventTime {
        val item = MediaItem.Builder().setUri("https://example.invalid/live.flv").setTag(token).build()
        val timeline = SinglePeriodTimeline(10_000_000, true, false, false, null, item)
        return AnalyticsListener.EventTime(0, timeline, 0, null, 0, timeline, 0, null, 0, 0)
    }
}
