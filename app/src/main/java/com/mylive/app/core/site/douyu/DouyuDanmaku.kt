package com.mylive.app.core.site.douyu

import com.mylive.app.core.common.BinaryReader
import com.mylive.app.core.common.BinaryWriter
import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageColor
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.core.site.LiveDanmaku
import org.json.JSONObject
import java.nio.ByteOrder

/**
 * Douyu live danmaku (chat) client.
 *
 * Protocol details:
 * - Binary framing: Little-Endian, 12-byte header (4B length + 4B length + 2B packType + 1B encrypted + 1B reserved) + body + null terminator
 * - STT (Serialized Text Transport) format: key@=value pairs separated by `/`, with `@S`=`/` and `@A`=`@` escaping
 * - Heartbeat: every 45s, send `type@=mrkl/`
 * - Join: `type@=loginreq/roomid@=<roomId>/` then `type@=joingroup/rid@=<roomId>/gid@=-9999/`
 */
class DouyuDanmaku(private val webSocketUtils: WebSocketUtils) : LiveDanmaku {

    override var heartbeatTime: Int = 45 * 1000
    override var onMessage: ((LiveMessage) -> Unit)? = null
    override var onClose: ((String) -> Unit)? = null
    override var onReady: (() -> Unit)? = null

    private val serverUrl = "wss://danmuproxy.douyu.com:8506"

    override suspend fun start(args: DanmakuArgs) {
        val douyuArgs = args as DanmakuArgs.Douyu
        webSocketUtils.connect(
            url = serverUrl,
            heartBeatTime = heartbeatTime.toLong(),
            onMessage = { e ->
                when (e) {
                    is ByteArray -> decodeMessage(e)
                }
            },
            onReady = {
                onReady?.invoke()
                joinRoom(douyuArgs.roomId)
            },
            onHeartBeat = { heartbeat() },
            onReconnect = { onClose?.invoke("与服务器断开连接，正在尝试重连") },
            onClose = { e ->
                if (!e.contains("Socket closed")) {
                    onClose?.invoke("服务器连接失败$e")
                }
            }
        )
    }

    /**
     * Send loginreq and joingroup messages to join the room's danmaku channel.
     */
    private fun joinRoom(roomId: String) {
        webSocketUtils.sendMessage(serializeDouyu("type@=loginreq/roomid@=$roomId/"))
        webSocketUtils.sendMessage(serializeDouyu("type@=joingroup/rid@=$roomId/gid@=-9999/"))
    }

    override fun heartbeat() {
        webSocketUtils.sendMessage(serializeDouyu("type@=mrkl/"))
    }

    override suspend fun stop() {
        onMessage = null
        onClose = null
        webSocketUtils.close()
    }

    // ── Binary framing ──────────────────────────────────────────────────

    /**
     * Serialize a Douyu STT message into binary frame.
     *
     * Header (Little-Endian):
     *   [0-3]  total length (4 bytes, = 4 + 4 + bodyLength + 1)
     *   [4-7]  total length repeated (4 bytes)
     *   [8-9]  pack type = 689 (2 bytes, little-endian)
     *   [10]   encrypted = 0 (1 byte)
     *   [11]   reserved = 0 (1 byte)
     *   [12..] body (UTF-8 encoded)
     *   [last] null terminator = 0 (1 byte)
     */
    private fun serializeDouyu(body: String): ByteArray {
        return try {
            val clientSendToServer = 689
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            val totalLen = 4 + 4 + bodyBytes.size + 1

            val writer = BinaryWriter(order = ByteOrder.LITTLE_ENDIAN)
            writer.writeInt(totalLen.toLong(), 4)       // full message length
            writer.writeInt(totalLen.toLong(), 4)       // full message length (repeated)
            writer.writeInt(clientSendToServer.toLong(), 2)  // pack type
            writer.writeInt(0, 1)                       // encrypted
            writer.writeInt(0, 1)                       // reserved
            writer.writeBytes(bodyBytes)                // body
            writer.writeInt(0, 1)                       // null terminator
            writer.toByteArray()
        } catch (e: Exception) {
            CoreLog.error(e)
            ByteArray(0)
        }
    }

