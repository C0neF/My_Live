package com.mylive.app.core.site.douyin

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Minimal protobuf wire-format encoder/decoder for Douyin live streaming protocol.
 *
 * Handles the specific message types used in Douyin's WebSocket danmaku:
 * PushFrame, Response, Message, ChatMessage, RoomUserSeqMessage, and supporting
 * text/image types for rich text support.
 *
 * Only wire types 0 (varint) and 2 (length-delimited) are implemented, which is
 * sufficient for the Douyin protocol.
 */
object DouyinProto {

    // ── Data classes ────────────────────────────────────────────────────────

    data class PushFrameData(
        val logId: Long = 0,
        val payloadType: String = "",
        val payload: ByteArray = ByteArray(0)
    )

    data class ResponseData(
        val messagesList: List<MessageData> = emptyList(),
        val cursor: String = "",
        val internalExt: String = "",
        val heartbeatDuration: Long = 0,
        val needAck: Boolean = false
    )

    data class MessageData(
        val method: String = "",
        val payload: ByteArray = ByteArray(0)
    )

    data class ChatMessageData(
        val nickName: String = "",
        val content: String = "",
        val rtfContent: TextData? = null
    )

    data class RoomUserSeqMessageData(
        val totalUser: Long = 0
    )

    data class TextData(
        val piecesList: List<TextPieceData> = emptyList()
    )

    data class TextPieceData(
        val stringValue: String = "",
        val patternRefValue: String? = null,
        val imageValue: ImageData? = null
    )

    data class ImageData(
        val urlListList: List<String> = emptyList(),
        val uri: String = "",
        val openWebUrl: String = "",
        val content: ImageContentData? = null
    )

    data class ImageContentData(
        val name: String = "",
        val alternativeText: String = ""
    )

    // ── Low-level wire format ───────────────────────────────────────────────

    /**
     * Encode a varint value into bytes.
     */
    private fun encodeVarint(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf(0)
        val result = mutableListOf<Byte>()
        var v = value
        do {
            var byte = (v and 0x7F).toInt()
            v = v ushr 7
            if (v != 0L) byte = byte or 0x80
            result.add(byte.toByte())
        } while (v != 0L)
        return result.toByteArray()
    }

    /**
     * Decode a varint from [data] starting at [offset].
     * Returns the decoded value and the number of bytes consumed.
     */
    private fun decodeVarint(data: ByteArray, offset: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = offset
        while (pos < data.size) {
            val byte = data[pos].toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            pos++
            if (byte and 0x80 == 0) break
            shift += 7
        }
        return Pair(result, pos - offset)
    }

    /**
     * Encode a protobuf field tag.
     */
    private fun encodeTag(fieldNumber: Int, wireType: Int): ByteArray {
        return encodeVarint((fieldNumber.toLong() shl 3) or wireType.toLong())
    }

    /**
     * Encode a varint field (wire type 0).
     */
    fun encodeVarintField(fieldNumber: Int, value: Long): ByteArray {
        return encodeTag(fieldNumber, 0) + encodeVarint(value)
    }

    /**
     * Encode a boolean field as a varint (wire type 0).
     */
    fun encodeBoolField(fieldNumber: Int, value: Boolean): ByteArray {
        return encodeVarintField(fieldNumber, if (value) 1L else 0L)
    }

    /**
     * Encode a string field (wire type 2).
     */
    fun encodeStringField(fieldNumber: Int, value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return encodeTag(fieldNumber, 2) + encodeVarint(bytes.size.toLong()) + bytes
    }

    /**
     * Encode a bytes field (wire type 2).
     */
    fun encodeBytesField(fieldNumber: Int, value: ByteArray): ByteArray {
        return encodeTag(fieldNumber, 2) + encodeVarint(value.size.toLong()) + value
    }

    /**
     * Encode an embedded message field (wire type 2).
     */
    fun encodeMessageField(fieldNumber: Int, value: ByteArray): ByteArray {
        return encodeBytesField(fieldNumber, value)
    }

    /**
     * Result of reading a single protobuf field.
     */
    private data class FieldReadResult(
        val fieldNumber: Int,
        val wireType: Int,
        val value: Any,
        val nextOffset: Int
    )

