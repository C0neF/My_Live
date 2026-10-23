package com.mylive.app.ui.screen.room.player

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import timber.log.Timber

@androidx.annotation.OptIn(UnstableApi::class)
internal class LiveVideoCodecAdapterFactory(
    private val delegate: MediaCodecAdapter.Factory,
    private val onVideoConfigured: (Float?) -> Unit = {},
    private val frameRateHint: () -> Float?
) : MediaCodecAdapter.Factory {
    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter {
        val video = MimeTypes.isVideo(configuration.format.sampleMimeType)
        val hint = frameRateHint()?.takeIf { it.isFinite() && it in 1f..240f }
        val mediaFormat = configuration.mediaFormat
        val addedKeys = mutableListOf<String>()
        // FLV does not populate Format.frameRate in Media3. Supply decoder resource hints only;
        // keep the track Format untouched so hints cannot alter codec/track selection or timestamps.
        // Older Android releases cannot remove optional MediaFormat keys for a safe retry.
        if (Build.VERSION.SDK_INT >= 29 && video && configuration.format.frameRate <= 0f && hint != null &&
            !mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            for (key in listOf(MediaFormat.KEY_FRAME_RATE, MediaFormat.KEY_OPERATING_RATE)) {
                if (!mediaFormat.containsKey(key)) {
                    mediaFormat.setFloat(key, hint)
                    addedKeys += key
                }
            }
        }
        val adapter = try {
            delegate.createAdapter(configuration)
        } catch (error: Exception) {
            if (Build.VERSION.SDK_INT < 29 || addedKeys.isEmpty() ||
                (error !is IllegalArgumentException && error !is MediaCodec.CodecException)) throw error
            // A vendor may reject an optional rate. Retry the same codec with its original
            // configuration before the normal decoder-fallback policy gets involved.
            addedKeys.forEach(mediaFormat::removeKey)
            Timber.w("Video decoder rejected frame-rate hints; retrying its default configuration")
            delegate.createAdapter(configuration)
        }
        if (video) {
            val configuredRate = if (mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                runCatching { mediaFormat.getFloat(MediaFormat.KEY_FRAME_RATE) }
                    .recoverCatching { mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                    .getOrNull()
                    ?.takeIf { it.isFinite() && it > 0f }
            } else null
            onVideoConfigured(configuredRate)
        }
        return adapter
    }
}
