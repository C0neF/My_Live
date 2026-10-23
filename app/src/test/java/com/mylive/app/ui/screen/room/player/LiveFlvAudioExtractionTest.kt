package com.mylive.app.ui.screen.room.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.common.DataReader
import java.io.ByteArrayOutputStream
import java.io.EOFException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the production factory and Media3 FLV reader, without any network or audio decoder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(UnstableApi::class)
class LiveFlvAudioExtractionTest {
    @Test
    fun fillOnlyPacketsDoNotBecomeAudioSamplesOrShiftRealSampleTimestamps() {
        val first = byteArrayOf(0x00, 0x10, 0x20)
        val second = byteArrayOf(0x00, 0x30, 0x40)
        val third = byteArrayOf(0x00, 0x50, 0x60)
        val input = aacFlv(listOf(
            0 to first, 0 to capturedFillOnlyPacket(),
            23 to second, 23 to capturedFillOnlyPacket(), 46 to third
        ))
        // Independent control: the unmodified Media3 reader queues all five packets.
        assertEquals(5, extract(input, DefaultExtractorsFactory()).samples.size)
        val output = extract(input)
        assertEquals("mp4a.40.2", output.format!!.codecs)

        assertEquals("No-audio AAC fill packets must never be queued to the decoder", 3, output.samples.size)
        assertEquals(listOf(0L, 23_000L, 46_000L), output.samples.map { it.timeUs })
        assertArrayEquals(first, output.samples[0].data)
        assertArrayEquals(second, output.samples[1].data)
        assertArrayEquals(third, output.samples[2].data)
        assertEquals(MimeTypes.AUDIO_AAC, output.format!!.sampleMimeType)
        assertEquals(44100, output.format!!.sampleRate)
        assertEquals(1, output.format!!.channelCount)
        assertEquals("mp4a.40.2", output.format!!.codecs)
    }

    @Test
    fun leadingFillFollowedByAChannelAndUnrecognizedPacketsRemainByteIdentical() {
        // A fill element alone is insufficient: the whole block must contain no channel data.
        // These are extractor fixtures, not assertions that malformed AAC decodes successfully.
        val packets = listOf(
            byteArrayOf(0xc0.toByte(), 0x00, 0x12), // zero-length FIL, followed by SCE
            byteArrayOf(0xde.toByte()), // truncated escaped fill length
            byteArrayOf(0xe0.toByte(), 0x12), // END with unexplained trailing data
            byteArrayOf(0x20, 0x10, 0x30) // CPE
        )
        val output = extract(aacFlv(packets.mapIndexed { index, bytes -> index * 23 to bytes }))
        assertEquals(packets.size, output.samples.size)
        packets.forEachIndexed { index, bytes -> assertArrayEquals(bytes, output.samples[index].data) }
    }

    @Test
    fun nonAacFlvAudioIsNotInterpretedAsAacSyntax() {
        val body = capturedFillOnlyPacket()
        val output = extract(flv(listOf(0 to (byteArrayOf(0x2f) + body))))
        assertEquals(MimeTypes.AUDIO_MPEG, output.format!!.sampleMimeType)
        assertEquals(1, output.samples.size)
        assertArrayEquals(body, output.samples.single().data)
    }

    @Test
    fun capturedFixtureIsCompletePaddingAndTruncatedCopiesAreNeverDropped() {
        val fixtures = listOf(
            Triple("/douyu-aac-fill-only.hex", 129, "2bec8861eb7777ef9c6419871aa297a29f777108235b9e2c6e7b4efde38db0b3"),
            Triple("/douyu-aac-fill-only-720p.hex", 137, "10d4f2215c4036568d90c586f69eb07a68b6a477bed627ca21b9b1a8e310c982")
        )
        for ((resource, expectedSize, expectedHash) in fixtures) {
            val packet = capturedFillOnlyPacket(resource)
            assertEquals(expectedSize, packet.size)
            assertEquals(expectedHash,
                java.security.MessageDigest.getInstance("SHA-256").digest(packet).joinToString("") { "%02x".format(it) })
            assertTrue(isAacPaddingOnlySample(packet, 0, packet.size))
            assertEquals(0, extract(aacFlv(listOf(0 to packet))).samples.size)
            for (size in 0 until packet.size) {
                assertFalse("$resource truncated length $size", isAacPaddingOnlySample(packet, 0, size))
            }
            assertFalse(isAacPaddingOnlySample(packet + byteArrayOf(0), 0, packet.size + 1))
            assertTrue(isAacPaddingOnlySample(byteArrayOf(0x12) + packet + byteArrayOf(0x34), 1, packet.size))
        }
    }