    /**
     * Read a single field from the byte array starting at [offset].
     * Returns a [FieldReadResult] with the field number, wire type, value, and
     * the offset of the next field. Returns null when end of data is reached.
     */
    private fun readField(data: ByteArray, offset: Int): FieldReadResult? {
        if (offset >= data.size) return null
        val (tag, tagBytes) = decodeVarint(data, offset)
        val fieldNumber = (tag shr 3).toInt()
        val wireType = (tag and 0x7).toInt()
        var pos = offset + tagBytes
        val value: Any = when (wireType) {
            0 -> {
                val (v, vb) = decodeVarint(data, pos)
                pos += vb
                v
            }
            1 -> {
                // 64-bit fixed (fixed64 / sfixed64 / double). Read raw so unknown fixed-width
                // fields are skipped gracefully instead of aborting the whole message.
                val bytes = data.copyOfRange(pos, pos + 8)
                pos += 8
                bytes
            }
            2 -> {
                val (len, lb) = decodeVarint(data, pos)
                pos += lb
                val bytes = data.copyOfRange(pos, pos + len.toInt())
                pos += len.toInt()
                bytes
            }
            5 -> {
                // 32-bit fixed (fixed32 / sfixed32 / float), e.g. TextPieceImage.scalingRate.
                // Previously this threw UnsupportedOperationException, which bubbled up through
                // decodeChatMessage and silently dropped every danmaku containing an inline image.
                val bytes = data.copyOfRange(pos, pos + 4)
                pos += 4
                bytes
            }
            else -> throw UnsupportedOperationException(
                "Unsupported wire type: $wireType at offset $offset"
            )
        }
        return FieldReadResult(fieldNumber, wireType, value, pos)
    }

    /**
     * Read all fields from a byte array, returning a list of (fieldNumber, value) pairs.
     */
    private fun readAllFields(data: ByteArray): List<Pair<Int, Any>> {
        val fields = mutableListOf<Pair<Int, Any>>()
        var offset = 0
        while (offset < data.size) {
            val result = readField(data, offset) ?: break
            fields.add(Pair(result.fieldNumber, result.value))
            offset = result.nextOffset
        }
        return fields
    }

    // ── Gzip decompression ──────────────────────────────────────────────────

    /**
     * Decompress gzip-compressed bytes.
     */
    fun gzipDecompress(data: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }

    // ── PushFrame encoding ──────────────────────────────────────────────────

    /**
     * Encode a PushFrame for heartbeat (payloadType = "hb").
     */
    fun encodeHeartbeat(): ByteArray {
        val fields = encodeStringField(7, "hb")
        return fields
    }

    /**
     * Encode a PushFrame for ACK.
     *
     * @param logId The log ID from the received PushFrame
     * @param internalExt The internalExt string from the received Response
     */
    fun encodeAck(logId: Long, internalExt: String): ByteArray {
        var fields = ByteArray(0)
        if (logId != 0L) {
            fields += encodeVarintField(2, logId)
        }
        fields += encodeStringField(7, "ack")
        fields += encodeBytesField(8, internalExt.toByteArray(Charsets.UTF_8))
        return fields
    }

    // ── PushFrame decoding ──────────────────────────────────────────────────

    /**
     * Decode a PushFrame from raw bytes.
     */
    fun decodePushFrame(data: ByteArray): PushFrameData {
        var logId = 0L
        var payloadType = ""
        var payload = ByteArray(0)
        for ((fieldNumber, value) in readAllFields(data)) {
            when (fieldNumber) {
                2 -> if (value is Long) logId = value
                7 -> if (value is ByteArray) payloadType = String(value, Charsets.UTF_8)
                8 -> if (value is ByteArray) payload = value
            }
        }
        return PushFrameData(logId = logId, payloadType = payloadType, payload = payload)
    }

    // ── Response decoding ───────────────────────────────────────────────────

    /**
     * Decode a Response from raw bytes (after gzip decompression of PushFrame payload).
     */
    fun decodeResponse(data: ByteArray): ResponseData {
        val messagesList = mutableListOf<MessageData>()
        var cursor = ""
        var internalExt = ""
        var heartbeatDuration = 0L
        var needAck = false
        for ((fieldNumber, value) in readAllFields(data)) {
            when (fieldNumber) {
                1 -> if (value is ByteArray) {
                    messagesList.add(decodeMessage(value))
                }
                2 -> if (value is ByteArray) cursor = String(value, Charsets.UTF_8)
                5 -> if (value is ByteArray) internalExt = String(value, Charsets.UTF_8)
                8 -> if (value is Long) heartbeatDuration = value
                9 -> if (value is Long) needAck = value != 0L
            }
        }
        return ResponseData(
            messagesList = messagesList,
            cursor = cursor,
            internalExt = internalExt,
            heartbeatDuration = heartbeatDuration,
            needAck = needAck
        )
    }

    // ── Message decoding ────────────────────────────────────────────────────

    /**
     * Decode a Message from raw bytes.
     */
    fun decodeMessage(data: ByteArray): MessageData {
        var method = ""
        var payload = ByteArray(0)
        for ((fieldNumber, value) in readAllFields(data)) {
            when (fieldNumber) {
                1 -> if (value is ByteArray) method = String(value, Charsets.UTF_8)
                2 -> if (value is ByteArray) payload = value
            }
        }
        return MessageData(method = method, payload = payload)
    }

    // ── ChatMessage decoding ────────────────────────────────────────────────

