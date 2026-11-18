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
    @Volatile override var onMessage: ((LiveMessage) -> Unit)? = null
    @Volatile override var onClose: ((String) -> Unit)? = null
    @Volatile override var onReady: (() -> Unit)? = null

    override suspend fun start(args: DanmakuArgs) {
        val douyuArgs = args as DanmakuArgs.Douyu
        val serverUrls = douyuDanmakuServerUrls()
        webSocketUtils.connect(
            url = serverUrls.first(),
            backupUrls = serverUrls.drop(1),
            connectTimeoutMillis = DOUYU_DANMAKU_CONNECT_TIMEOUT_MILLIS,
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
        onReady = null
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
     * Deserialize one or more concatenated Douyu binary frames into UTF-8 strings.
     */
    internal fun deserializeDouyuFrames(buffer: ByteArray): List<String> {
        val messages = mutableListOf<String>()
        var offset = 0
        while (offset + 12 <= buffer.size) {
            try {
                val frameReader = BinaryReader(buffer.copyOfRange(offset, offset + 12), ByteOrder.LITTLE_ENDIAN)
                val fullMsgLength = frameReader.readInt32()
                frameReader.readInt32() // repeated length
                val bodyLength = fullMsgLength - 9
                val totalFrameBytes = 4L + fullMsgLength.toLong()
                if (fullMsgLength < 9 || bodyLength < 0 || totalFrameBytes <= 12L || offset + totalFrameBytes > buffer.size) {
                    break
                }
                val bodyStart = offset + 12
                val bodyEnd = bodyStart + bodyLength
                if (bodyEnd > buffer.size) break
                messages.add(String(buffer, bodyStart, bodyLength, Charsets.UTF_8))
                offset += totalFrameBytes.toInt()
            } catch (e: Exception) {
                CoreLog.error(e)
                break
            }
        }
        return messages
    }

    // ── STT format parsing ──────────────────────────────────────────────

    /**
     * Read top-level STT fields before decoding their escapes. Chat fields are strings:
     * decoded URLs or literal `@=` text must not be parsed again as arrays or objects.
     */
    private fun parseStt(str: String): JSONObject {
        val result = JSONObject()
        for (field in str.split('/')) {
            if (field.isEmpty()) continue
            val tokens = field.split("@=", limit = 2)
            if (tokens.size < 2) continue
            result.put(unescapeSlashAt(tokens[0]), unescapeSlashAt(tokens[1]))
        }
        return result
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
            val results = deserializeDouyuFrames(data)
            for (result in results) {
                val jsonData = parseStt(result)

                val type = jsonData.optString("type", "")
                if (type == "chatmsg") {
                    // Filter out blocked danmaku (dms == null means blocked)
                    if (jsonData.isNull("dms")) continue

                    val col = jsonData.optString("col", "0").toIntOrNull() ?: 0
                    val liveMsg = LiveMessage(
                        type = LiveMessageType.CHAT,
                        userName = jsonData.optString("nn", ""),
                        message = jsonData.optString("txt", ""),
                        color = getColor(col)
                    )
                    onMessage?.invoke(liveMsg)
                }
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