    @Test
    fun statefulExtensionsAndAudioChannelsAreNotDiscarded() {
        for (extension in listOf("1011", "1101", "1110", "1111")) {
            // FIL(count=1), extension type (DRC, SBR or unsupported), END.
            val packet = bits("1100001" + extension + "0000" + "111")
            assertFalse(isAacPaddingOnlySample(packet, 0, packet.size))
            assertArrayEquals(packet, extract(aacFlv(listOf(0 to packet))).samples.single().data)
        }
        val leadingFillAndChannel = bits("1100001" + "00000000" + "0000000000000000")
        assertFalse(isAacPaddingOnlySample(leadingFillAndChannel, 0, leadingFillAndChannel.size))
        assertFalse(isAacPaddingOnlySample(ByteArray(4097), 0, 4097))
        assertFalse(isAacPaddingOnlySample(byteArrayOf(0), Int.MAX_VALUE, 1))
    }

    @Test
    fun encryptedPaddingAndMultipartSamplesPassThroughWithTheirOriginalMetadata() {
        val packet = capturedFillOnlyPacket()
        val encryptedOutput = CapturedTrack()
        val encryptedFilter = AacPaddingFilteringTrackOutput(encryptedOutput)
        encryptedFilter.format(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setCodecs("mp4a.40.2").build())
        val crypto = TrackOutput.CryptoData(C.CRYPTO_MODE_AES_CTR, ByteArray(16), 0, 0)
        encryptedFilter.sampleData(ParsableByteArray(packet), packet.size)
        encryptedFilter.sampleMetadata(3000, C.BUFFER_FLAG_ENCRYPTED, packet.size, 0, crypto)
        assertArrayEquals(packet, encryptedOutput.samples.single().data)
        assertEquals(C.BUFFER_FLAG_ENCRYPTED, encryptedOutput.samples.single().flags)
        assertSame(crypto, encryptedOutput.samples.single().crypto)

        val multipartOutput = CapturedTrack()
        val multipartFilter = AacPaddingFilteringTrackOutput(multipartOutput)
        multipartFilter.format(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setCodecs("mp4a.40.2").build())
        val suffix = byteArrayOf(0x01, 0x02)
        multipartFilter.sampleData(ParsableByteArray(packet), packet.size)
        multipartFilter.sampleData(ParsableByteArray(suffix), suffix.size)
        multipartFilter.sampleMetadata(4000, 0, packet.size + suffix.size, 0, null)
        assertArrayEquals(packet + suffix, multipartOutput.samples.single().data)
        assertEquals(4000L, multipartOutput.samples.single().timeUs)
    }

    @Test
    fun nonLcAacDoesNotUseTheLcPaddingFilter() {
        val output = CapturedTrack()
        val filter = AacPaddingFilteringTrackOutput(output)
        filter.format(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setCodecs("mp4a.40.5").build())
        val packet = capturedFillOnlyPacket()
        filter.sampleData(ParsableByteArray(packet), packet.size)
        filter.sampleMetadata(0, 0, packet.size, 0, null)
        assertArrayEquals(packet, output.samples.single().data)
    }

