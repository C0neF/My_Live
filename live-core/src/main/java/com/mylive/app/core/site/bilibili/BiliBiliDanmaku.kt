package com.mylive.app.core.site.bilibili

import com.mylive.app.core.common.BinaryReader
import com.mylive.app.core.common.BinaryWriter
import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.MAX_DECOMPRESSED_DANMAKU_BYTES
import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.common.readBytesWithinLimit
import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageColor
import com.mylive.app.core.model.LiveMessageDanmakuPosition
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.core.site.LiveDanmaku
import org.brotli.dec.BrotliInputStream
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

class BiliBiliDanmaku(private val webSocketUtils: WebSocketUtils) : LiveDanmaku {

    override var heartbeatTime: Int = 30 * 1000
    @Volatile override var onMessage: ((LiveMessage) -> Unit)? = null
    @Volatile override var onClose: ((String) -> Unit)? = null
    @Volatile override var onReady: (() -> Unit)? = null

    override suspend fun start(args: DanmakuArgs) {
        val biliArgs = args as DanmakuArgs.BiliBili
        webSocketUtils.connect(
            url = "wss://${biliArgs.serverHost}/sub",
            backupUrls = biliArgs.backupHosts.map { "wss://$it/sub" },
            heartBeatTime = heartbeatTime.toLong(),
            headers = if (biliArgs.cookie.isEmpty()) null else mapOf("cookie" to biliArgs.cookie),
            onMessage = { e -> decodeMessage(e) },
            onReady = {
                onReady?.invoke()
                joinRoom(biliArgs)
                heartbeat()
            },
            onHeartBeat = { heartbeat() },
            onReconnect = { onClose?.invoke("与服务器断开连接，正在尝试重连") },
            onClose = { e ->
                // Ignore expected socket closed errors during reconnection
                if (!e.contains("Socket closed")) {
                    onClose?.invoke("服务器连接失败$e")
                }
            }
        )
    }

    private fun joinRoom(args: DanmakuArgs.BiliBili) {
        val joinData = encodeData(
            JSONObject().apply {
                put("uid", args.uid)
                put("roomid", args.roomId)
                put("protover", 3)
                put("buvid", args.buvid)
                put("platform", "web")
                put("type", 2)
                put("key", args.token)
            }.toString(),
            7
        )
        webSocketUtils.sendMessage(joinData)
    }

    override fun heartbeat() {
        webSocketUtils.sendMessage(encodeData("", 2))
    }

    override suspend fun stop() {
        onMessage = null
        onClose = null
        onReady = null
        webSocketUtils.close()
    }

    /**
     * Encode a message into the BiliBili binary protocol.
     * Header is 16 bytes, big-endian:
     *   [0-3]   packet length (4 bytes)
     *   [4-5]   header length (2 bytes, always 16)
     *   [6-7]   protocol version (2 bytes, 0=JSON)
     *   [8-11]  operation type (4 bytes)
     *   [12-15] sequence ID (4 bytes, always 1)
     * Followed by the UTF-8 encoded body.
     */
    private fun encodeData(msg: String, action: Int): ByteArray {
        val data = msg.toByteArray(Charsets.UTF_8)
        val length = data.size + 16
        val writer = BinaryWriter()

        // Packet length
        writer.writeInt(length.toLong(), 4)
        // Header length, fixed 16
        writer.writeInt(16, 2)
        // Protocol version, 0=JSON
        writer.writeInt(0, 2)
        // Operation type
        writer.writeInt(action.toLong(), 4)
        // Sequence ID, fixed 1
        writer.writeInt(1, 4)

        writer.writeBytes(data)

        return writer.toByteArray()
    }