    /**
     * Decode a ChatMessage from raw bytes.
     */
    fun decodeChatMessage(data: ByteArray): ChatMessageData {
        var nickName = ""
        var content = ""
        var rtfContent: TextData? = null
        for ((fieldNumber, value) in readAllFields(data)) {
            when (fieldNumber) {
                2 -> if (value is ByteArray) {
                    // User message
                    nickName = decodeUserNickName(value)
                }
                3 -> if (value is ByteArray) content = String(value, Charsets.UTF_8)
                22 -> if (value is ByteArray) {
                    // Text rtfContent
                    rtfContent = decodeText(value)
                }
            }
        }
        return ChatMessageData(
            nickName = nickName,
            content = content,
            rtfContent = rtfContent
        )
    }

    /**
     * Decode just the nickName from a User message.
     */
    private fun decodeUserNickName(data: ByteArray): String {
        for ((fieldNumber, value) in readAllFields(data)) {
            if (fieldNumber == 3 && value is ByteArray) {
                return String(value, Charsets.UTF_8)
            }
        }
        return ""
    }

    // ── Text / TextPiece / Image decoding (for rich text) ──────────────────

    /**
     * Decode a Text message (contains piecesList).
     */
    private fun decodeText(data: ByteArray): TextData {
        val piecesList = mutableListOf<TextPieceData>()
        for ((fieldNumber, value) in readAllFields(data)) {
            if (fieldNumber == 4 && value is ByteArray) {
                piecesList.add(decodeTextPiece(value))
            }
        }
        return TextData(piecesList = piecesList)
    }

    /**
     * Decode a TextPiece.
     */
    private fun decodeTextPiece(data: ByteArray): TextPieceData {
        var stringValue = ""
        var patternRefValue: String? = null
        var imageValue: ImageData? = null
        for ((fieldNumber, value) in readAllFields(data)) {
            when (fieldNumber) {
                3 -> if (value is ByteArray) stringValue = String(value, Charsets.UTF_8)
                7 -> if (value is ByteArray) {
                    patternRefValue = decodePatternRef(value)
                }
                8 -> if (value is ByteArray) {
                    imageValue = decodeTextPieceImage(value)
                }
            }
        }
        return TextPieceData(
            stringValue = stringValue,
            patternRefValue = patternRefValue,
            imageValue = imageValue
        )
    }

    /**
     * Decode a TextPiecePatternRef (extract defaultPattern).
     */
    private fun decodePatternRef(data: ByteArray): String? {
        for ((fieldNumber, value) in readAllFields(data)) {
            if (fieldNumber == 2 && value is ByteArray) {
                val pattern = String(value, Charsets.UTF_8)
                if (pattern.isNotEmpty()) return pattern
            }
        }
        return null
    }

    /**
     * Decode a TextPieceImage (contains embedded Image).
     */
    private fun decodeTextPieceImage(data: ByteArray): ImageData? {
        for ((fieldNumber, value) in readAllFields(data)) {
            if (fieldNumber == 1 && value is ByteArray) {
                return decodeImage(value)
            }
        }
        return null
    }

    /**
     * Decode an Image message.
     */
    private fun decodeImage(data: ByteArray): ImageData {
        val urlListList = mutableListOf<String>()
        var uri = ""
        var openWebUrl = ""
        var content: ImageContentData? = null
        for ((fieldNumber, value) in readAllFields(data)) {
            when (fieldNumber) {
                1 -> if (value is ByteArray) {
                    urlListList.add(String(value, Charsets.UTF_8))
                }
                2 -> if (value is ByteArray) uri = String(value, Charsets.UTF_8)
                7 -> if (value is ByteArray) openWebUrl = String(value, Charsets.UTF_8)
                8 -> if (value is ByteArray) content = decodeImageContent(value)
            }
        }
        return ImageData(
            urlListList = urlListList,
            uri = uri,
            openWebUrl = openWebUrl,
            content = content
        )
    }

    /**
     * Decode an ImageContent message.
     */
    private fun decodeImageContent(data: ByteArray): ImageContentData {
        var name = ""
        var alternativeText = ""
        for ((fieldNumber, value) in readAllFields(data)) {
            when (fieldNumber) {
                1 -> if (value is ByteArray) name = String(value, Charsets.UTF_8)
                4 -> if (value is ByteArray) alternativeText = String(value, Charsets.UTF_8)
            }
        }
        return ImageContentData(name = name, alternativeText = alternativeText)
    }

    // ── RoomUserSeqMessage decoding ─────────────────────────────────────────

    /**
     * Decode a RoomUserSeqMessage from raw bytes.
     */
    fun decodeRoomUserSeqMessage(data: ByteArray): RoomUserSeqMessageData {
        var totalUser = 0L
        for ((fieldNumber, value) in readAllFields(data)) {
            if (fieldNumber == 7 && value is Long) {
                totalUser = value
            }
        }
        return RoomUserSeqMessageData(totalUser = totalUser)
    }
}
