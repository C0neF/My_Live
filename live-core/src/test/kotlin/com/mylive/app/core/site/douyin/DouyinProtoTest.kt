package com.mylive.app.core.site.douyin

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Regression tests for [DouyinProto] protobuf decoding and bounded decompression.
 *
 * Previously [DouyinProto] only understood wire types 0 (varint) and 2 (length-delimited) and
 * THREW on anything else. A single 32-bit field (wire type 5 — e.g. a float `scalingRate` inside
 * an inline emoji image) therefore aborted decoding and silently dropped the entire danmaku.
 */
class DouyinProtoTest {

    /** Build a raw wire-type-5 (32-bit fixed) field. Field numbers < 16 use a single tag byte. */
    private fun fixed32Field(fieldNumber: Int, bytes: ByteArray): ByteArray {
        require(bytes.size == 4)
        return byteArrayOf(((fieldNumber shl 3) or 5).toByte()) + bytes
    }

    /** Build a raw wire-type-1 (64-bit fixed) field. */
    private fun fixed64Field(fieldNumber: Int, bytes: ByteArray): ByteArray {
        require(bytes.size == 8)
        return byteArrayOf(((fieldNumber shl 3) or 1).toByte()) + bytes
    }

    private fun gzipCompress(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    @Test
    fun decodesContentDespiteUnknownFixed32Field() {
        val bytes = DouyinProto.encodeStringField(3, "你好世界") +
            fixed32Field(10, byteArrayOf(0, 0, 0x80.toByte(), 0x3F))
        assertEquals("你好世界", DouyinProto.decodeChatMessage(bytes).content)
    }

    @Test
    fun decodesContentDespiteUnknownFixed64Field() {
        val bytes = DouyinProto.encodeStringField(3, "abc") + fixed64Field(11, ByteArray(8))
        assertEquals("abc", DouyinProto.decodeChatMessage(bytes).content)
    }

    @Test
    fun decodesChatMessageWithInlineImageCarryingScalingRate() {
        // rtfContent(22) -> Text -> TextPiece(4) -> Image(8) carrying a float scalingRate
        // (wire type 5). Before the fix, this 32-bit field aborted decoding of the whole chat.
        val imageBytes = fixed32Field(6, byteArrayOf(0x3F, 0x80.toByte(), 0, 0)) // scalingRate=1.0f
        val textPiece = DouyinProto.encodeStringField(3, "emoji") +
            DouyinProto.encodeMessageField(8, imageBytes)
        val text = DouyinProto.encodeMessageField(4, textPiece)
        val chat = DouyinProto.encodeStringField(3, "hi") +
            DouyinProto.encodeMessageField(22, text)

        val msg = DouyinProto.decodeChatMessage(chat)
        assertEquals("hi", msg.content)
        assertNotNull(msg.rtfContent)
        assertTrue(msg.rtfContent!!.piecesList.isNotEmpty())
    }

    @Test
    fun gzipDecompressEnforcesUpperBoundAgainstBomb() {
        val valid = "douyin-response".toByteArray(Charsets.UTF_8)
        assertArrayEquals(valid, DouyinProto.gzipDecompress(gzipCompress(valid), maxBytes = 64))

        val bomb = gzipCompress(ByteArray(64 * 1024))
        assertThrows(IllegalArgumentException::class.java) {
            DouyinProto.gzipDecompress(bomb, maxBytes = 4096)
        }
    }

    @Test
    fun rejectsOversizedLengthDelimitedFieldWithoutOverAllocating() {
        // Tag: field 3, wireType 2 => (3 << 3) | 2 = 26 (0x1A)
        // Length varint: 100_000_000 (0x80 0xC2 0xD7 0x2F), with only 1 payload byte present.
        val crafted = byteArrayOf(
            0x1A,
            0x80.toByte(),
            0xC2.toByte(),
            0xD7.toByte(),
            0x2F,
            0x41
        )
        assertThrows(IllegalArgumentException::class.java) {
            DouyinProto.decodeChatMessage(crafted)
        }
    }

    @Test
    fun rejectsTruncatedVarintAndFixedWidthFields() {
        val truncatedVarint = byteArrayOf(0x08, 0x80.toByte())
        assertThrows(IllegalArgumentException::class.java) {
            DouyinProto.decodePushFrame(truncatedVarint)
        }

        val truncatedFixed32 = byteArrayOf(((10 shl 3) or 5).toByte(), 1, 2)
        assertThrows(IllegalArgumentException::class.java) {
            DouyinProto.decodeChatMessage(truncatedFixed32)
        }
    }
}