    /**
     * Decode an incoming binary message from BiliBili.
     *
     * Header (16 bytes, big-endian):
     *   [0-3]   packet length
     *   [4-5]   header length
     *   [6-7]   protocol version (0=JSON, 1=Int32, 2=zlib, 3=brotli)
     *   [8-11]  operation type (3=heartbeat reply, 5=notification, 8=join reply)
     *   [12-15] sequence ID
     */
    private fun decodeMessage(data: Any) {
        try {
            val bytes: ByteArray = when (data) {
                is ByteArray -> data
                is String -> data.toByteArray(Charsets.UTF_8)
                else -> return
            }

            if (bytes.size < 16) return
            var offset = 0
            while (offset + 16 <= bytes.size) {
                val reader = BinaryReader(bytes)
                reader.position = offset
                val packetLen = reader.readInt32()
                val headerLen = reader.readShort()
                val protocolVersion = reader.readShort()
                val operation = reader.readInt32()
                if (headerLen < 16 || packetLen < headerLen || offset.toLong() + packetLen > bytes.size) {
                    break
                }
                val body = bytes.copyOfRange(offset + headerLen, offset + packetLen)

                when (operation) {
                    3 -> {
                        // Heartbeat reply: body contains online count as 4-byte int32
                        if (body.size >= 4) {
                            val bodyReader = BinaryReader(body)
                            val online = bodyReader.readInt32()
                            onMessage?.invoke(
                                LiveMessage(
                                    type = LiveMessageType.ONLINE,
                                    userName = "",
                                    message = "",
                                    color = LiveMessageColor.WHITE,
                                    onlineCount = online
                                )
                            )
                        }
                    }
                    5 -> {
                        // Notification: may be compressed
                        val decompressedBody = decompressBiliBiliDanmakuBody(protocolVersion, body)
                        for (item in extractBiliBiliNotificationJsons(protocolVersion, decompressedBody)) {
                            parseMessage(item)
                        }
                    }
                }
                offset += packetLen
            }
        } catch (e: Exception) {
            CoreLog.error(e)
        }
    }

    /**
     * Parse a single JSON message from the notification payload.
     * Handles DANMU_MSG and SUPER_CHAT_MESSAGE commands.
     */
    private fun parseMessage(jsonMessage: String) {
        try {
            val obj = JSONObject(jsonMessage)
            val cmd = obj.optString("cmd", "")
            if (cmd.contains("DANMU_MSG")) {
                val info = obj.optJSONArray("info")
                if (info != null && info.length() != 0) {
                    val message = info.opt(1).toString()
                    val color = info.optJSONArray(0)?.optInt(3, 0) ?: 0
                    val info2 = info.optJSONArray(2)
                    if (info2 != null && info2.length() != 0) {
                        val username = info2.opt(1).toString()
                        val imageMap = extractImageMap(info, message)
                        val danmakuPosition = resolveBiliBiliDanmakuPosition(
                            info.optJSONArray(0)?.optInt(1, 1) ?: 1
                        )
                        val liveMsg = LiveMessage(
                            type = LiveMessageType.CHAT,
                            userName = username,
                            message = message,
                            danmakuPosition = danmakuPosition,
                            color = if (color == 0) LiveMessageColor.WHITE
                            else LiveMessageColor.numberToColor(color),
                            imageUrls = imageMap.values.toList().ifEmpty { null },
                            imageMap = imageMap.ifEmpty { null }
                        )
                        onMessage?.invoke(liveMsg)
                    }
                }
            } else if (cmd == "SUPER_CHAT_MESSAGE") {
                val data = obj.optJSONObject("data") ?: return
                val userInfo = data.optJSONObject("user_info")
                val sc = LiveSuperChatMessage(
                    backgroundBottomColor = data.optString("background_bottom_color", ""),
                    backgroundColor = data.optString("background_color", ""),
                    endTime = data.optLong("end_time") * 1000,
                    face = "${userInfo?.optString("face", "")}@200w.jpg",
                    message = data.optString("message", ""),
                    price = data.optInt("price"),
                    startTime = data.optLong("start_time") * 1000,
                    userName = userInfo?.optString("uname", "") ?: ""
                )
                val liveMsg = LiveMessage(
                    type = LiveMessageType.SUPER_CHAT,
                    userName = "SUPER_CHAT_MESSAGE",
                    message = "SUPER_CHAT_MESSAGE",
                    color = LiveMessageColor.WHITE,
                    superChatMessage = sc
                )
                onMessage?.invoke(liveMsg)
            }
        } catch (e: Exception) {
            CoreLog.error(e)
        }
    }

