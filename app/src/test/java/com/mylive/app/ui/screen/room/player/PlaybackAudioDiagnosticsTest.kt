package com.mylive.app.ui.screen.room.player

import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.test.core.app.ApplicationProvider
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Tests diagnostic event retention; these do not establish a cause for audible crackling. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackAudioDiagnosticsTest {
    @Test
    fun snapshotShowsTheCurrentAudioFormatAndDecoder() = withPlayer { controller, listener, _ ->
        listener.onAudioInputFormatChanged(event(1), Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC).setCodecs("mp4a.40.2")
            .setSampleRate(44100).setChannelCount(1).build(), null)
        listener.onAudioDecoderInitialized(event(1), "c2.android.aac.decoder", 0, 3)

        val snapshot = controller.playbackDiagnostics("test")
        assertEquals("44100 Hz", snapshot.valueFor("音频采样率"))
        assertEquals("1", snapshot.valueFor("音频声道"))
        assertEquals("mp4a.40.2", snapshot.valueFor("音频编码"))
        assertEquals("c2.android.aac.decoder", snapshot.valueFor("音频解码器"))
    }

    @Test
    fun timestampErrorsRemainVisibleWithoutTriggeringPlaybackRecovery() = withPlayer { controller, listener, player ->
        clearMocks(player, answers = false)
        val pauseGeneration = controller.playbackPauseGeneration
        listener.onAudioSinkError(event(1), AudioSink.UnexpectedDiscontinuityException(
            1_000_045_533_000L, 1_000_045_742_322L))
        listener.onAudioSinkError(event(1), IllegalStateException("https://example.invalid/?token=private"))

        val snapshot = controller.playbackDiagnostics("test")
        assertEquals("2", snapshot.valueFor("音频输出异常次数"))
        assertEquals("1", snapshot.valueFor("音频时间戳跳变次数"))
        assertEquals("-209.32 ms", snapshot.valueFor("最近音频时间戳偏差"))
        assertFalse(snapshot.asText().contains("private"))
        assertEquals(pauseGeneration, controller.playbackPauseGeneration)
        assertNull(controller.state.value.error)
        verify(exactly = 0) { player.stop() }
        verify(exactly = 0) { player.playWhenReady = any() }
    }

    @Test
    fun underrunsAreCountedPerSourceAndOldSourceEventsAreIgnored() = withPlayer { controller, listener, _ ->
        listener.onAudioUnderrun(event(1), 11025, 250, 50)
        listener.onAudioUnderrun(event(1), 11025, 250, 51)
        assertEquals("2", controller.playbackDiagnostics("first").valueFor("音频缓冲欠载次数"))

        controller.play(listOf("https://example.invalid/second.flv"))
        listener.onAudioUnderrun(event(1), 11025, 250, 52)
        listener.onAudioSinkError(event(1), AudioSink.UnexpectedDiscontinuityException(0, 300_000))
        assertEquals("0", controller.playbackDiagnostics("second").valueFor("音频缓冲欠载次数"))
        assertEquals("0", controller.playbackDiagnostics("second").valueFor("音频时间戳跳变次数"))

        listener.onAudioUnderrun(event(2), 11025, 250, 53)
        assertEquals("1", controller.playbackDiagnostics("second").valueFor("音频缓冲欠载次数"))
        controller.stop()
        listener.onAudioUnderrun(event(2), 11025, 250, 54)
        assertEquals("1", controller.playbackDiagnostics("stopped").valueFor("音频缓冲欠载次数"))
    }

    @Test
    fun missingAudioFactsAreExplicitAndNeverGuessedFromVideo() = withPlayer { controller, _, _ ->
        val snapshot = controller.playbackDiagnostics("test")
        assertEquals("未知", snapshot.valueFor("音频采样率"))
        assertEquals("未知", snapshot.valueFor("音频声道"))
        assertEquals("0", snapshot.valueFor("音频时间戳跳变次数"))
        assertEquals("无", snapshot.valueFor("最近音频时间戳偏差"))
    }

    private fun withPlayer(action: (PlayerController, AnalyticsListener, ExoPlayer) -> Unit) {
        val controller = PlayerController(ApplicationProvider.getApplicationContext())
        controller.player?.release()
        val player = mockk<ExoPlayer>(relaxed = true)
        PlayerController::class.java.getDeclaredField("player").apply { isAccessible = true }.set(controller, player)
        try {
            controller.play(listOf("https://example.invalid/first.flv"))
            val listener = PlayerController::class.java.getDeclaredField("analyticsListener")
                .apply { isAccessible = true }.get(controller) as AnalyticsListener
            action(controller, listener, player)
        } finally {
            controller.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun event(token: Long): AnalyticsListener.EventTime {
        val item = MediaItem.Builder().setUri("https://example.invalid/live.flv").setTag(token).build()
        val timeline = SinglePeriodTimeline(10_000_000, true, false, false, null, item)
        return AnalyticsListener.EventTime(0, timeline, 0, null, 0, timeline, 0, null, 0, 0)
    }
}
