package com.mylive.app.ui.screen.room.player

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerVideoSizeAnalyticsTest {
    @Test fun oldSourceEventsCannotChangeCurrentVideoOrientation() = withController { controller, listener ->
        controller.play(listOf("https://example.invalid/portrait.flv"))
        listener.onVideoSizeChanged(event(1), VideoSize(720, 1280))
        assertEquals(9f / 16f, controller.state.value.videoAspectRatio!!, 0.0001f)
        controller.play(listOf("https://example.invalid/landscape.flv"))
        listener.onVideoSizeChanged(event(2), VideoSize(1920, 1080))
        listener.onVideoSizeChanged(event(1), VideoSize(720, 1280))
        assertEquals(16f / 9f, controller.state.value.videoAspectRatio!!, 0.0001f)
    }

    @Test fun aQualityChangeAndRendererDisableRetainGeometryUntilTheNextValidSize() =
        withController { controller, listener ->
            controller.play(listOf("https://example.invalid/live.flv"))
            listener.onVideoSizeChanged(event(1), VideoSize(720, 1280))
            controller.play(listOf("https://example.invalid/quality.flv"))
            listener.onVideoSizeChanged(event(2), VideoSize.UNKNOWN)
            assertEquals(9f / 16f, controller.state.value.videoAspectRatio!!, 0.0001f)
            listener.onVideoSizeChanged(event(2), VideoSize(1080, 1920))
            assertEquals(9f / 16f, controller.state.value.videoAspectRatio!!, 0.0001f)
        }

    @Test fun leavingOrLoadingAnotherRoomClearsGeometryAndRejectsLateCallbacks() =
        withController { controller, listener ->
            controller.play(listOf("https://example.invalid/live.flv"))
            listener.onVideoSizeChanged(event(1), VideoSize(720, 1280))
            controller.beginRoomLoad(1000)
            listener.onVideoSizeChanged(event(1), VideoSize(720, 1280))
            assertNull(controller.state.value.videoAspectRatio)
            controller.play(listOf("https://example.invalid/next.flv"))
            listener.onVideoSizeChanged(event(2), VideoSize(1080, 1920))
            controller.stop()
            listener.onVideoSizeChanged(event(2), VideoSize(1080, 1920))
            assertNull(controller.state.value.videoAspectRatio)
        }

    private fun withController(test: (PlayerController, AnalyticsListener) -> Unit) {
        val controller = PlayerController(ApplicationProvider.getApplicationContext())
        controller.player?.release()
        PlayerController::class.java.getDeclaredField("player").apply { isAccessible = true }
            .set(controller, mockk<ExoPlayer>(relaxed = true))
        val listener = PlayerController::class.java.getDeclaredField("analyticsListener")
            .apply { isAccessible = true }.get(controller) as AnalyticsListener
        try { test(controller, listener) }
        finally { controller.release(); shadowOf(Looper.getMainLooper()).idle() }
    }

    private fun event(token: Long): AnalyticsListener.EventTime {
        val item = MediaItem.Builder().setUri("https://example.invalid/live.flv").setTag(token).build()
        val timeline = SinglePeriodTimeline(10_000_000, true, false, false, null, item)
        return AnalyticsListener.EventTime(0, timeline, 0, null, 0, timeline, 0, null, 0, 0)
    }
}