    /**
     * Deserialize a Douyu binary frame into a UTF-8 string.
     *
     * Header (Little-Endian):
     *   [0-3]  full message length (4 bytes)
     *   [4-7]  full message length repeated (4 bytes)
     *   [8-9]  pack type (2 bytes)
     *   [10]   encrypted (1 byte)
     *   [11]   reserved (1 byte)
     *   [12..] body (fullMsgLength - 9 bytes)
     *   [last] null terminator (1 byte)
     */
    private fun deserializeDouyu(buffer: ByteArray): String? {
        return try {
            val reader = BinaryReader(buffer, ByteOrder.LITTLE_ENDIAN)
            val fullMsgLength = reader.readInt32()
            reader.readInt32()  // fullMsgLength2 (repeated)
            val bodyLength = fullMsgLength - 9
            reader.readShort()  // packType
            reader.readByte()   // encrypted
            reader.readByte()   // reserved
            val bytes = reader.readBytes(bodyLength)
            reader.readByte()   // null terminator (0)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            CoreLog.error(e)
            null
        }
    }

    // ── STT format parsing ──────────────────────────────────────────────

    /**
     * Parse Douyu STT (Serialized Text Transport) format into a JSON-like structure.
     *
     * STT rules:
     * - Key-value pairs separated by `/`: `key@=value/key2@=value2/`
     * - Nested objects: value itself contains `@=` pairs
     * - Arrays: fields separated by `//`
     * - Escaping: `@S` -> `/`, `@A` -> `@`
     *
     * @return JSONObject for maps, JSONArray for arrays, String for leaf values
     */
    private fun parseStt(str: String): Any? {
        // Array: fields separated by "//"
        if (str.contains("//")) {
            val result = org.json.JSONArray()
            for (field in str.split("//")) {
                if (field.isEmpty()) continue
                result.put(parseStt(field))
            }
            return result
        }
        // Object: key@=value pairs separated by "/"
        if (str.contains("@=")) {
            val result = JSONObject()
            for (field in str.split('/')) {
                if (field.isEmpty()) continue
                val tokens = field.split("@=", limit = 2)
                if (tokens.size < 2) continue
                val key = tokens[0]
                val value = unescapeSlashAt(tokens[1])
                result.put(key, parseStt(value))
            }
            return result
        }
        // Nested escaped value
        if (str.contains("@A=")) {
            return parseStt(unescapeSlashAt(str))
        }
        // Leaf string value
        return unescapeSlashAt(str)
    }

    /**
     * Unescape Douyu STT special characters.
     * `@S` -> `/` and `@A` -> `@`
     */
    private fun unescapeSlashAt(str: String): String {
        return str.replace("@S", "/").replace("@A", "@")
    }

    // ── Message handling ────────────────────────────────────────────────

    /**
     * Decode an incoming binary message from Douyu.
     * Parses the binary frame, then processes STT-encoded message data.
     */
    private fun decodeMessage(data: ByteArray) {
        try {
            val result = deserializeDouyu(data) ?: return
            val jsonData = parseStt(result) as? JSONObject ?: return

            val type = jsonData.optString("type", "")
            if (type == "chatmsg") {
                // Filter out blocked danmaku (dms == null means blocked)
                if (jsonData.isNull("dms")) return

                val col = jsonData.optString("col", "0").toIntOrNull() ?: 0
                val liveMsg = LiveMessage(
                    type = LiveMessageType.CHAT,
                    userName = jsonData.optString("nn", ""),
                    message = jsonData.optString("txt", ""),
                    color = getColor(col)
                )
                onMessage?.invoke(liveMsg)
            }
        } catch (e: Exception) {
            CoreLog.error(e)
        }
    }

    /**
     * Map Douyu color code to LiveMessageColor.
     *
     * Color codes:
     *   1 = red, 2 = blue, 3 = green, 4 = orange, 5 = purple, 6 = pink
     *   default = white
     */
    private fun getColor(type: Int): LiveMessageColor {
        return when (type) {
            1 -> LiveMessageColor(255, 0, 0)
            2 -> LiveMessageColor(30, 135, 240)
            3 -> LiveMessageColor(122, 200, 75)
            4 -> LiveMessageColor(255, 127, 0)
            5 -> LiveMessageColor(155, 57, 244)
            6 -> LiveMessageColor(255, 105, 180)
            else -> LiveMessageColor.WHITE
        }
    }
}
