package com.mylive.app.ui.screen.room.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.flv.FlvExtractor

/** Shared progressive-stream extractor configuration. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class LiveExtractorsFactory : ExtractorsFactory {
    private val delegate = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)

    override fun createExtractors(): Array<Extractor> = delegate.createExtractors().withFlvAudioFilter()

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>
    ): Array<Extractor> = delegate.createExtractors(uri, responseHeaders).withFlvAudioFilter()

    private fun Array<Extractor>.withFlvAudioFilter(): Array<Extractor> = map { extractor ->
        // Media3 also wraps FLV in SubtitleTranscodingExtractor. Preserve that wrapper and
        // inspect its underlying implementation so the filter is installed on real playback.
        if (extractor.underlyingImplementation is FlvExtractor) PaddingFilteringFlvExtractor(extractor) else extractor
    }.toTypedArray()
}

@androidx.annotation.OptIn(UnstableApi::class)
private class PaddingFilteringFlvExtractor(private val delegate: Extractor) : Extractor by delegate {
    private val audioOutputs = mutableListOf<AacPaddingFilteringTrackOutput>()

    override fun init(output: ExtractorOutput) {
        audioOutputs.clear()
        delegate.init(object : ExtractorOutput by output {
            override fun track(id: Int, type: Int): TrackOutput {
                val track = output.track(id, type)
                return if (type == C.TRACK_TYPE_AUDIO) {
                    AacPaddingFilteringTrackOutput(track).also(audioOutputs::add)
                } else track
            }
        })
    }

    override fun seek(position: Long, timeUs: Long) {
        audioOutputs.forEach { it.resetForSeek() }
        delegate.seek(position, timeUs)
    }
}

/**
 * Some live FLV sources insert AAC EXT_FILL-only blocks at the preceding audio timestamp.
 * They carry no channel samples. Queuing them as 1024-sample frames accumulates audio drift.
 * Only complete, unencrypted AAC-LC padding is removed; every other packet passes unchanged.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class AacPaddingFilteringTrackOutput(private val delegate: TrackOutput) : TrackOutput {
    private var aacLc = false
    private var sampleDataStarted = false
    private var pendingPadding: ByteArray? = null

    override fun format(format: Format) {
        flushPending()
        aacLc = format.sampleMimeType == MimeTypes.AUDIO_AAC && format.codecs == "mp4a.40.2"
        delegate.format(format)
    }

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
        flushPending()
        sampleDataStarted = true
        return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart)
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        flushPending()
        val canFilter = aacLc && !sampleDataStarted && sampleDataPart == TrackOutput.SAMPLE_DATA_PART_MAIN
        sampleDataStarted = true
        if (canFilter && isAacPaddingOnlySample(data.data, data.position, length)) {
            // FLV submits a complete raw AAC block followed by metadata (offset 0). Defer only
            // the small padding candidate until metadata confirms that boundary and encryption.
            pendingPadding = data.data.copyOfRange(data.position, data.position + length)
            data.skipBytes(length)
        } else {
            delegate.sampleData(data, length, sampleDataPart)
        }
    }

    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
        val padding = pendingPadding
        val drop = padding != null && size == padding.size && offset == 0 && cryptoData == null &&
            flags and C.BUFFER_FLAG_ENCRYPTED == 0
        if (drop) pendingPadding = null else flushPending()
        sampleDataStarted = false
        if (!drop) delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData)
    }

    fun resetForSeek() {
        pendingPadding = null
        sampleDataStarted = false
    }

    private fun flushPending() {
        val padding = pendingPadding ?: return
        pendingPadding = null
        delegate.sampleData(ParsableByteArray(padding), padding.size, TrackOutput.SAMPLE_DATA_PART_MAIN)
    }
}

/** Recognizes only FIL(EXT_FILL)* + END + byte alignment, with no channel or stateful extension. */
internal fun isAacPaddingOnlySample(data: ByteArray, offset: Int, length: Int): Boolean {
    if (offset < 0 || length !in 1..4096 || offset > data.size - length) return false
    var bit = 0
    fun read(count: Int): Int {
        if (count > length * 8 - bit) return -1
        var value = 0
        repeat(count) {
            value = (value shl 1) or ((data[offset + (bit ushr 3)].toInt() ushr (7 - (bit and 7))) and 1)
            bit++
        }
        return value
    }
    var hasFill = false
    while (true) {
        when (read(3)) {
            6 -> {
                var count = read(4)
                if (count < 0) return false
                if (count == 15) {
                    val escape = read(8)
                    if (escape < 0) return false
                    count += escape - 1
                }
                if (count * 8 > length * 8 - bit) return false
                if (count > 0) {
                    // EXT_FILL is inert padding. Preserve SBR, DRC and unknown extension types.
                    if (read(4) != 0) return false
                    bit += count * 8 - 4
                }
                hasFill = true
            }
            7 -> {
                val remaining = length * 8 - bit
                return hasFill && remaining <= 7 && read(remaining) == 0
            }
            else -> return false
        }
    }
}
