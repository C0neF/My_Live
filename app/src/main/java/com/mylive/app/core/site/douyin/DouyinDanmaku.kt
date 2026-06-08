package com.mylive.app.core.site.douyin

import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.EmojiParser
import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageColor
import com.mylive.app.core.model.LiveMessageSpan
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.core.script.JsEngine
import com.mylive.app.core.site.LiveDanmaku
import java.net.URLEncoder

/**
 * Douyin (抖音) live danmaku implementation.
 *
 * Connects via WebSocket using a protobuf-based protocol. Messages are received
 * as gzip-compressed protobuf [DouyinProto.PushFrameData] frames, decompressed
 * into [DouyinProto.ResponseData], and dispatched by method type.
 *
 * Supports:
 * - `WebcastChatMessage` (chat messages with rich text / image spans)
 * - `WebcastRoomUserSeqMessage` (online viewer count)
 * - Periodic heartbeat (PushFrame with payloadType "hb") every 10 seconds
 * - ACK replies when the server sets `needAck = true`
 *
 * Multiple backup WebSocket URLs are tried for resilience.
 */
class DouyinDanmaku(
    private val webSocketUtils: WebSocketUtils,
    private val jsEngine: JsEngine
) : LiveDanmaku {

    override var heartbeatTime: Int = 10_000

    override var onMessage: ((LiveMessage) -> Unit)? = null
    override var onClose: ((String) -> Unit)? = null
    override var onReady: (() -> Unit)? = null

    private lateinit var danmakuArgs: DanmakuArgs.Douyin
    private val douyinSign = DouyinSign(jsEngine)

    companion object {
        private const val SERVER_URL = "wss://webcast3-ws-web-lq.douyin.com/webcast/im/push/v2/"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.97 Safari/537.36 Core/1.116.567.400 QQBrowser/19.7.6764.400"

        /** Backup URL host substitutions for resilience. */
        private val BACKUP_HOSTS = listOf(
            "webcast5-ws-web-lf",
            "webcast5-ws-web-hl",
            "webcast3-ws-web-hl",
            "webcast3-ws-web-lf"
        )
    }

    /**
     * Start the danmaku connection.
     *
     * Builds the WebSocket URL with all required query parameters, generates
     * the signature via [DouyinSign.getSignature], and connects using [WebSocketUtils].
     *
     * @param args Must be a [DanmakuArgs.Douyin] instance
     */
    override suspend fun start(args: DanmakuArgs) {
        danmakuArgs = args as DanmakuArgs.Douyin
        val ts = System.currentTimeMillis()

        // Build the base WebSocket URL with query parameters
        val baseParams = linkedMapOf(
            "app_name" to "douyin_web",
            "version_code" to "180800",
            "webcast_sdk_version" to "1.3.0",
            "update_version_code" to "1.3.0",
            "compress" to "gzip",
            "cursor" to "h-1_t-${ts}_r-1_d-1_u-1",
            "host" to "https://live.douyin.com",
            "aid" to "6383",
            "live_id" to "1",
            "did_rule" to "3",
            "debug" to "false",
            "maxCacheMessageNumber" to "20",
            "endpoint" to "live_pc",
            "support_wrds" to "1",
            "im_path" to "/webcast/im/fetch/",
            "user_unique_id" to danmakuArgs.userId,
            "device_platform" to "web",
            "cookie_enabled" to "true",
            "screen_width" to "1920",
            "screen_height" to "1080",
            "browser_language" to "zh-CN",
            "browser_platform" to "Win32",
            "browser_name" to "Mozilla",
            "browser_version" to DEFAULT_USER_AGENT.replace("Mozilla/", ""),
            "browser_online" to "true",
            "tz_name" to "Asia/Shanghai",
            "identity" to "audience",
            "room_id" to danmakuArgs.roomId,
            "heartbeatDuration" to "0"
        )

        val queryString = baseParams.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }

        // Generate the signature
        val sign = douyinSign.getSignature(danmakuArgs.roomId, danmakuArgs.userId)
        val url = "$SERVER_URL?$queryString&signature=$sign"

        // Build backup URLs
        val backupUrl = url.replace("webcast3-ws-web-lq", "webcast5-ws-web-lf")
        val backupUrls = BACKUP_HOSTS.map { host ->
            url.replace("webcast3-ws-web-lq", host)
        }

        CoreLog.d("[DouyinDanmaku] Connecting to: $url")

        webSocketUtils.connect(
            url = url,
            backupUrl = backupUrl,
            backupUrls = backupUrls,
            heartBeatTime = heartbeatTime.toLong(),
            headers = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                "Cache-Control" to "no-cache",
                "Pragma" to "no-cache",
                "User-Agent" to DEFAULT_USER_AGENT,
                "Cookie" to danmakuArgs.cookie,
                "Origin" to "https://live.douyin.com",
                "Referer" to "https://live.douyin.com/${danmakuArgs.webRid}"
            ),
            onMessage = { data -> decodeMessage(data) },
            onReady = {
                onReady?.invoke()
                joinRoom()
            },
            onHeartBeat = { heartbeat() },
            onReconnect = { onClose?.invoke("与服务器断开连接，正在尝试重连") },
            onClose = { msg ->
                if (!msg.contains("Socket closed")) {
                    onClose?.invoke("服务器连接失败$msg")
                }
            }
        )
    }

    /**
     * Send a heartbeat PushFrame (payloadType = "hb").
     */
    override fun heartbeat() {
        try {
            val frame = DouyinProto.encodeHeartbeat()
            webSocketUtils.sendMessage(frame)
        } catch (e: Exception) {
            CoreLog.e("[DouyinDanmaku] heartbeat failed", e)
        }
    }

    /**
     * Join the room by sending an initial heartbeat frame.
     */
    private fun joinRoom() {
        try {
            val frame = DouyinProto.encodeHeartbeat()
            webSocketUtils.sendMessage(frame)
        } catch (e: Exception) {
            CoreLog.e("[DouyinDanmaku] joinRoom failed", e)
        }
    }

    /**
     * Decode an incoming binary WebSocket message.
     *
     * Flow: raw bytes -> PushFrame -> gzip decompress payload -> Response -> iterate Messages
     */
    private fun decodeMessage(data: Any) {
        try {
            val bytes: ByteArray = when (data) {
                is ByteArray -> data
                is String -> data.toByteArray(Charsets.UTF_8)
                else -> return
            }

            val pushFrame = DouyinProto.decodePushFrame(bytes)
            val logId = pushFrame.logId

            // Decompress the payload (gzip)
            val decompressed = DouyinProto.gzipDecompress(pushFrame.payload)
            val response = DouyinProto.decodeResponse(decompressed)

            // Send ACK if needed
            if (response.needAck) {
                sendAck(logId, response.internalExt)
            }

            // Process each message
            for (msg in response.messagesList) {
                when (msg.method) {
                    "WebcastChatMessage" -> unpackWebcastChatMessage(msg.payload)
                    "WebcastRoomUserSeqMessage" -> unpackWebcastRoomUserSeqMessage(msg.payload)
                }
            }
        } catch (e: Exception) {
            CoreLog.e("[DouyinDanmaku] decodeMessage failed", e)
        }
    }

    /**
     * Decode a WebcastChatMessage and emit a [LiveMessage] of type [LiveMessageType.CHAT].
     *
     * Supports rich text (RTF) content: extracts text and image spans from
     * [DouyinProto.TextPieceData] entries, falling back to plain `content` if
     * no RTF data is available.
     */
    private fun parseEmojiSpans(spans: List<LiveMessageSpan>): List<LiveMessageSpan> {
        val result = mutableListOf<LiveMessageSpan>()
        for (span in spans) {
            when (span) {
                is LiveMessageSpan.Text -> {
                    result.addAll(EmojiParser.parse(span.text, "douyin"))
                }
                is LiveMessageSpan.Image -> {
                    result.add(span)
                }
            }
        }
        return result
    }

    private fun unpackWebcastChatMessage(payload: ByteArray) {
        try {
            val chatMessage = DouyinProto.decodeChatMessage(payload)
            val spans = extractRtfSpans(chatMessage)
            val finalSpans = if (spans.isEmpty()) {
                EmojiParser.parse(chatMessage.content, "douyin")
            } else {
                parseEmojiSpans(spans)
            }
            val imageUrls = finalSpans
                .filterIsInstance<LiveMessageSpan.Image>()
                .map { it.imageUrl.trim() }
                .distinct()
            val message = buildChatMessageText(chatMessage, finalSpans)
            onMessage?.invoke(
                LiveMessage(
                    type = LiveMessageType.CHAT,
                    color = LiveMessageColor.WHITE,
                    message = message,
                    userName = chatMessage.nickName,
                    imageUrls = imageUrls.ifEmpty { null },
                    spans = finalSpans.ifEmpty { null }
                )
            )
        } catch (e: Exception) {
            CoreLog.e("[DouyinDanmaku] unpackWebcastChatMessage failed", e)
        }
    }

    /**
     * Build the display text for a chat message from RTF spans.
     * Falls back to plain content if no text spans are found.
     */
    private fun buildChatMessageText(
        chatMessage: DouyinProto.ChatMessageData,
        spans: List<LiveMessageSpan>
    ): String {
        if (spans.isEmpty()) return chatMessage.content
        val text = spans.filterIsInstance<LiveMessageSpan.Text>()
            .joinToString("") { it.text }
            .trim()
        return text.ifEmpty { chatMessage.content }
    }

    /**
     * Extract rich text spans from a ChatMessage's rtfContent.
     *
     * For each TextPiece:
     * - stringValue -> text span
     * - patternRefValue.defaultPattern -> text span
     * - imageValue.image -> image span (with fallback to text if no URL)
     */
    private fun extractRtfSpans(chatMessage: DouyinProto.ChatMessageData): List<LiveMessageSpan> {
        val spans = mutableListOf<LiveMessageSpan>()
        val rtf = chatMessage.rtfContent ?: return spans

        for (piece in rtf.piecesList) {
            // Plain text value
            if (piece.stringValue.trim().isNotEmpty()) {
                spans.add(LiveMessageSpan.Text(piece.stringValue))
            }
            // Pattern reference (e.g. emoji placeholder)
            val pattern = piece.patternRefValue?.trim()
            if (!pattern.isNullOrEmpty()) {
                spans.add(LiveMessageSpan.Text(pattern))
            }
            // Image value
            val imageData = piece.imageValue
            if (imageData != null) {
                val imageUrl = extractImageUrl(imageData)
                if (imageUrl != null) {
                    spans.add(LiveMessageSpan.Image(imageUrl))
                    continue
                }
                // Fallback to text description
                val fallback = extractImageFallbackText(imageData)
                if (fallback != null) {
                    spans.add(LiveMessageSpan.Text(fallback))
                }
            }
        }
        return spans
    }

    /**
     * Extract an image URL from an [DouyinProto.ImageData], checking urlListList,
     * openWebUrl, and uri in order.
     */
    private fun extractImageUrl(image: DouyinProto.ImageData): String? {
        for (url in image.urlListList) {
            val value = url.trim()
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return value
            }
        }
        val openWebUrl = image.openWebUrl.trim()
        if (openWebUrl.startsWith("http://") || openWebUrl.startsWith("https://")) {
            return openWebUrl
        }
        val uri = image.uri.trim()
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            return uri
        }
        return null
    }

    /**
     * Extract fallback text from an [DouyinProto.ImageData] when no image URL is available.
     */
    private fun extractImageFallbackText(image: DouyinProto.ImageData): String? {
        val content = image.content
        if (content != null) {
            val altText = content.alternativeText.trim()
            if (altText.isNotEmpty()) return altText
            val name = content.name.trim()
            if (name.isNotEmpty()) return name
        }
        val uri = image.uri.trim()
        if (uri.isNotEmpty()) return "[$uri]"
        return null
    }

    /**
     * Decode a WebcastRoomUserSeqMessage and emit a [LiveMessage] of type [LiveMessageType.ONLINE].
     */
    private fun unpackWebcastRoomUserSeqMessage(payload: ByteArray) {
        try {
            val seqMessage = DouyinProto.decodeRoomUserSeqMessage(payload)
            onMessage?.invoke(
                LiveMessage(
                    type = LiveMessageType.ONLINE,
                    userName = "",
                    message = "",
                    color = LiveMessageColor.WHITE,
                    onlineCount = seqMessage.totalUser.toInt()
                )
            )
        } catch (e: Exception) {
            CoreLog.e("[DouyinDanmaku] unpackWebcastRoomUserSeqMessage failed", e)
        }
    }

    /**
     * Send an ACK frame to the server.
     *
     * @param logId The logId from the received PushFrame
     * @param internalExt The internalExt from the received Response
     */
    private fun sendAck(logId: Long, internalExt: String) {
        try {
            val frame = DouyinProto.encodeAck(logId, internalExt)
            webSocketUtils.sendMessage(frame)
        } catch (e: Exception) {
            CoreLog.e("[DouyinDanmaku] sendAck failed", e)
        }
    }

    /**
     * Stop the danmaku connection and clean up callbacks.
     */
    override suspend fun stop() {
        onMessage = null
        onClose = null
        onReady = null
        webSocketUtils.close()
    }
}