    @Test
    fun completeClearPaddingIsNotForwardedToTheAudioTrack() {
        val output = CapturedTrack()
        val filter = AacPaddingFilteringTrackOutput(output)
        filter.format(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setCodecs("mp4a.40.2").build())
        val packet = capturedFillOnlyPacket()
        filter.sampleData(ParsableByteArray(packet), packet.size)
        filter.sampleMetadata(0, C.BUFFER_FLAG_KEY_FRAME, packet.size, 0, null)
        assertEquals(0, output.samples.size)
    }

    private fun extract(bytes: ByteArray, factory: ExtractorsFactory = LiveExtractorsFactory()): CapturedTrack {
        val uri = Uri.parse("https://example.invalid/fixture.flv")
        val source = ByteArrayDataSource(bytes)
        source.open(DataSpec(uri))
        val input = DefaultExtractorInput(source, 0L, bytes.size.toLong())
        val extractor = factory.createExtractors(uri, emptyMap()).first { candidate ->
            input.resetPeekPosition()
            candidate.sniff(input)
        }
        input.resetPeekPosition()
        val track = CapturedTrack()
        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput {
                assertEquals(C.TRACK_TYPE_AUDIO, type)
                return track
            }
            override fun endTracks() = Unit
            override fun seekMap(seekMap: SeekMap) = Unit
        })
        try {
            val position = PositionHolder()
            var reads = 0
            while (extractor.read(input, position) != Extractor.RESULT_END_OF_INPUT) {
                check(++reads < 1000) { "Extractor did not terminate" }
            }
        } finally {
            extractor.release()
            source.close()
        }
        return track
    }

    private fun aacFlv(samples: List<Pair<Int, ByteArray>>): ByteArray = flv(
        listOf(0 to byteArrayOf(0xaf.toByte(), 0x00, 0x12, 0x08)) +
            samples.map { (time, bytes) -> time to (byteArrayOf(0xaf.toByte(), 0x01) + bytes) }
    )

    private fun flv(tags: List<Pair<Int, ByteArray>>): ByteArray = ByteArrayOutputStream().apply {
        write(byteArrayOf(0x46, 0x4c, 0x56, 0x01, 0x04, 0x00, 0x00, 0x00, 0x09))
        write(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        tags.forEach { (timestamp, data) ->
            write(8)
            writeInt(data.size, 3)
            writeInt(timestamp and 0xffffff, 3)
            write(timestamp ushr 24)
            writeInt(0, 3)
            write(data)
            writeInt(data.size + 11, 4)
        }
    }.toByteArray()

    private fun ByteArrayOutputStream.writeInt(value: Int, size: Int) {
        for (i in size - 1 downTo 0) write(value ushr (i * 8) and 0xff)
    }

    private fun capturedFillOnlyPacket(resource: String = "/douyu-aac-fill-only.hex"): ByteArray =
        javaClass.getResourceAsStream(resource)!!.bufferedReader().use { reader ->
            reader.readText().trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

    private fun bits(value: String): ByteArray = value.padEnd((value.length + 7) / 8 * 8, '0')
        .chunked(8).map { it.toInt(2).toByte() }.toByteArray()

    private data class Sample(val timeUs: Long, val data: ByteArray, val flags: Int, val crypto: TrackOutput.CryptoData?)

    private class CapturedTrack : TrackOutput {
        var format: Format? = null
        val samples = mutableListOf<Sample>()
        private val bytes = ByteArrayOutputStream()

        override fun format(format: Format) { this.format = format }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            val buffer = ByteArray(length)
            val read = input.read(buffer, 0, length)
            if (read < 0) {
                if (!allowEndOfInput) throw EOFException()
                return C.RESULT_END_OF_INPUT
            }
            bytes.write(buffer, 0, read)
            return read
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            val buffer = ByteArray(length)
            data.readBytes(buffer, 0, length)
            bytes.write(buffer)
        }

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            val data = bytes.toByteArray()
            samples += Sample(timeUs, data.copyOfRange(data.size - offset - size, data.size - offset), flags, cryptoData)
        }
    }
}
