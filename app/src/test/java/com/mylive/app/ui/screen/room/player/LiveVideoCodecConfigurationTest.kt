package com.mylive.app.ui.screen.room.player

import android.app.Application
import android.media.MediaFormat
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class LiveVideoCodecConfigurationTest {
    @Test fun missingFrameRateConfigures2kAnd4kWithoutChangingResolutionOrTheTrackFormat() {
        for ((width, height, fps) in listOf(
            Triple(2560, 1440, 60f), Triple(2560, 1440, 120f),
            Triple(3840, 2160, 30f), Triple(3840, 2160, 60f), Triple(3840, 2160, 120f)
        )) {
            val configuration = configuration(width = width, height = height)
            val adapter = mockk<MediaCodecAdapter>()
            val factory = LiveVideoCodecAdapterFactory(MediaCodecAdapter.Factory { configured ->
                assertTrue("FLV video must carry its resolved frame rate to the decoder",
                    configured.mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
                assertEquals(fps, configured.mediaFormat.getFloat(MediaFormat.KEY_FRAME_RATE), 0f)
                assertEquals(fps, configured.mediaFormat.getFloat(MediaFormat.KEY_OPERATING_RATE), 0f)
                assertEquals(width, configured.mediaFormat.getInteger(MediaFormat.KEY_WIDTH))
                assertEquals(height, configured.mediaFormat.getInteger(MediaFormat.KEY_HEIGHT))
                adapter
            }) { fps }

            assertSame(adapter, factory.createAdapter(configuration))
            assertEquals(Format.NO_VALUE.toFloat(), configuration.format.frameRate, 0f)
        }
    }

    @Test
    @Config(sdk = [28])
    fun olderAndroidKeepsTheOriginalConfigurationWhenOptionalKeysCannotBeSafelyRemoved() {
        val configuration = configuration(width = 3840, height = 2160)
        val factory = LiveVideoCodecAdapterFactory(MediaCodecAdapter.Factory { configured ->
            assertFalse(configured.mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
            assertFalse(configured.mediaFormat.containsKey(MediaFormat.KEY_OPERATING_RATE))
            mockk<MediaCodecAdapter>()
        }) { 60f }

        factory.createAdapter(configuration)
    }

    @Test fun containerFrameRateAndExistingCodecSettingsTakePriority() {
        val configuration = configuration(frameRate = 30f)
        configuration.mediaFormat.setFloat(MediaFormat.KEY_FRAME_RATE, 30f)
        configuration.mediaFormat.setFloat(MediaFormat.KEY_OPERATING_RATE, 45f)
        val adapter = mockk<MediaCodecAdapter>()
        val factory = LiveVideoCodecAdapterFactory(MediaCodecAdapter.Factory { adapter }) { 120f }

        factory.createAdapter(configuration)

        assertEquals(30f, configuration.mediaFormat.getFloat(MediaFormat.KEY_FRAME_RATE), 0f)
        assertEquals(45f, configuration.mediaFormat.getFloat(MediaFormat.KEY_OPERATING_RATE), 0f)
    }

    @Test fun unknownInvalidOrAudioHintsLeaveTheOriginalConfigurationAlone() {
        val adapter = mockk<MediaCodecAdapter>()
        for (fps in listOf(null, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 1000f)) {
            val configuration = configuration()
            LiveVideoCodecAdapterFactory(MediaCodecAdapter.Factory { adapter }) { fps }
                .createAdapter(configuration)
            assertFalse(configuration.mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
            assertFalse(configuration.mediaFormat.containsKey(MediaFormat.KEY_OPERATING_RATE))
        }
        val audio = configuration(mime = MimeTypes.AUDIO_AAC)
        LiveVideoCodecAdapterFactory(MediaCodecAdapter.Factory { adapter }) { 120f }
            .createAdapter(audio)
        assertFalse(audio.mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
        assertFalse(audio.mediaFormat.containsKey(MediaFormat.KEY_OPERATING_RATE))
    }

    @Test
    @Config(sdk = [28])
    fun anExistingIntegerFrameRateIsReportedAndNeverReplacedByAHint() {
        val configuration = configuration()
        configuration.mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        var configuredRate: Float? = null
        val factory = LiveVideoCodecAdapterFactory(
            MediaCodecAdapter.Factory { mockk<MediaCodecAdapter>() },
            onVideoConfigured = { configuredRate = it }
        ) { 120f }

        factory.createAdapter(configuration)

        assertEquals(30, configuration.mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE))
        assertFalse(configuration.mediaFormat.containsKey(MediaFormat.KEY_OPERATING_RATE))
        assertEquals(30f, configuredRate)
    }

    @Test fun rejectionRemovesOnlyAddedKeysAndReportsTheActualFallbackConfiguration() {
        val configuration = configuration()
        configuration.mediaFormat.setFloat(MediaFormat.KEY_OPERATING_RATE, 45f)
        var attempts = 0
        var configuredRate: Float? = 120f
        val factory = LiveVideoCodecAdapterFactory(MediaCodecAdapter.Factory { configured ->
            attempts++
            assertEquals(45f, configured.mediaFormat.getFloat(MediaFormat.KEY_OPERATING_RATE), 0f)
            if (attempts == 1) throw IllegalArgumentException("Unsupported frame rate")
            assertFalse(configured.mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
            mockk<MediaCodecAdapter>()
        }, onVideoConfigured = { configuredRate = it }) { 120f }

        factory.createAdapter(configuration)

        assertEquals(2, attempts)
        assertNull(configuredRate)
    }

    @Test fun unrelatedFailuresAndFailedFallbacksPropagateWithoutRepeatedRetries() {
        for ((error, expectedAttempts) in listOf(java.io.IOException("unavailable") to 1,
            IllegalArgumentException("rejected") to 2)) {
            var attempts = 0
            val factory = LiveVideoCodecAdapterFactory(MediaCodecAdapter.Factory {
                attempts++
                throw error
            }) { 120f }

            assertSame(error, runCatching { factory.createAdapter(configuration()) }.exceptionOrNull())
            assertEquals(expectedAttempts, attempts)
        }
    }

    @Test fun aCodecThatRejectsTheHintRetriesItsOriginalConfigurationOnce() {
        val configuration = configuration()
        val adapter = mockk<MediaCodecAdapter>()
        var attempts = 0
        val factory = LiveVideoCodecAdapterFactory(MediaCodecAdapter.Factory { configured ->
            attempts++
            if (attempts == 1) {
                assertTrue(configured.mediaFormat.containsKey(MediaFormat.KEY_OPERATING_RATE))
                throw IllegalArgumentException("Unsupported operating rate")
            }
            assertFalse(configured.mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
            assertFalse(configured.mediaFormat.containsKey(MediaFormat.KEY_OPERATING_RATE))
            adapter
        }) { 120f }

        assertSame(adapter, factory.createAdapter(configuration))
        assertEquals(2, attempts)
    }

    private fun configuration(
        frameRate: Float = Format.NO_VALUE.toFloat(),
        mime: String = MimeTypes.VIDEO_H264,
        width: Int = 2560,
        height: Int = 1440
    ): MediaCodecAdapter.Configuration {
        val format = Format.Builder().setSampleMimeType(mime).setWidth(width).setHeight(height)
            .setFrameRate(frameRate).build()
        val mediaFormat = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mime)
            setInteger(MediaFormat.KEY_WIDTH, width)
            setInteger(MediaFormat.KEY_HEIGHT, height)
        }
        val codec = MediaCodecInfo.newInstance("test.decoder", mime, mime, null, true, false, true, false, false)
        return MediaCodecAdapter.Configuration.createForVideoDecoding(codec, mediaFormat, format, null, null)
    }
}