    /**
     * Build a bracket-text → image-URL map from the danmaku info array.
     *
     * Checks two locations:
     * 1. info[0][15]["extra"] - JSON string with "emots" map. Each key is a bracket
     *    placeholder (e.g. "[微笑]") and its value contains the CDN URL. This is the
     *    primary source for inline emoji.
     * 2. info[0][13]["url"] - single sticker URL. Used as fallback when no emots entry
     *    covers a bracket pattern in the message.
     *
     * Returns a map so the display layer can look up URLs by bracket text instead of
     * relying on positional matching, which breaks when JSONObject.keys() returns an
     * arbitrary order or when .distinct() collapses duplicate URLs.
     */
    private fun extractImageMap(info: org.json.JSONArray, message: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var stickerUrl: String? = null

        // Source 1: info[0][13] — single large sticker URL (fallback only)
        try {
            val info0 = info.optJSONArray(0)
            if (info0 != null && info0.length() > 13) {
                val info0_13 = info0.optJSONObject(13)
                if (info0_13 != null) {
                    val url = info0_13.optString("url", "").trim()
                    if (url.isNotEmpty()) stickerUrl = url
                }
            }
        } catch (_: Exception) {}

        // Source 2: info[0][15].extra.emots — inline emoji map (primary source)
        try {
            val info0 = info.optJSONArray(0)
            if (info0 != null && info0.length() > 15) {
                val info0_15 = info0.optJSONObject(15)
                if (info0_15 != null) {
                    val extra = info0_15.optString("extra", "")
                    if (extra.isNotEmpty()) {
                        val extraObj = JSONObject(extra)
                        val emots = extraObj.optJSONObject("emots")
                        if (emots != null) {
                            for (key in emots.keys()) {
                                if (key.isEmpty() || !message.contains(key)) continue
                                val emot = emots.optJSONObject(key)
                                val url = emot?.optString("url", "")?.trim() ?: ""
                                if (url.isNotEmpty()) {
                                    map[key] = url
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback: if a bracket pattern in the message has no emots entry but a
        // sticker URL exists from info[0][13], assign it to uncovered brackets.
        if (stickerUrl != null) {
            for (match in BRACKET_EMOJI_REGEX.findAll(message)) {
                val bracket = match.value
                if (!map.containsKey(bracket)) {
                    map[bracket] = stickerUrl
                }
            }
        }

        return map
    }
}

private val BRACKET_EMOJI_REGEX = Regex("\\[[^\\[\\]]{1,32}]")
private val CONTROL_CHAR_SPLIT_REGEX = Regex("[\\x00-\\x1f]+")

internal fun resolveBiliBiliDanmakuPosition(mode: Int): LiveMessageDanmakuPosition {
    return when (mode) {
        4 -> LiveMessageDanmakuPosition.BOTTOM
        5 -> LiveMessageDanmakuPosition.TOP
        else -> LiveMessageDanmakuPosition.SCROLL
    }
}

internal fun decompressBiliBiliDanmakuBody(
    protocolVersion: Int,
    body: ByteArray,
    maxBytes: Int = MAX_DECOMPRESSED_DANMAKU_BYTES
): ByteArray {
    return when (protocolVersion) {
        2 -> InflaterInputStream(ByteArrayInputStream(body)).use {
            it.readBytesWithinLimit(maxBytes)
        }
        3 -> BrotliInputStream(ByteArrayInputStream(body)).use {
            it.readBytesWithinLimit(maxBytes)
        }
        else -> {
            require(body.size <= maxBytes) { "弹幕包体超过大小限制（上限 $maxBytes 字节）" }
            body
        }
    }
}

internal fun extractBiliBiliNotificationJsons(
    protocolVersion: Int,
    decompressedBody: ByteArray
): List<String> {
    if ((protocolVersion == 2 || protocolVersion == 3) && decompressedBody.size >= 16) {
        val packets = mutableListOf<String>()
        var offset = 0
        var parsedFramedPacket = false
        while (offset + 16 <= decompressedBody.size) {
            val packetLen = ((decompressedBody[offset].toInt() and 0xFF) shl 24) or
                ((decompressedBody[offset + 1].toInt() and 0xFF) shl 16) or
                ((decompressedBody[offset + 2].toInt() and 0xFF) shl 8) or
                (decompressedBody[offset + 3].toInt() and 0xFF)
            val headerLen = ((decompressedBody[offset + 4].toInt() and 0xFF) shl 8) or
                (decompressedBody[offset + 5].toInt() and 0xFF)
            val operation = ((decompressedBody[offset + 8].toInt() and 0xFF) shl 24) or
                ((decompressedBody[offset + 9].toInt() and 0xFF) shl 16) or
                ((decompressedBody[offset + 10].toInt() and 0xFF) shl 8) or
                (decompressedBody[offset + 11].toInt() and 0xFF)
            if (headerLen < 16 || packetLen < headerLen || offset.toLong() + packetLen > decompressedBody.size) {
                break
            }
            parsedFramedPacket = true
            if (operation == 5 && packetLen > headerLen) {
                val json = String(
                    decompressedBody,
                    offset + headerLen,
                    packetLen - headerLen,
                    Charsets.UTF_8
                ).trim()
                if (json.length > 2 && json.startsWith('{')) {
                    packets.add(json)
                }
            }
            offset += packetLen
        }
        if (parsedFramedPacket) {
            return packets
        }
    }
    val text = String(decompressedBody, Charsets.UTF_8)
    return text.split(CONTROL_CHAR_SPLIT_REGEX)
        .filter { it.length > 2 && it.startsWith('{') }
}
